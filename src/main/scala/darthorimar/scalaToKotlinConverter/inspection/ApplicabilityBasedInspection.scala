package darthorimar.scalaToKotlinConverter.inspection

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics

class ApplicabilityBasedInspection[Elem <: KtElement](val inspection: AbstractApplicabilityBasedInspection[Elem],
                                                      elementType: Class[Elem])
  extends Inspection {
  override def createAction(element: KtElement,
                            project: Project,
                            file: PsiFile,
                            diagnostics: Diagnostics): Option[Fix] = {
    val elem = element.asInstanceOf[Elem]

    def isStillAvailable: Boolean =
      element.getClass == elementType && inspection.isApplicable(elem)

    if (isStillAvailable) Some(Fix(() => if (isStillAvailable) inspection.applyTo(elem, project, null)))
    else None
  }

}
