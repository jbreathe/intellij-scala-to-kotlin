package darthorimar.scalaToKotlinConverter.ast

import darthorimar.scalaToKotlinConverter.types.StdTypes

sealed trait Expr extends AST {
  def exprType: Type
}

case class ErrorExpr(text: String) extends Expr with ErrorAst {
  override def exprType: Type = NoType
}

case class InfixExpr(exprType: Type, op: RefExpr, left: Expr, right: Expr, isLeftAssoc: Boolean) extends Expr {
  def rightOrder: (Expr, Expr) =
    if (isLeftAssoc) (left, right)
    else (right, left)
}

case class ParenthesesExpr(inner: Expr) extends Expr {
  override def exprType: Type = inner.exprType
}

case class CallExpr(exprType: Type, ref: Expr, params: Seq[Expr], paramsExpectedTypes: Seq[CallParameterInfo])
  extends Expr

case class LitExpr(exprType: Type, name: String) extends Expr

case class UnderscoreExpr(exprType: Type) extends Expr

case class RefExpr(exprType: Type,
                   referencedObject: Option[Expr],
                   referenceName: String,
                   typeParams: Seq[Type],
                   isFunctionRef: Boolean)
  extends Expr

case class PostfixExpr(exprType: Type, expr: Expr, op: String) extends Expr

case class PrefixExpr(exprType: Type, expr: Expr, op: String) extends Expr

case class MatchExpr(exprType: Type, expr: Expr, clauses: Seq[MatchCaseClause]) extends Expr

case class WhenExpr(exprType: Type, expr: Option[Expr], clauses: Seq[WhenClause]) extends Expr

case class BracketsExpr(exprType: Type, expr: Expr, inBrackets: Expr) extends Expr

case class AssignExpr(left: Expr, right: Expr) extends Expr {
  override def exprType: Type = SimpleType("Unit")
}

case class NewExpr(instanceType: Type, arguments: Seq[Expr]) extends Expr {
  override def exprType: Type = instanceType
}

case class LambdaExpr(exprType: Type, parameters: Seq[DefParameter], expr: Expr, needBraces: Boolean) extends Expr

case class ThrowExpr(expr: Expr) extends Expr {
  override def exprType: Type = StdTypes.NOTHING
}

case class IfExpr(exprType: Type, condition: Expr, trueBranch: Expr, falseBranch: Option[Expr]) extends Expr

case class ForExpr(exprType: Type, generators: Seq[ForEnumerator], isYield: Boolean, body: Expr) extends Expr

case class ForInExpr(exprType: Type, value: RefExpr, range: Expr, body: Expr) extends Expr

case class WhileExpr(exprType: Type, condition: Expr, body: BlockExpr) extends Expr

case class ThisExpr(exprType: Type) extends Expr

case class InterpolatedStringExpr(parts: Seq[String], injected: Seq[Expr]) extends Expr {
  override def exprType: Type = StdTypes.STRING
}

case class ReturnExpr(label: Option[String], expr: Option[Expr]) extends Expr {
  override def exprType: Type = StdTypes.NOTHING
}

case class ScalaTryExpr(exprType: Type, tryBlock: Expr, catchBlock: Option[ScalaCatch], finallyBlock: Option[Expr])
  extends Expr

case class KotlinTryExpr(exprType: Type, tryBlock: Expr, catchBlock: Seq[KotlinCatchCase], finallyBlock: Option[Expr])
  extends Expr

case class TypeExpr(exprType: Type) extends Expr

case class BlockExpr(exprs: Seq[Expr]) extends Expr {
  def isSingle: Boolean = exprs.size == 1

  def isEmpty: Boolean = exprs.isEmpty

  override def exprType: Type =
    if (exprs.isEmpty) NoType
    else exprs.last.exprType
}

case class ExprContainer(exprs: Seq[Expr]) extends DefExpr {
  override def exprType: Type =
    if (exprs.isEmpty) NoType
    else exprs.last.exprType

  override def attributes: Seq[Attribute] = Seq.empty
}

sealed trait DefExpr extends Expr {
  override def exprType: Type = NoType

  def attributes: Seq[Attribute]

  def isDefn: Boolean = false

  def isClassDefn: Boolean = false

  def isObjectDefn: Boolean = false
}

case class Defn(attributes: Seq[Attribute],
                defnType: DefnType,
                name: String,
                typeParams: Seq[TypeParam],
                constructor: Option[Constructor],
                supersBlock: Option[SupersBlock],
                body: Option[BlockExpr],
                companionDefn: Option[CompanionModule])
  extends DefExpr {
  override def exprType: Type = NoType

  override def isDefn: Boolean = true

  override def isClassDefn: Boolean = defnType == ClassDefn

  override def isObjectDefn: Boolean = defnType == ObjDefn
}

object EmptyDefExpr extends DefExpr {
  override def attributes: Seq[Attribute] = Seq.empty
}

trait ValOrVarDef extends DefExpr {
  def isVal: Boolean

  def keyword: String =
    if (isVal) "val"
    else "var"
}

case class ScalaValOrVarDef(attributes: Seq[Attribute], isVal: Boolean, pattern: ConstructorPattern, expr: Expr)
  extends ValOrVarDef

case class KotlinValOrVarDef(attributes: Seq[Attribute], isVal: Boolean, patterns: Seq[KotlinValDestructor], expr: Expr)
  extends ValOrVarDef

case class SimpleValOrVarDef(attributes: Seq[Attribute],
                             isVal: Boolean,
                             name: String,
                             valType: Option[Type],
                             expr: Option[Expr])
  extends ValOrVarDef

case class LazyValDef(attributes: Seq[Attribute], name: String, valType: Type, expr: Expr) extends ValOrVarDef {
  override def isVal: Boolean = true
}

case class DefnDef(attributes: Seq[Attribute],
                   receiver: Option[Type],
                   name: String,
                   typeParameters: Seq[TypeParam],
                   parameters: Seq[DefParameter],
                   returnType: Type,
                   body: Option[Expr])
  extends DefExpr
