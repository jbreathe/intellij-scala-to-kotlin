package darthorimar.languageConversion

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.kotlin.idea.refactoring.DataContextUtilsKt
import org.jetbrains.kotlin.idea.util.application.ApplicationUtilsKt
import org.jetbrains.kotlin.psi.psiUtil.PsiUtilsKt

import scala.collection.JavaConverters._

object IdeaKotlinUtils {

  implicit class PsiFileExtension(file: PsiFile) {
    def elementsInRange(range: TextRange): List[PsiElement] = {
      val elementsInRange = PsiUtilsKt.elementsInRange(file, range)
      elementsInRange.asScala.toList
    }
  }

  implicit class PsiElementExtension(element: PsiElement) {
    def getStartOffset: Int = PsiUtilsKt.getStartOffset(element)

    def getEndOffset: Int = PsiUtilsKt.getEndOffset(element)
  }

  implicit class DataContextUtils(dataContext: DataContext) {
    def getProject: Project = DataContextUtilsKt.getProject(dataContext)
  }

  def runReadAction[T](action: () => T): T = {
    ApplicationUtilsKt.runReadAction(() => action())
  }

}
