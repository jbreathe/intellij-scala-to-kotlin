package darthorimar.scalaToKotlinConverter.types

import darthorimar.scalaToKotlinConverter.ast._

object LibTypes {
  def tupleType(arity: Int): ClassType = ClassType(s"Tuple$arity")
}
