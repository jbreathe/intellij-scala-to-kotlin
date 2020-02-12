package darthorimar.scalaToKotlinConverter.types

import darthorimar.scalaToKotlinConverter.ast.KotlinType

object KotlinTypes {
  val THROWABLE: KotlinType = KotlinType("Throwable")
  val EXCEPTION: KotlinType = KotlinType("Exception")
  val LIST: KotlinType = KotlinType("List")
  val PAIR: KotlinType = KotlinType("Pair")
  val ARRAY: KotlinType = KotlinType("Array")
}
