package darthorimar.scalaToKotlinConverter

import darthorimar.scalaToKotlinConverter.ast._
import darthorimar.scalaToKotlinConverter.types.{KotlinTypes, StdTypes}

object Exprs {

  val falseLit: LitExpr = LitExpr(StdTypes.BOOLEAN, "false")
  val trueLit: LitExpr = LitExpr(StdTypes.BOOLEAN, "true")
  val nullLit: LitExpr = LitExpr(NoType, "null") //TODO fix

  def isExpr(expr: Expr, ty: Type): InfixExpr =
    simpleInfix(StdTypes.BOOLEAN, "is", expr, TypeExpr(ty))

  def simpleInfix(resultType: Type, op: String, left: Expr, right: Expr): InfixExpr =
    InfixExpr(
      FunctionType(right.exprType, resultType),
      RefExpr(FunctionType(right.exprType, resultType), None, op, Seq.empty, isFunctionRef = false),
      left,
      right,
      isLeftAssoc = true
    )

  def asExpr(expr: Expr, ty: Type): InfixExpr =
    simpleInfix(StdTypes.BOOLEAN, "as", expr, TypeExpr(ty))

  def andExpr(left: Expr, right: Expr): InfixExpr =
    simpleInfix(StdTypes.BOOLEAN, "&&", left, right)

  def orExpr(left: Expr, right: Expr): InfixExpr =
    simpleInfix(StdTypes.BOOLEAN, "||", left, right)

  def letExpr(obj: Expr, lambda: LambdaExpr): CallExpr =
    CallExpr(lambda.exprType, RefExpr(NoType, Some(obj), "let", Seq.empty, isFunctionRef = true), Seq(lambda), Seq.empty)

  def emptyList(ty: Type): CallExpr =
    CallExpr(listType(ty), RefExpr(ty, None, "emptyList", Seq(ty), isFunctionRef = true), Seq.empty, Seq.empty)

  def listType(ty: Type): GenericType =
    GenericType(KotlinTypes.LIST, Seq(ty))

  def emptyList: CallExpr =
    CallExpr(listType(NoType), RefExpr(NoType, None, "emptyList", Seq.empty, isFunctionRef = true), Seq.empty, Seq.empty)

  def simpleRef(name: String, refType: Type): RefExpr =
    RefExpr(refType, None, name, Seq.empty, isFunctionRef = false)

  def runExpr(expr: Expr): CallExpr =
    simpleCall("run", expr.exprType, Seq(LambdaExpr(expr.exprType, Seq.empty, expr, needBraces = false)))

  def simpleCall(name: String, returnType: Type, arguments: Seq[Expr]): CallExpr =
    CallExpr(FunctionType(ProductType(arguments.map(_.exprType)), returnType),
      RefExpr(returnType, None, name, Seq.empty, isFunctionRef = true),
      arguments,
      Seq.empty)

  def blockOrWrapped(expr: Expr): BlockExpr = expr match {
    case block: BlockExpr => block
    case _ => BlockExpr(Seq(expr))
  }

  def blockOrSingleExpr(exprs: Seq[Expr]): Expr =
    if (exprs.length == 1) exprs.head
    else BlockExpr(exprs)
}
