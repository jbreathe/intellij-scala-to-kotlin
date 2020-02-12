package darthorimar.scalaToKotlinConverter.inspection

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.diagnostics.{Diagnostic, DiagnosticWithParameters2, Errors}
import org.jetbrains.kotlin.idea.inspections.MoveLambdaOutsideParenthesesInspection
import org.jetbrains.kotlin.idea.intentions.{RemoveUnnecessaryParenthesesIntention, UsePropertyAccessSyntaxIntention}
import org.jetbrains.kotlin.idea.quickfix.{AddExclExclCallFix, ReplaceWithSafeCallFix}
import org.jetbrains.kotlin.psi._
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.{types => ktTypes}

import scala.collection.JavaConverters._

trait Inspection {
  def createAction(element: KtElement, project: Project, file: PsiFile, diagnostics: Diagnostics): Option[Fix]
}

case class Fix(fix: () => Unit) {
  def apply(): Unit = fix()
}

object Inspection {
  val inspections = Seq(
    new DiagnosticBasedInspection(
      Errors.TYPE_MISMATCH_ERRORS.asScala.toSeq, {
        (element: KtElement, diagnostic: Diagnostic, project: Project, file: PsiFile) => {
          val fix = new AddExclExclCallFix(element)

          def isStillAvailable = diagnostic match {
            case d: DiagnosticWithParameters2[_, ktTypes.KotlinType, ktTypes.KotlinType]
              if TypeUtils.isNullableType(d.getA) != TypeUtils.isNullableType(d.getB) &&
                d.getA.unwrap.toString.stripSuffix("?") == d.getB.unwrap.toString.stripSuffix("?") =>
              fix.isAvailable(project, null, file)
            case _ => false
          }

          if (isStillAvailable) Some(() => if (isStillAvailable) fix.invoke(project, null, file))
          else None
        }
      }
    ),
    new DiagnosticBasedInspection(
      Seq(Errors.UNSAFE_CALL), { (element: KtElement, _: Diagnostic, project: Project, file: PsiFile) =>
        if (!element.isInstanceOf[KtDotQualifiedExpression]) None
        else {
          val fix = new ReplaceWithSafeCallFix(element.asInstanceOf[KtDotQualifiedExpression], true)

          def isStillAvailable =
            fix.isAvailable(project, null, file)

          if (isStillAvailable) Some(() => if (isStillAvailable) fix.invoke(project, null, file))
          else None
        }
      }
    ),
    new IntentionBaseInspection(new RemoveUnnecessaryParenthesesIntention, classOf[KtParenthesizedExpression]),
    new IntentionBaseInspection(new UsePropertyAccessSyntaxIntention, classOf[KtCallExpression]),
    new ApplicabilityBasedInspection(new MoveLambdaOutsideParenthesesInspection, classOf[KtCallExpression])
  )
}
