package darthorimar.scalaToKotlinConverter.step.transform

import darthorimar.scalaToKotlinConverter.ast.{ReturnExpr, _}
import darthorimar.scalaToKotlinConverter.definition.Definition
import darthorimar.scalaToKotlinConverter.scopes.ScopedVal.scoped
import darthorimar.scalaToKotlinConverter.types.StdTypes
import darthorimar.scalaToKotlinConverter.{Exprs, Utils, ast}

object MatchUtils {

  def createConstructorValOrVarDefinition(constructorPattern: ConstructorPattern, isVal: Boolean, initValue: Expr): KotlinValOrVarDef =
    KotlinValOrVarDef(Seq.empty,
      isVal,
      collectVals(constructorPattern).map(p => ReferenceKotlinValDestructor(p.name)),
      initValue)

  def expandCompositePatternAndApplyTransform(clauses: Seq[MatchCaseClause],
                                              transformInst: Transform): Seq[MatchCaseClause] = {
    import transformInst._

    clauses flatMap {
      case MatchCaseClause(CompositePattern(parts, _), expr, guard) =>
        parts.map { p =>
          MatchCaseClause(p, expr, guard)
        }
      case x => Seq(x)
    } map {
      case MatchCaseClause(pattern, expr, guard) =>
        MatchCaseClause(transform[CasePattern](pattern), expr, guard.map(transform[Expr]))
    }
  }

  def generateDataClassByConstructorPattern(constructorPattern: ConstructorPattern): Defn = {
    val name = Utils.escapeName(s"${constructorPattern.representation}_data")
    val vals = collectVals(constructorPattern)
    Defn(Seq(DataAttribute), ClassDefn, name, Seq.empty, Some(ParamsConstructor(vals)), None, None, None)
  }

  def generateInitialisationExprByConstructorPattern(constructorPattern: ConstructorPattern,
                                                     valRef: RefExpr,
                                                     generateSuccessExpr: CallExpr => Expr,
                                                     errorExpr: Expr,
                                                     transformInst: Transform): BlockExpr = {
    import transformInst._
    val params =
      collectVals(constructorPattern).map(v => RefExpr(NoType, None, v.name, Seq.empty, isFunctionRef = false))
    val callConstructor =
      CallExpr(NoType,
        RefExpr(NoType,
          None,
          Utils.escapeName(s"${constructorPattern.representation}_data"),
          Seq.empty,
          isFunctionRef = true),
        params,
        Seq.empty)

    val finalExpr = generateSuccessExpr(callConstructor)

    val refName: String = constructorPattern.ref match {
      case _: CaseClassConstructorRef => valRef.referenceName
      case UnapplyCallConstructorRef(_, _) =>
        namerVal.newName("l")
    }

    val innerBodyExprs =
      handleConstructors(Seq((refName, constructorPattern)), finalExpr, valRef, transformInst)

    val condition = genTypeCheckCondition(refName, constructorPattern.ref, valRef)

    val valForUnapplyConstructorRef = constructorPattern.ref match {
      case UnapplyCallConstructorRef(objectName, _) =>
        val unapplyRef =
          RefExpr(NoType, Some(Exprs.simpleRef(objectName, NoType)), "unapply", Seq.empty, isFunctionRef = true)
        val unapplyCall = CallExpr(NoType, unapplyRef, Seq(valRef), Seq.empty)
        Seq(SimpleValOrVarDef(Seq.empty, isVal = true, refName, None, Some(unapplyCall)))
      case _ => Seq.empty
    }

    BlockExpr(
      valForUnapplyConstructorRef ++
        Seq(
          IfExpr(NoType, condition, BlockExpr(innerBodyExprs), None),
          errorExpr
        ))
  }

  def collectVals(constructorPatternMatch: ConstructorPattern): Seq[ConstructorParam] = {
    constructorPatternMatch.patterns.flatMap {
      case _: LitPattern =>
        Seq.empty
      case ReferencePattern(ref, _) =>
        Seq(ConstructorParam(ValKind, PublicAttribute, ref, NoType))
      case _: WildcardPattern =>
        Seq.empty
      case c: ConstructorPattern =>
        collectVals(c)
      case TypedPattern(ref, exprTypePattern, _) =>
        Seq(ConstructorParam(ValKind, PublicAttribute, ref, exprTypePattern))
      case _: CompositePattern => Seq.empty
    }
  }

  def collectConstructors(constructors: Seq[(String, CasePattern)],
                          transform: Transform): (Seq[KotlinValOrVarDef], Seq[Expr], Seq[(String, CasePattern)]) = {
    import transform._
    def handlePattern(pattern: CasePattern): (KotlinValDestructor, Option[InfixExpr], Option[(String, CasePattern)]) =
      pattern match {
        case LitPattern(expr, label) =>
          val local = label.getOrElse(namerVal.get.newName("l"))
          (ReferenceKotlinValDestructor(local),
            Some(Exprs.simpleInfix(StdTypes.BOOLEAN, "==", Exprs.simpleRef(local, expr.exprType), expr)),
            None)
        case ReferencePattern(referenceName, _) =>
          (ReferenceKotlinValDestructor(referenceName), None, None)
        case WildcardPattern(label) =>
          (label.map(ReferenceKotlinValDestructor).getOrElse(WildcardKotlinValDestructor), None, None)
        case p@ConstructorPattern(CaseClassConstructorRef(name), _, label, _) =>
          val local = label.getOrElse(namerVal.get.newName("l"))
          (ReferenceKotlinValDestructor(local),
            Some(Exprs.isExpr(Exprs.simpleRef(local, NoType), name)),
            Some(local -> p))
        case p@ConstructorPattern(_: UnapplyCallConstructorRef, _, label, _) =>
          val local = label.getOrElse(namerVal.get.newName("l"))
          (ReferenceKotlinValDestructor(local), None, Some(local -> p))
        case TypedPattern(referenceName, patternType, _) =>
          (ReferenceKotlinValDestructor(referenceName),
            Some(Exprs.isExpr(LitExpr(NoType, referenceName), patternType)),
            None)
        case p@CompositePattern(_, label) =>
          val local = label.getOrElse(namerVal.get.newName("l"))
          (ReferenceKotlinValDestructor(local), None, Some(local -> p))
      }

    val (vals, conds, refs) = constructors.map {
      case (r, ConstructorPattern(constructorRef, patterns, _, _)) =>
        val (destructors, conds, refs) = patterns.map(handlePattern).unzip3

        def rightSide = constructorRef match {
          case CaseClassConstructorRef(_) => Exprs.simpleRef(r, NoType)
          case UnapplyCallConstructorRef(objectName, unapplyReturnType) =>
            val unapplyRef = RefExpr(NoType, Some(Exprs.simpleRef(objectName, NoType)), "unapply", Seq.empty, isFunctionRef = true)
            val callExpr = CallExpr(unapplyReturnType, unapplyRef, Seq(Exprs.simpleRef(r, NoType)), Seq.empty)
            Exprs.simpleInfix(unapplyReturnType, "?:", callExpr, ReturnExpr(Some("lazy"), Some(Exprs.nullLit)))
        }

        val valDef = ast.KotlinValOrVarDef(Seq.empty, isVal = true, destructors, rightSide)
        (Seq(valDef), conds.flatten, refs.flatten)

      case (r, p: CompositePattern) =>
        (Seq.empty, Seq.empty, Seq(r -> p))
      case (_, otherPattern) =>
        val (_, cond, ref) = handlePattern(otherPattern)
        (Seq.empty, cond.toSeq, ref.toSeq)

    }.unzip3
    (vals.flatten, conds.flatten, refs.flatten)
  }

  def genTypeCheckCondition(refName: String, constructorRef: ConstructorRef, valRef: RefExpr): Expr =
    constructorRef match {
      case CaseClassConstructorRef(ScalaType("scala.Some")) =>
        Exprs.simpleInfix(StdTypes.BOOLEAN, "!=", valRef, Exprs.nullLit)

      case CaseClassConstructorRef(constructorType) =>
        Exprs.isExpr(Exprs.simpleRef(refName, NoType), constructorType)

      case UnapplyCallConstructorRef(_, unapplyReturnType) =>
        val ref = Exprs.simpleRef(refName, unapplyReturnType)
        val notNullExpr = Exprs.simpleInfix(StdTypes.BOOLEAN, "!=", ref, Exprs.nullLit)
        val isExpr = Exprs.isExpr(ref, unapplyReturnType match {
          case NullableType(inner) => inner
          case t => t
        })
        Exprs.andExpr(notNullExpr, isExpr)
    }

  def convertMatchToWhen(valRef: RefExpr,
                         clauses: Seq[MatchCaseClause],
                         exprType: Type,
                         transformInst: Transform): Seq[Expr] = {
    import transformInst._

    val expandedClauses = expandCompositePatternAndApplyTransform(clauses, transformInst)

    val caseClasses = expandedClauses collect {
      case MatchCaseClause(pattern: ConstructorPattern, _, _) =>
        generateDataClassByConstructorPattern(pattern)
    }

    val lazyDefs = expandedClauses.collect {
      case MatchCaseClause(pattern: ConstructorPattern, _, guard) =>
        val generateSuccessExpr = (callConstructor: CallExpr) => {
          val returnExpr = ReturnExpr(Some("lazy"), Some(callConstructor))
          guard match {
            case Some(g) => IfExpr(NoType, g, returnExpr, None)
            case None => returnExpr
          }
        }
        val errorExpr = ReturnExpr(Some("lazy"), Some(Exprs.nullLit))
        val body =
          generateInitialisationExprByConstructorPattern(pattern, valRef, generateSuccessExpr, errorExpr, transformInst)
        LazyValDef(Seq.empty, Utils.escapeName(pattern.representation), body.exprType, body)

    }

    def addGuardExpr(expr: Expr, guard: Option[Expr]) =
      guard match {
        case Some(g) => Exprs.andExpr(expr, g)
        case None => expr
      }

    val whenClauses =
      expandedClauses
        .map {
          case MatchCaseClause(LitPattern(lit, _), e, guard) =>
            val equalsExpr = Exprs.simpleInfix(StdTypes.BOOLEAN, "==", valRef, lit)
            ExprWhenClause(addGuardExpr(equalsExpr, guard), transform[Expr](e))

          case MatchCaseClause(WildcardPattern(_), e, guard) =>
            guard match {
              case Some(g) => ExprWhenClause(transform[Expr](g), transform[Expr](e))
              case None => ElseWhenClause(transform[Expr](e))
            }

          case MatchCaseClause(ReferencePattern(ref, _), e, guard) =>
            scoped(
              renamerVal.update(_.add(ref -> valRef))
            ) {
              guard match {
                case Some(g) => ExprWhenClause(transform[Expr](g), transform[Expr](e))
                case None => ElseWhenClause(transform[Expr](e))
              }
            }

          case MatchCaseClause(TypedPattern(ref, patternTy, _), e, guard) =>
            scoped(
              renamerVal.update(_.add(ref -> valRef))
            ) {
              ExprWhenClause(addGuardExpr(Exprs.isExpr(valRef, patternTy), guard.map(transform[Expr])),
                transform[Expr](e))
            }

          case MatchCaseClause(pattern@ConstructorPattern(_, _, _, repr), e, _) =>
            val lazyRef = RefExpr(NoType, None, Utils.escapeName(repr), Seq.empty, isFunctionRef = false)
            val notEqualsExpr = Exprs.simpleInfix(StdTypes.BOOLEAN, "!=", lazyRef, Exprs.nullLit)
            val valDef =
              createConstructorValOrVarDefinition(pattern, isVal = true, lazyRef)
            val body = e match {
              case BlockExpr(exprs) =>
                BlockExpr(valDef +: exprs)
              case expr =>
                BlockExpr(Seq(valDef, expr))
            }
            ExprWhenClause(notEqualsExpr, transform[Expr](body))
        }
        .span(_.isInstanceOf[ExprWhenClause]) match { //take all before first `else` including it
        case (h, t) => h ++ t.headOption.toSeq
      }
    val elseClause = if (!whenClauses.exists {
      case _: ElseWhenClause => true
      case _ => false
    }) {
      stateStepVal.addDefinition(Definition.matchError)
      val exception = NewExpr(ClassType("MatchError"), Seq(valRef))
      Seq(ElseWhenClause(ThrowExpr(exception)))
    } else Seq.empty

    val whenExpr = WhenExpr(NoType, None, whenClauses ++ elseClause)
    (caseClasses ++ lazyDefs) :+ whenExpr
  }

  private def handleConstructors(constructors: Seq[(String, CasePattern)],
                                 defaultCase: Expr,
                                 valRef: RefExpr,
                                 transformInst: Transform): Seq[Expr] = {
    import transformInst._
    val (valDefns, conditionParts, collectedPatterns) = collectConstructors(constructors, transformInst)
    val collectedConstructors =
      collectedPatterns.collect { case (ref, c: ConstructorPattern) => (ref, c) }

    val innerBlock =
      if (collectedConstructors.nonEmpty) {
        val exprs = handleConstructors(collectedConstructors, defaultCase, valRef, transformInst)
        Exprs.blockOrSingleExpr(exprs)
      } else defaultCase

    val trueBlock = {
      val collectedCompositePatterns =
        collectedPatterns.collect { case (ref, p: CompositePattern) => (ref, p) }

      val valDefs =
        collectedCompositePatterns.map {
          case (ref, CompositePattern(parts, _)) =>
            val returnFalseExpr = ReturnExpr(Some("run"), Some(Exprs.falseLit))
            val returnTrueExpr = ReturnExpr(Some("run"), Some(Exprs.trueLit))
            parts.map { part =>
              val exprs = handleConstructors(Seq((ref, part)), returnTrueExpr, valRef, transformInst)
              val block = BlockExpr(exprs)
              val finalExpr = part match {
                case c: ConstructorPattern =>
                  BlockExpr(
                    Seq(IfExpr(NoType, genTypeCheckCondition(ref, c.ref, valRef), block, None), returnFalseExpr))
                case _ => block.copy(exprs = block.exprs :+ returnFalseExpr)
              }
              SimpleValOrVarDef(Seq.empty, isVal = true, namerVal.newName("f"), None, Some(finalExpr))
            }
        }

      lazy val condition =
        valDefs flatMap { parts =>
          if (parts.nonEmpty)
            Some(
              ParenthesesExpr(parts map { part =>
                Exprs.simpleRef(part.name, NoType)
              } reduce Exprs.orExpr)
            )
          else None
        } reduce Exprs.andExpr

      val ifExpr =
        if (valDefs.nonEmpty)
          IfExpr(NoType, condition, innerBlock, None)
        else innerBlock

      BlockExpr(valDefs.flatten :+ ifExpr)
    }

    val ifCond =
      if (conditionParts.nonEmpty)
        IfExpr(NoType, conditionParts.reduceLeft(Exprs.andExpr), trueBlock, None)
      else trueBlock

    valDefns :+ ifCond
  }
}
