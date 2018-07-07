package org.jetbrains.plugins.kotlinConverter.pass

import org.jetbrains.plugins.kotlinConverter.ast.Expr._
import org.jetbrains.plugins.kotlinConverter.ast.Stmt._
import org.jetbrains.plugins.kotlinConverter.ast._

trait Pass {
  protected def action(ast: AST): Option[AST]

  private var parentsStack = List.empty[AST]

  protected def parents: List[AST] =
    parentsStack.tail

  protected def parent: AST =
    parents.head

  final def pass[T](ast: AST): T = {
//    println(" " * parentsStack.size + ast.getClass.getSimpleName)
    parentsStack = ast :: parentsStack
    val res = action(ast).getOrElse(copy(ast)).asInstanceOf[T]
    parentsStack = parentsStack.tail
    res
  }

  protected def copy(ast: AST): AST = ast match {
    case Defn(attrs, t, name, construct, supers, block) =>
      Defn(attrs.map(pass[Attr]), t, name, construct.map(pass[Construct]), supers.map(pass[Super]), pass[Block](block))

    case Super(ty, construct) =>
      Super(pass[TypeCont](ty),construct.map(pass[Construct]))
    case EmptyConstruct => EmptyConstruct

    case ParamsConstruct(params) =>
      ParamsConstruct(params.map(pass[ConstructParam]))

    case ConstructParam(parType, mod, name, ty) =>
      ConstructParam(parType, mod, name, pass[TypeCont](ty))

    case ValDef(name, ty, expr) =>
      ValDef(name, pass[TypeCont](ty), pass[Expr](expr))

    case VarDef(name, ty, expr) =>
      VarDef(name, pass[TypeCont](ty), pass[Expr](expr))

    case DefnDef(name, ty, args, body) =>
      DefnDef(name, pass[TypeCont](ty), args.map(pass[DefParam]), pass[Block](body))

    case ImportDef(ref, names) =>
      ImportDef(ref, names)

    case FileDef(pckg, imports, defns) =>
      FileDef(pckg, imports.map(pass[ImportDef]), defns.map(pass[Def]))

    case BinExpr(ty, op, left, right) =>
      BinExpr(pass[TypeCont](ty), op, pass[Expr](left), pass[Expr](right))

    case ParenExpr(inner) =>
      ParenExpr(pass[Expr](inner))

    case Call(ty, ref, typeParams, params) =>
      Call(pass[TypeCont](ty), pass[Expr](ref), typeParams.map(pass[TypeParam]), params.map(pass[Expr]))

    case Lit(ty, name) =>
      Lit(pass[TypeCont](ty), name)

    case UnderSc =>
      UnderSc

    case Ref(ty, name) =>
      Ref(pass[TypeCont](ty), name)

    case Match(expr, clauses) =>
      Match(pass[Expr](expr), clauses.map(pass[CaseClause]))

    case MultiBlock(stmts) =>
      MultiBlock(stmts.map(pass[Expr]))

    case SingleBlock(stmt) =>
      SingleBlock(pass[Expr](stmt))

    case EmptyBlock =>
      EmptyBlock

    case Assign(left, right) =>
      Assign(pass[Expr](left), pass[Expr](right))

    case New(name, args) =>
      New(name, args.map(pass[Expr]))

    case Lambda(params, expr) =>
      Lambda(params.map(pass[DefParam]), pass[Expr](expr))

    case If(cond, trueB, falseB) =>
      If(pass[Expr](cond), pass[Block](trueB), pass[Block](falseB))

    case For(range, body) =>
      For(pass[Expr](range), pass[Block](body))

    case While(cond, body) =>
      While(pass[Expr](cond), pass[Block](body))

    case TypeCont(real, inferenced) =>
      TypeCont(real, inferenced)

    case FuncType(left, right) =>
      FuncType(pass[Type](left), pass[Type](right))
    case ProdType(types) =>
      ProdType(types.map(pass[Type]))

    case SimpleType(name) =>
      SimpleType(name)

    case DefParam(ty, name) =>
      DefParam(pass[TypeCont](ty), name)

    case CaseClause(pattern, expr) =>
      CaseClause(pattern, pass[Expr](expr))

    case TypeParam(ty) =>
      TypeParam(ty)

    case LitPattern(lit) =>
      LitPattern(lit)

    case ConstructorPattern(ref, args) =>
      ConstructorPattern(ref, args.map(pass))

    case TypedPattern(ref, ty) =>
      TypedPattern(ref, pass[TypeCont](ty))

    case ReferencePattern(ref) =>
      ReferencePattern(ref)

    case WildcardPattern =>
      WildcardPattern

    case EmptyAst => EmptyAst
    case x: Keyword => x
  }
}

object Pass {
  def applyPasses(ast: AST): AST = {
    new BasicPass().pass[AST](ast)
  }
}