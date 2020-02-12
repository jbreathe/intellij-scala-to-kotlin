package darthorimar.languageConversion

import com.intellij.lang.Language
import com.intellij.openapi.extensions.{AbstractExtensionPointBean, ExtensionPointName}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
 * Add support of converting one language to another.
 *
 * Add actions to menus which will convert selected or (opened files) from [languageFrom] to [languageTo]
 *
 * Add converting on copy-past from file with language [languageFrom] to one with [languageTo]
 *
 * Perform pos-processing after every conversion by [runPostProcessOperations]
 *
 * [InternalRepresentation] stores data which will be collected by [convertPsiElementToInternalRepresentation] method
 * while copying code
 * [ConverterState] a internal state of converter which may be used to pass information
 * during conversion steps
 *
 * @param languageFrom language to convert from
 * @param languageTo   language to convert to
 */
abstract case class LanguageConverterExtension[InternalRepresentation, ConverterState](languageFrom: Language, languageTo: Language) extends AbstractExtensionPointBean {

  /**
   * Converts given [element] to [InternalRepresentation]. Called when user performs copy operation
   * Called in dispatch thread and within a run action with alternative resolving enabled
   *
   * @param element PSI element to convert to [InternalRepresentation]
   * @return [Pair] which contains converted [InternalRepresentation] and collected [ConverterState] if succeed, null otherwise
   */
  def convertPsiElementToInternalRepresentation(element: PsiElement): (InternalRepresentation, ConverterState)

  /**
   * Convert given [representation] to code in [languageTo]. Called when user performs past operation
   * Called in write command
   *
   * @param representation [InternalRepresentation] which was collected by [convertPsiElementToInternalRepresentation]
   * @param state          [ConverterState] which was collected by [convertPsiElementToInternalRepresentation]
   * @param project        project in file of which converted code will be pasted in
   * @return [Pair] which contains converted code in [languageTo] and collected [ConverterState] if succeed, null otherwise
   */
  def convertInternalRepresentationToText(representation: InternalRepresentation, state: ConverterState, project: Project): (String, ConverterState)

  /**
   * Runs post-processing operations like adding imports to file, reformat code or running inspections on generated code
   *
   * @param element       PSI element which was converted. May be a [PsiFile] or arbitrary [PsiElement]
   * @param internalState [ConverterState] collected during conversion
   */
  def runPostProcessOperations(element: PsiElement, internalState: ConverterState)
}

object LanguageConverterExtension {
  val EP_NAME: ExtensionPointName[LanguageConverterExtension[Any, Any]] = ExtensionPointName.create[LanguageConverterExtension[Any, Any]]("com.intellij.languageConverter")
}
