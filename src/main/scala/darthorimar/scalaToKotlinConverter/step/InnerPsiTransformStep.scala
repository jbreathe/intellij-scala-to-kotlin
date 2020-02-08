package darthorimar.scalaToKotlinConverter.step

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import darthorimar.scalaToKotlinConverter.ImplicitTransform
import darthorimar.scalaToKotlinConverter.step.ConverterStep.Notifier
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.transformation.Transformer

class InnerPsiTransformStep extends ConverterStep[ScalaPsiElement, ScalaPsiElement] {
  override def name: String = "Preparing code for translation"

  private val transformers: Set[Transformer] = Set(
    new ImplicitTransform()
  )

  override def apply(from: ScalaPsiElement,
                     state: ConverterStepState,
                     index: Int,
                     notifier: Notifier): (ScalaPsiElement, ConverterStepState) = {
    notifier.notify(this, index)
    val result = inWriteAction {
      val copy = from.copy()
      val file = copy.getContainingFile
      Transformer.applyTransformersAndReformat(copy, file, None, transformers, null)
      copy.asInstanceOf[ScalaPsiElement]
    }
    (result, state)
  }

  def noOp(list: List[TextRange], file: PsiFile, doc: Document): Unit = {}
}
