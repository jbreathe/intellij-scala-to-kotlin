package darthorimar.scalaToKotlinConverter.types

import darthorimar.scalaToKotlinConverter.ast.StdType

object StdTypes {
  val ANY: StdType = StdType("Any")
  val STRING: StdType = StdType("String")
  val ANY_REF: StdType = StdType("AnyRef")
  val NOTHING: StdType = StdType("Nothing")
  val ANY_VAL: StdType = StdType("AnyVal")
  val UNIT: StdType = StdType("Unit")
  val BOOLEAN: StdType = StdType("Boolean")
  val CHAR: StdType = StdType("Char")
  val BYTE: StdType = StdType("Byte")
  val SHORT: StdType = StdType("Short")
  val INT: StdType = StdType("Int")
  val LONG: StdType = StdType("Long")
  val FLOAT: StdType = StdType("Float")
  val DOUBLE: StdType = StdType("Double")

  val NUMERIC_TYPES = Seq(
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE
  )
}
