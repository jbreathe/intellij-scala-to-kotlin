package darthorimar.scalaToKotlinConverter.step

import darthorimar.scalaToKotlinConverter.ast.Import
import darthorimar.scalaToKotlinConverter.definition.{Definition, DefinitionGenerator, FileDefinition}

class ConverterStepState {
  private var definitions: Set[Definition] = Set.empty
  private var imports: Set[Import] = Set.empty

  def addDefinition(definition: Definition): Unit = {
    val definitionNames = definition match {
      case fileDefinition: FileDefinition =>
        fileDefinition.usedDefinitions
      case _ => Seq(definition.name)
    }
    definitionNames foreach { definitionName =>
      imports += Import(DefinitionGenerator.packageName + "." + definitionName)
    }
    definitions += definition
  }

  def addImport(imp: Import): Unit =
    imports += imp

  def collectedDefinitions: Seq[Definition] =
    definitions.toSeq

  def collectImports: Seq[Import] =
    imports.toSeq
}
