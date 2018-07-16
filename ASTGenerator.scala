package org.jetbrains.plugins.kotlinConverter

import com.intellij.psi.{PsiClass, PsiCodeBlock, PsiElement, PsiStatement}
import org.jetbrains.plugins.kotlinConverter.ast._
import org.jetbrains.plugins.kotlinConverter.ast._
import org.jetbrains.plugins.kotlinConverter.types.ScalaTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScClassParents
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.{ScBlockExprImpl, ScNewTemplateDefinitionImpl, ScReferenceExpressionImpl}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.FakePsiStatement
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalSignature, ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.scalafmt.internal.SyntacticGroup.Type.SimpleTyp

import scala.collection.immutable

object ASTGenerator extends {
  private def genDefinitions(file: ScalaFile): Seq[PsiElement] = {
    val functionDefns =
      file.findChildrenByType(ScalaElementTypes.FUNCTION_DEFINITION)
    val functionDecls =
      file.findChildrenByType(ScalaElementTypes.FUNCTION_DECLARATION)
    functionDefns ++ functionDecls ++ file.typeDefinitions
  }



  private def genFunctionBody(fun: ScFunction): Option[Expr] = fun match {
    case x: ScFunctionDefinition =>
      x.body.map(gen[Expr])
    case _: ScFunctionDeclaration =>
      None
  }


  def genTypeArgs(genCall: ScGenericCall): Seq[TypeParam] = {
    genCall.typeArgs.
      map(_.typeArgs)
      .toSeq
      .flatten
      .map(z => TypeParam(genType(z.`type`())))
  }

  def genType(t: ScType): Type = {
    t match {
      case x: ScParameterizedType if x.designator.canonicalText.startsWith(ScalaTypes.FUNCTION_PREFFIX) =>
        if (x.typeArguments.init.length == 1)
          FuncType(genType(x.typeArguments.head), genType(x.typeArguments.last))
        else
          FuncType(ProdType(x.typeArguments.init.map(genType)), genType(x.typeArguments.last))
      case x: ScParameterizedType =>
        ProductType(genType(x.designator), x.typeArguments.map(genType))
      case x =>
        SimpleType(x.canonicalText)
    }
  }

  def genType(t: Option[ScTypeElement]): Type =
    t.flatMap(_.`type`().toOption).map(genType)
      .getOrElse(NoType)

  def genType(t: TypeResult): Type =
    t.map(genType).getOrElse(NoType)

  def blockOrEmpty(exprs: Seq[Expr]): Option[BlockExpr] =
    if (exprs.nonEmpty) Some(BlockExpr(exprs))
    else None

  def genAttrs(x: ScMember): Seq[Attr] = {
    def attr(p: Boolean, a: Attr) =
      if (p) Some(a) else None

    val memberAttrs = (attr(x.isPrivate, PrivAttr) ::
      attr(x.isPublic, PublAttr) ::
      attr(x.isProtected, ProtAttr) ::
      attr(x.hasFinalModifier, FinalAttr) ::
      Nil).flatten
    val extraAttrs = x match {
      case y: ScFunction =>
        attr(y.superSignatures.exists(!_.isInstanceOf[PhysicalSignature]), OverrideAttr).toSeq
      case y: ScTypeDefinition =>
        attr(y.isCase, CaseAttr).toSeq
      case _ => Seq.empty
    }
    memberAttrs ++ extraAttrs
  }

  def gen[T](psi: PsiElement): T = (psi match {
    case x: ScalaFile =>
      FileDef(
        x.getPackageName,
        x.importStatementsInHeader.flatMap(_.importExprs).map(gen[ImportDef]),
        genDefinitions(x)
          .filter {
            case _: PsiClassWrapper => false
            case y: ScObject if y.isSyntheticObject => false
            case _ => true
          }
          .map(gen[DefExpr]))

    case x: ScImportExpr =>
      ImportDef(x.reference.map(_.getText).get, x.importedNames)

    case x: ScTypeDefinition =>
      val construct = x match {
        case y: ScClass => Some(y.constructor.map(gen[Construct]).getOrElse(EmptyConstruct))
        case _ => None
      }

      val overrideConstuctParamsDefs =
        x match {
          case y: ScClass => y.constructor.toSeq.collect {
            case z: ScPrimaryConstructor =>
              z.parameters
                .filter(p => ScalaPsiUtil.superValsSignatures(p).nonEmpty)
          }.flatten
            .map { case p: ScClassParameter =>
              DefnDef(Seq(PublAttr, OverrideAttr),
                p.name,
                genType(p.`type`()),
                Seq.empty,
                genType(p.`type`()),
                Some(RefExpr(genType(p.`type`()), None, p.name, Seq.empty, false)))
            }
          case _ => Seq.empty
        }

      Defn(
        genAttrs(x),
        x match {
          case _: ScClass => ClassDefn
          case _: ScTrait => TraitDefn
          case _: ScObject => ObjDefn
        },
        x.name,
        construct,
        x.extendsBlock
          .findChildrenByType(ScalaElementTypes.CLASS_PARENTS)
          .flatMap { case y: ScClassParents =>
            y.findChildrenByType(ScalaElementTypes.CONSTRUCTOR)
              .map { case z: ScConstructor =>
                Super(genType(z.typeElement.`type`()), None)
              }
          },
        blockOrEmpty(
          overrideConstuctParamsDefs ++ x.extendsBlock.members.map(gen[DefExpr])))
    case x: PsiClassWrapper =>
      gen[DefExpr](x.definition)

    case x: ScFunction =>
      DefnDef(
        genAttrs(x),
        x.name,
        genType(x.`type`()),
        x.parameters.map(gen[DefParam]),
        genType(x.returnType),
        genFunctionBody(x))

    case x: ScBlockExpr if x.hasCaseClauses =>
      LambdaExpr(genType(x.`type`()),
        Seq.empty,
        MatchExpr(genType(x.`type`()), UnderScExpr(NoType), x.caseClauses.get.caseClauses.map(gen[MatchCaseClause])),
        false)

    case x: ScBlock =>
     BlockExpr(x.exprs.map(gen[Expr]))

    case x: ScInfixExpr =>
      BinExpr(genType(x.`type`()), x.operation.getText, gen[Expr](x.left), gen[Expr](x.right))
    case x: ScLiteral =>
      LitExpr(genType(x.`type`()), x.getText)
    case x: ScUnderscoreSection =>
      UnderScExpr(NoType)
    case x: ScParenthesisedExpr =>
      ParenExpr(gen[Expr](x.innerElement.get))
    case x: ScReferenceExpression =>
      val isFunc =
        x.getReference.asInstanceOf[ScReferenceExpressionImpl]
          .shapeResolve
          .map(_.element)
          .exists(_.isInstanceOf[ScFunction])
      RefExpr(
        genType(x.`type`()),
        x.qualifier.map(gen[Expr]),
        x.refName,
        Seq.empty,
        isFunc)

    case x: ScMethodCall =>
      CallExpr(
        genType(x.`type`()),
        gen[Expr](x.getInvokedExpr),
        x.args.exprs.map(gen[Expr]))

    case x: ScGenericCall =>
      gen[RefExpr](x.referencedExpr).copy(typeParams = genTypeArgs(x))

    case x: ScIfStmt =>
      IfExpr(
        genType(x.`type`()),
        gen[Expr](x.condition.get),
        gen[Expr](x.thenBranch.get),
        x.elseBranch.map(gen[Expr]))

    case x: ScMatchStmt =>
      MatchExpr(genType(x.`type`()), gen[Expr](x.expr.get), x.caseClauses.map(gen[MatchCaseClause]))
    case x: ScFunctionExpr =>
      LambdaExpr(genType(x.`type`()), x.parameters.map(gen[DefParam]), gen[Expr](x.result.get), false)

    case x: ScCaseClause =>
      MatchCaseClause(gen[MatchCasePattern](x.pattern.get),
        x.expr.map(gen[Expr]).get,//todo fix
        x.guard.flatMap(_.expr).map(gen[Expr]))

    case x: ScCompositePattern =>
      CompositePatternMatch(x.subpatterns.map(gen[MatchCasePattern]))
    case x: ScLiteralPattern =>
      LitPatternMatch(gen[LitExpr](x.getLiteral))
    case x: ScNamingPattern =>
      gen[ConstructorPatternMatch](x.named).copy(label = Some(x.name))
    case x: ScConstructorPattern =>
      ConstructorPatternMatch(x.ref.qualName, x.args.patterns.map(gen[MatchCasePattern]), None, x.getText)
    case x: ScTypedPattern =>
      TypedPatternMatch(x.name, genType(x.typePattern.map(_.typeElement)))
    case x: ScReferencePattern =>
      ReferencePatternMatch(x.name)
    case x: ScReferenceElement =>
      ReferencePatternMatch(x.refName)
    case x: ScStableReferenceElementPattern =>
      ReferencePatternMatch(x.getReferenceExpression.get.refName)
    case _: ScWildcardPattern =>
      WildcardPatternMatch
    case x: ScPatternDefinition =>
      ValDef(
        x.pList.patterns.map(gen[MatchCasePattern]),
        gen[Expr](x.expr.get))
    case x: ScVariableDefinition =>
      VarDef(
        x.bindings.head.name,
        genType(x.typeElement),
        gen[Expr](x.expr.get))
    case x: ScAssignStmt =>
      AssignExpr(gen[Expr](x.getLExpression), gen[Expr](x.getRExpression.get))
    case x: ScNewTemplateDefinitionImpl =>
      NewExpr(
        genType(x.`type`()),
        x.constructor.get.typeElement.getText,
        x.constructor.get.args.toSeq.flatMap(_.exprs).map(gen[Expr]))
    case x: ScPrimaryConstructor =>
      ParamsConstruct(x.parameters.map(gen[ConstructParam]))

    case x: ScClassParameter =>
      val mod =
        if (x.isPrivate) PrivAttr
        else PublAttr
      val t =
        if (x.isVal) ValKind
        else if (x.isVar) VarKind
        else NoMemberKind
      ConstructParam(t, mod, x.name, genType(x.typeElement))

    case x: ScParameter =>
      DefParam(genType(x.typeElement), x.name)

  }).asInstanceOf[T]
}
