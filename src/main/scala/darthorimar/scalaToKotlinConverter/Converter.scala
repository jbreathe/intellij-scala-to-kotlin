package darthorimar.scalaToKotlinConverter

import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Computable, ThrowableComputable}
import darthorimar.scalaToKotlinConverter.ast.AST
import darthorimar.scalaToKotlinConverter.step.ConverterStep.{Notifier, Result}
import darthorimar.scalaToKotlinConverter.step._
import darthorimar.scalaToKotlinConverter.step.transform._
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

class PostProcessOperationConverter(protect: Project) extends Converter[KtElement, KtElement](protect) {
  override def convert(from: KtElement, state: ConverterStepState): Result[KtElement] = {
    val converter: ConverterStep[KtElement, KtElement] =
      wrapped(withProgress(background = false),
        new FormatFileAndGenerateImportsAndDefinitionsStep -->
          new ApplyInspectionsStep)
    converter(from, state, 0, notifier(stepsCount = 2, s"Converting file ${from.getContainingFile.getName}"))
  }
}

class ScalaPsiToAstConverter(protect: Project) extends Converter[ScalaPsiElement, AST](protect) {
  override def convert(from: ScalaPsiElement, state: ConverterStepState): Result[AST] = {
    val converter: ConverterStep[ScalaPsiElement, AST] =
      new InnerPsiTransformStep -->
        new ASTGenerationStep
    converter(from, state, 0, Notifier.empty)
  }
}

class AstToTextConverter(protect: Project) extends Converter[AST, String](protect) {
  override def convert(from: AST, state: ConverterStepState): Result[String] = {
    val converter: ConverterStep[AST, String] =
      wrapped[AST, String](
        withProgress(background = true),
        new TypeTransform -->
          new BasicTransform -->
          new CollectionTransform -->
          new TypeTransform -->
          new DefinitionCollectorTransform -->
          new CollectImportsStep -->
          new PrintStringStep
      )
    converter(from, state, 0, notifier(stepsCount = 10, "Converting copied Scala code"))
  }
}

abstract class Converter[From, To](project: Project) {

  val title = "Converting Scala to Kotlin"

  def convert(from: From, state: ConverterStepState): Result[To]

  def notifier(stepsCount: Int, caption: String): Notifier = (step: ConverterStep[_, _], index: Int) => {
    Option(ProgressManager.getInstance().getProgressIndicator) foreach { indicator =>
      indicator.setFraction(index.toDouble / stepsCount)
      indicator.setText(caption)
      indicator.setText2(s"${step.name} $index/$stepsCount")
    }
  }

  def wrapped[From1, To1](wrapper: (=> Result[To1]) => Result[To1],
                          step: ConverterStep[From1, To1]): ConverterStep[From1, To1] = new ConverterStep[From1, To1] {
    override def apply(from: From1,
                       state: ConverterStepState,
                       index: Int,
                       notifier: Notifier): (To1, ConverterStepState) = {
      wrapper(step(from, state, index, notifier))
    }

    override def name: String = step.name
  }

  def withProgress[T](background: Boolean)(data: => T): T = {
    val convert: ThrowableComputable[T, Exception] = () =>
      ProgressManager
        .getInstance()
        .runProcess((() => data): Computable[T], ProgressManager.getInstance().getProgressIndicator)

    if (background)
      ProgressManager
        .getInstance()
        .runProcessWithProgressSynchronously(convert, title, true, project)
    else {
      var result: T = null.asInstanceOf[T]
      val task = new Task.Modal(project, title, false) {
        override def run(indicator: ProgressIndicator): Unit =
          result = convert.compute()
      }
      ProgressManager.getInstance().run(task)
      result
    }
  }
}
