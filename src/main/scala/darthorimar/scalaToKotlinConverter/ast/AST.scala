package darthorimar.scalaToKotlinConverter.ast

trait AST

trait ErrorAst extends AST {
  def text: String
}

case class ErrorCasePattern(text: String) extends CasePattern with ErrorAst {
  override def representation: String = ""

  override def label: Option[String] = None
}

case class ErrorWhenClause(text: String) extends WhenClause with ErrorAst

case class ErrorForEnumerator(text: String) extends ForEnumerator with ErrorAst

case class DefParameter(parameterType: Type, name: String, isVarArg: Boolean, isCallByName: Boolean) extends AST

case class MatchCaseClause(pattern: CasePattern, expr: Expr, guard: Option[Expr]) extends AST

sealed trait Constructor extends AST

case class ParamsConstructor(parameters: Seq[ConstructorParam]) extends Constructor

case object EmptyConstructor extends Constructor

case class ConstructorParam(kind: MemberKind, modifier: Attribute, name: String, parameterType: Type) extends AST

case class File(packageName: String, definitions: Seq[DefExpr]) extends AST

sealed trait CasePattern extends AST {
  def representation: String

  def isConstructorPattern: Boolean = false

  def label: Option[String]
}

case class CompositePattern(parts: Seq[CasePattern], label: Option[String]) extends CasePattern {
  override def representation: String = parts.mkString(" | ")
}

case class LitPattern(expr: Expr, label: Option[String]) extends CasePattern {
  override def representation: String = expr match {
    case LitExpr(_, name) => name
    case RefExpr(_, _, ref, _, _) => ref
  }
}

trait ConstructorRef extends AST

case class CaseClassConstructorRef(name: Type) extends ConstructorRef

case class UnapplyCallConstructorRef(objectName: String, unapplyReturnType: Type) extends ConstructorRef

case class ConstructorPattern(ref: ConstructorRef,
                              patterns: Seq[CasePattern],
                              label: Option[String],
                              representation: String)
  extends CasePattern {
  override def isConstructorPattern: Boolean = true
}

case class TypedPattern(referenceName: String, patternType: Type, label: Option[String]) extends CasePattern {
  override def representation: String = s"$referenceName: ${patternType.asKotlin}"
}

case class ReferencePattern(referenceName: String, label: Option[String]) extends CasePattern {
  override def representation: String = referenceName
}

case class WildcardPattern(label: Option[String]) extends CasePattern {
  override def representation: String = "_"
}

sealed trait WhenClause extends AST

case class ExprWhenClause(clause: Expr, expr: Expr) extends WhenClause

case class ElseWhenClause(expr: Expr) extends WhenClause

sealed trait ForEnumerator extends AST

case class ForGenerator(pattern: CasePattern, expr: Expr) extends ForEnumerator

case class ForGuard(condition: Expr) extends ForEnumerator

case class ForVal(valDefExpr: Expr) extends ForEnumerator

case class SupersBlock(constructor: Option[SuperConstructor], supers: Seq[Type]) extends AST

case class SuperConstructor(constructorType: Type, exprs: Seq[Expr], needBrackets: Boolean) extends AST

case class ScalaCatch(cases: Seq[MatchCaseClause]) extends AST

case class KotlinCatchCase(name: String, valueType: Type, expr: Expr) extends AST

sealed trait CompanionModule extends AST

case class ClassCompanion(companion: Defn) extends CompanionModule

case object ObjectCompanion extends CompanionModule

case class CallParameterInfo(expectedType: Type, isCallByName: Boolean) extends AST

case class Import(ref: String) extends AST

case class RefWithQualifier(qualifier: Option[String], ref: String) extends AST {
  def qualified: String = qualifier.map(_ + ".").getOrElse("") + ref
}

trait KotlinValDestructor extends AST

case class ReferenceKotlinValDestructor(reference: String) extends KotlinValDestructor

case object WildcardKotlinValDestructor extends KotlinValDestructor

case class TypedKotlinValDestructor(ref: String, valType: Type) extends KotlinValDestructor
