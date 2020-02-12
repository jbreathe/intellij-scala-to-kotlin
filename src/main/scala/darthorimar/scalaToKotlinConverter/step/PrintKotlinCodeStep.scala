package darthorimar.scalaToKotlinConverter.step

import darthorimar.scalaToKotlinConverter.BuilderBase
import darthorimar.scalaToKotlinConverter.ast._
import darthorimar.scalaToKotlinConverter.scopes.ScopedVal.scoped
import darthorimar.scalaToKotlinConverter.scopes.{BuilderState, ScopedVal}
import darthorimar.scalaToKotlinConverter.step.ConverterStep.Notifier

class PrintStringStep extends ConverterStep[AST, String] {
  override def name: String = "Generating Kotlin Code"

  override def apply(from: AST,
                     state: ConverterStepState,
                     index: Int,
                     notifier: Notifier): (String, ConverterStepState) = {
    notifier.notify(this, index)
    val builder = new KotlinBuilder
    builder.gen(from)
    (builder.text, state) //not modifying state
  }

}

class KotlinBuilder extends BuilderBase {
  val stateVal: ScopedVal[BuilderState] = new ScopedVal[BuilderState](BuilderState())

  def gen(ast: AST): Unit =
    ast match {
      case e: ErrorAst =>
        str(s"/* ERROR converting `${e.text}`*/")

      case File(pckg, defns) =>
        if (pckg.trim.nonEmpty) {
          str("package ")
          str(pckg)
          nl()
        }
        repNl(defns)(gen)

      case ExprContainer(exprs) =>
        repNl(exprs)(gen)

      case Defn(attrs, t, name, typeParams, construct, supersBlock, block, _) =>
        rep(attrs, " ")(gen)
        if (attrs.nonEmpty) str(" ")
        genKeyword(t)
        str(" ")
        str(name)
        if (typeParams.nonEmpty) {
          str("<")
          rep(typeParams, ", ")(gen)
          str(">")
        }
        opt(construct)(gen)
        opt(supersBlock) {
          case SupersBlock(constructor, supers) =>
            str(" : ")
            opt(constructor) {
              case SuperConstructor(exprType, exprs, needBrackets) =>
                genType(exprType, pref = false)
                if (needBrackets || exprs.nonEmpty) str("(")
                rep(exprs, ", ")(gen)
                if (needBrackets || exprs.nonEmpty) str(")")
            }
            if (constructor.isDefined && supers.nonEmpty) {
              str(", ")
            }
            rep(supers, ", ")(genType(_, pref = false))
        }
        str(" ")
        opt(block)(genAsBlock)

      case EmptyConstructor =>
      case ParamsConstructor(params) =>
        str("(")
        rep(params, ", ")(gen)
        str(")")

      case ConstructorParam(parType, mod, name, exprType) =>
        gen(mod)
        str(" ")
        gen(parType)
        str(" ")
        str(name)
        genType(exprType)

      case x@SimpleValOrVarDef(attributes, _, name, valType, expr) =>
        rep(attributes, " ")(gen)
        str(" ")
        str(x.keyword)
        str(" ")
        str(name)
        opt(valType)(genType(_))
        opt(expr) { e =>
          str(" = ")
          gen(e)
        }

      case x@KotlinValOrVarDef(attributes, _, patterns, expr) =>
        rep(attributes, " ")(gen)
        str(" ")
        str(x.keyword)
        str(" (")
        rep(patterns, ", ")(gen)
        str(")")
        str(" = ")
        gen(expr)

      case ReferenceKotlinValDestructor(reference) =>
        str(reference)

      case TypedKotlinValDestructor(reference, valType) =>
        str(reference)
        str(": ")
        genType(valType, pref = false)

      case WildcardKotlinValDestructor =>
        str("_")

      case LazyValDef(attributes, name, _, expr) =>
        rep(attributes, " ")(gen)
        str("val ")
        str(name)
        str(" by lazy ")
        genBlockOrExpr(expr)

      case ReturnExpr(label, expr) =>
        str("return")
        opt(label) { l =>
          str("@")
          str(l)
        }
        str(" ")
        opt(expr)(gen)

      case p: CasePattern =>
        str(p.representation)

      case DefnDef(attrs, receiver, name, typeParams, args, retType, body) =>
        rep(attrs, " ")(gen)
        if (attrs.nonEmpty) str(" ")
        str("fun")
        if (typeParams.nonEmpty) {
          str("<")
          rep(typeParams, ", ")(gen)
          str(">")
        }
        str(" ")
        opt(receiver) { receiverType =>
          genType(receiverType, pref = false)
          str(".")
        }
        str(name)
        str("(")
        rep(args, ", ") {
          case DefParameter(parameterType, parameterName, isVarArg, _) =>
            if (isVarArg) str("vararg ")
            str(parameterName)
            genType(parameterType)
        }
        str(")")
        genType(retType)
        str(" ")
        opt(body) { b =>
          if (!b.isInstanceOf[BlockExpr]) {
            str("=")
            gen(b)
          } else genAsBlock(b)
        }

      case KotlinTryExpr(_, tryBlock, catches, finallyBlock) =>
        str("try ")
        genAsBlock(tryBlock)
        rep(catches, " ") {
          case KotlinCatchCase(name, valueType, expr) =>
            str("catch (")
            str(name)
            genType(valueType)
            str(") ")
            genAsBlock(expr)
        }
        opt(finallyBlock) { f =>
          str(" finally ")
          genAsBlock(f)
        }

      case Import(reference) =>
        str("import ")
        str(reference)

      case InfixExpr(_, op, left, right, _) =>
        gen(left)
        str(" ")
        gen(op)
        str(" ")
        gen(right)
      case ParenthesesExpr(inner) =>
        str("(")
        gen(inner)
        str(")")
      case LambdaExpr(_, params, expr, needBraces) =>
        str("{ ")
        if (needBraces) str("(")
        rep(params, ", ") {
          case DefParameter(_, name, _, _) =>
            str(name)
        }
        if (needBraces) str(")")
        if (params.nonEmpty) str(" -> ")
        expr match {
          case b: BlockExpr =>
            repNl(b.exprs)(gen)
          case _ =>
            gen(expr)
        }
        str(" }")

      case AssignExpr(left, right) =>
        gen(left)
        str(" = ")
        gen(right)

      case TypeExpr(exprType) =>
        genType(exprType, pref = false)

      case CallExpr(_, ref, params, _) =>
        gen(ref)
        if (params.size == 1 && params.head.isInstanceOf[LambdaExpr]) {
          str(" ")
          gen(params.head)
        } else {
          str("(")
          rep(params, ", ")(gen)
          str(")")
        }

      case RefExpr(_, obj, ref, typeParams, _) =>
        opt(obj) { x =>
          gen(x); str(".")
        }
        str(ref)
        if (typeParams.nonEmpty) {
          str("<")
          rep(typeParams, ", ")(genType(_, pref = false))
          str(">")
        }

      case IfExpr(_, cond, trueB, falseB) =>
        str("if (")
        gen(cond)
        str(") ")
        genBlockOrExpr(trueB)
        opt(falseB) { b =>
          str(" else ")
          genBlockOrExpr(b)
        }

      case PostfixExpr(_, obj, op) =>
        gen(obj)
        str(op)

      case PrefixExpr(_, obj, op) =>
        str(op)
        gen(obj)

      case LitExpr(_, name) =>
        str(name)
      case UnderscoreExpr(_) =>
        str("it")

      case WhenExpr(_, expr, clauses) =>
        str("when")
        opt(expr) { e =>
          str(" (")
          gen(e)
          str(")")
        }
        str(" {")
        indented {
          repNl(clauses) {
            case ExprWhenClause(clause, expr) =>
              gen(clause)
              str(" -> ")
              genAsBlock(expr)
            case ElseWhenClause(expr) =>
              str("else -> ")
              genBlockOrExpr(expr)
          }
        }
        str("}")

      case NewExpr(instanceType, args) =>
        genType(instanceType, pref = false)
        str("(")
        rep(args, ", ")(gen)
        str(")")

      case e: BlockExpr =>
        genRunBlock(e)

      case ForInExpr(_, ref, range, body) =>
        str("for (")
        gen(ref)
        str(" in ")
        gen(range)
        str(") ")
        genBlockOrExpr(body)

      case InterpolatedStringExpr(parts, injected) =>
        scoped(
          stateVal.update(_.copy(inInterpolatedString = true))
        ) {
          str("\"")
          rep(parts.zip(injected), "") {
            case (p, i) =>
              str(p)
              str("$")
              gen(i)
          }
          str(parts.last)
          str("\"")
        }

      case BracketsExpr(_, expr, inBrackets) =>
        gen(expr)
        str("[")
        gen(inBrackets)
        str("]")

      case ThisExpr(_) =>
        str("this")

      case TypeParam(name, variance, upperBound, lowerBound) =>
        str(variance.kotlinKeyword)
        if (!variance.isInvariant) str(" ")
        str(name)
        opt(upperBound) { b =>
          str(" : ")
          genType(b, pref = false)
        }
        opt(lowerBound) { b =>
          str("/* Kotlin does not support lower bounds :( Lower bound was ")
          genType(b)
          str("*/")
        }

      case ThrowExpr(expr) =>
        str("throw ")
        gen(expr)

      case EmptyDefExpr =>
      case x: Keyword =>
        genKeyword(x)
    }

  def genBlock(block: BlockExpr): Unit = block.exprs match {
    case Seq(b: BlockExpr) =>
      genBlock(b)
    case exprs =>
      str("{")
      indentedIf(!stateVal.inInterpolatedString) {
        repNl(exprs)(gen)
      }
      str("}")
  }

  def genAsBlock(e: Expr): Unit = e match {
    case b: BlockExpr =>
      genBlock(b)
    case _ =>
      genAsBlock(BlockExpr(Seq(e)))
  }

  def genBlockOrExpr(expr: Expr): Unit = expr match {
    case b: BlockExpr => genAsBlock(b)
    case e => gen(e)
  }

  def genRunBlock(block: BlockExpr): Unit = block.exprs match {
    case Seq(b: BlockExpr) =>
      gen(b)
    case _ =>
      if (!stateVal.inInterpolatedString) str("run ")
      genBlock(block)
  }

  def genKeyword(k: Keyword): Unit =
    str(k.name)

  def genType(t: Type, pref: Boolean = true): Unit = {
    if (pref) str(": ")
    str(t.asKotlin)
  }
}
