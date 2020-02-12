package darthorimar.languageConversion

import com.intellij.ide.scratch.{ScratchFileService, ScratchRootType}
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiFile, PsiManager}
import darthorimar.languageConversion.IdeaKotlinUtils._
import darthorimar.languageConversion.LanguageConversions._

class ConvertFileAction[InternalRepresentation, ConverterState](private val converter: LanguageConverterExtension[InternalRepresentation, ConverterState])
  extends AnAction(s"Convert ${converter.languageFrom.getDisplayName} to ${converter.languageTo.getDisplayName}") {

  override def update(e: AnActionEvent): Unit = {
    val presentation: Presentation = e.getPresentation

    def enable(): Unit = {
      presentation.setEnabled(true)
      presentation.setVisible(true)
    }

    def disable(): Unit = {
      presentation.setEnabled(false)
      presentation.setVisible(false)
    }

    try {
      val selectedFiles = getSelectedFiles(e.getDataContext)
      if (selectedFiles == null || selectedFiles.isEmpty) disable() else enable()
    } catch {
      case _: Exception => disable()
    }
  }

  override def actionPerformed(e: AnActionEvent): Unit = {
    val project = e.getProject
    if (project == null) return
    val selectedFiles: List[PsiFile] = getSelectedFiles(e.getDataContext)
    if (selectedFiles == null) return
    for (file <- selectedFiles) {
      val newFile = convertFile(file, project)
      if (newFile == null) showError(s"Can not convert file ${file.getName}", project)
    }
  }

  private def getSelectedFiles(dataContext: DataContext): List[PsiFile] = {
    def suitableFile(file: PsiFile) =
      file.isWritable && file.getLanguage == converter.languageFrom

    val data = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)
    if (data != null) {
      val files = data
        .filter(f => f != null)
        .map(f => PsiManager.getInstance(dataContext.getProject).findFile(f))
      if (files != null) {
        val selectedFiles = files.filter(f => suitableFile(f))
        if (selectedFiles != null && selectedFiles.nonEmpty) return selectedFiles.toList
      }
    }
    val inEditorFile = CommonDataKeys.PSI_FILE.getData(dataContext)
    if (inEditorFile != null && suitableFile(inEditorFile)) {
      return List(inEditorFile)
    }
    null
  }

  private def convertFile(file: PsiFile, project: Project): PsiFile = {
    converter.runConverterCommand(project, () => {
      val internalRepresentation = converter.convertPsiElementToInternalRepresentation(file)
      if (internalRepresentation == null) return null
      val text = converter.convertInternalRepresentationToText(internalRepresentation._1, internalRepresentation._2, project)
      if (text == null) return null
      val newFile = replaceFileContent(text._1, file, project)
      if (newFile == null) return null
      converter.runPostProcessOperations(newFile, text._2)
      newFile
    })
  }

  private def replaceFileContent(newText: String, file: PsiFile, project: Project): PsiFile = {
    val document = PsiDocumentManager.getInstance(project).getDocument(file)
    if (document == null) return null
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
    document.replaceString(0, document.getTextLength, newText)
    PsiDocumentManager.getInstance(project).commitDocument(document)
    FileDocumentManager.getInstance().saveDocument(document)
    val virtualFile = file.getVirtualFile
    if (ScratchRootType.getInstance().containsFile(virtualFile)) {
      val mapping = ScratchFileService.getInstance().getScratchesMapping
      mapping.setMapping(virtualFile, converter.languageTo)
    } else {
      val fileNameWithoutExtension =
        file.getName.stripSuffix(converter.languageFrom.getAssociatedFileType.getDefaultExtension) // was "removeSuffix"
      val newFileName = s"$fileNameWithoutExtension${converter.languageTo.getAssociatedFileType.getDefaultExtension}"
      virtualFile.rename(this, newFileName)
    }
    val newDocument = PsiDocumentManager.getInstance(project).getDocument(file)
    if (newDocument == null) return null
    PsiDocumentManager.getInstance(project).commitDocument(newDocument)
    PsiManager.getInstance(project).findFile(virtualFile)
  }

}

object ConvertFileAction {
  val ACTION_PREFIX = "ConvertLanguageAction"
}
