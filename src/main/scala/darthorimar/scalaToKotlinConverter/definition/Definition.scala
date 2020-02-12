package darthorimar.scalaToKotlinConverter.definition

trait Definition {
  def name: String

  def dependencies: Seq[Definition] = Seq.empty
}

object Definition {
  val unzip3: FileDefinition = FileDefinition("unzip3")
  val matchError: FileDefinition = FileDefinition("MatchError")
  val tryDefinition: FileDefinition = FileDefinition("Try", Seq(matchError), Seq("runTry", "Failure", "Success"))
  val eitherDefinition: FileDefinition = FileDefinition("Either", Seq.empty, Seq("Right", "Left"))
  val collect: FileDefinition = FileDefinition("collect", Seq(matchError))
  val partialFunction: FileDefinition = FileDefinition("PartialFunction", Seq(matchError))
  val stripSuffix: FileDefinition = FileDefinition("stripSuffix", Seq(matchError))

  def tuple(arity: Int) = new TupleDefinition(arity)
}
