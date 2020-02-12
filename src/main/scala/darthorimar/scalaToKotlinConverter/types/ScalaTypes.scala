package darthorimar.scalaToKotlinConverter.types

import darthorimar.scalaToKotlinConverter.ast._

object ScalaTypes {
  val OPTION: ScalaType = ScalaType("scala.Option")
  val SOME: ScalaType = ScalaType("scala.Some")
  val NONE: ScalaType = ScalaType("scala.None$")
  val STRING: SimpleType = SimpleType("scala.Predef.String")
  val JAVA_STRING: JavaType = JavaType("java.lang.String")
  val COLLECTION_SEQ: ScalaType = ScalaType("scala.collection.Seq")
  val SEQ: ScalaType = ScalaType("scala.Seq")
  val COLLECTION_LIST: ScalaType = ScalaType("scala.collection.List")
  val COLLECTION_IMMUTABLE_LIST: ScalaType = ScalaType("scala.collection.immutable.List")
  val LIST: ScalaType = ScalaType("scala.List")

  val FUNCTION_PREFIX = "_root_.scala.Function"
}
