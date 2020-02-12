package darthorimar.scalaToKotlinConverter.definition

import darthorimar.scalaToKotlinConverter.BuilderBase

trait TextDefinition extends Definition with BuilderBase {
  def get: String = {
    generate()
    text
  }

  protected def generate(): Unit
}
