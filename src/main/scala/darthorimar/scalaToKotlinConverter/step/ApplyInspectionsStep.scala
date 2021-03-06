package darthorimar.scalaToKotlinConverter.step

import java.util

import com.intellij.openapi.components.ServiceManager
import darthorimar.scalaToKotlinConverter.inspection.Inspection
import darthorimar.scalaToKotlinConverter.step.ConverterStep.Notifier
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.psi._
import org.jetbrains.plugins.scala.extensions._

import scala.util.Try

class ApplyInspectionsStep extends ConverterStep[KtElement, KtElement] {
  override def name: String = "Fixing code after conversion"

  override def apply(from: KtElement,
                     state: ConverterStepState,
                     index: Int,
                     notifier: Notifier): (KtElement, ConverterStepState) = {
    val project = from.getProject
    val file = from.getContainingFile.asInstanceOf[KtFile]
    notifier.notify(this, index)
    var succeedFixes: Int = 0
    do {
      val diagnostics = inReadAction {
        ServiceManager
          .getService(project, classOf[KotlinCacheService])
          .getResolutionFacade(util.Collections.singletonList(from))
          .analyzeWithAllCompilerChecks(util.Collections.singletonList(from))
          .getBindingContext
          .getDiagnostics
      }
      val fixes = from depthFirst() flatMap {
        case element: KtElement =>
          Inspection.inspections.flatMap(_.createAction(element, project, file, diagnostics))
        case _ => List.empty
      } toList

      succeedFixes = fixes flatMap { f =>
        inWriteAction {
          Try {
            f()
          } toOption
        }
      } length
    } while (succeedFixes != 0)

    (from, state)
  }
}
