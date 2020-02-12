package darthorimar.scalaToKotlinConverter

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import darthorimar.languageConversion.LanguageConverterExtension
import darthorimar.scalaToKotlinConverter.ast.AST
import darthorimar.scalaToKotlinConverter.step.ConverterStepState
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

class ScalaToKotlinLanguageConverter extends LanguageConverterExtension[AST, ConverterStepState](ScalaLanguage.INSTANCE, KotlinLanguage.INSTANCE) {

  override def runPostProcessOperations(element: PsiElement, internalState: ConverterStepState): Unit =
    element match {
      case ktElement: KtElement =>
        new PostProcessOperationConverter(element.getProject).convert(ktElement, internalState)
      case _ =>
    }

  override def convertInternalRepresentationToText(representation: AST,
                                                   state: ConverterStepState,
                                                   project: Project): (String, ConverterStepState) = {
    new AstToTextConverter(project).convert(representation, state)
  }

  override def convertPsiElementToInternalRepresentation(element: PsiElement): (AST, ConverterStepState) =
    element match {
      case scalaElement: ScalaPsiElement =>
        new ScalaPsiToAstConverter(element.getProject).convert(scalaElement, new ConverterStepState)
      case _ => null
    }

}
