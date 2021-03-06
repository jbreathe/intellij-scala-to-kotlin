package darthorimar.scalaToKotlinConverter.ast

sealed trait Type extends AST {
  def asKotlin: String

  def isFunction: Boolean = false
}

case class FunctionType(left: Type, right: Type) extends Type {
  override def asKotlin: String = {
    val leftStr = left match {
      case StdType("Unit") => "()"
      case t: ProductType => t.asKotlin
      case t => s"(${t.asKotlin})"
    }
    s"$leftStr -> ${right.asKotlin}"
  }

  override def isFunction: Boolean = true
}

case class GenericType(baseType: Type, parameters: Seq[Type]) extends Type {
  override def asKotlin: String =
    parameters.map(_.asKotlin).mkString(baseType.asKotlin + "<", ", ", ">")
}

case class NullableType(inner: Type) extends Type {
  override def asKotlin: String =
    inner.asKotlin + "?"
}

case class ProductType(types: Seq[Type]) extends Type {
  override def asKotlin: String =
    types.map(_.asKotlin).mkString("(", ", ", ")")
}

case class SimpleType(name: String) extends Type {
  override def asKotlin: String = name
}

case class ClassType(name: String) extends Type {
  override def asKotlin: String = name
}

case class StdType(name: String) extends Type {
  override def asKotlin: String = name
}

case class ScalaType(name: String) extends Type {
  override def asKotlin: String = name
}

case class JavaType(name: String) extends Type {
  override def asKotlin: String = name
}

case class KotlinType(name: String) extends Type {
  override def asKotlin: String = name
}

case class TypeParamType(typeParam: TypeParam) extends Type {
  override def asKotlin: String = typeParam.name
}

case object NoType extends Type {
  override def asKotlin: String = "Any"
}

case class ErrorType(text: String) extends Type with ErrorAst {
  override def asKotlin: String = ""
}

case class TypeParam(name: String, variance: TypeParamVariance, upperBound: Option[Type], lowerBound: Option[Type])
  extends AST

sealed trait TypeParamVariance {
  def kotlinKeyword: String

  def isInvariant: Boolean = false
}

case object InvariantTypeParam extends TypeParamVariance {
  override def kotlinKeyword: String = ""

  override def isInvariant: Boolean = true
}

case object CovariantTypeParam extends TypeParamVariance {
  override def kotlinKeyword: String = "out"
}

case object ContravariantTypeParam extends TypeParamVariance {
  override def kotlinKeyword: String = "in"
}
