package darthorimar.languageConversion

import java.awt.datatransfer.{DataFlavor, Transferable}
import java.{lang, util}

import com.intellij.codeInsight.editorActions.{CopyPastePostProcessor, TextBlockTransferableData}
import com.intellij.lang.Language
import com.intellij.openapi.editor.{Editor, RangeMarker}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Ref, TextRange}
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import darthorimar.languageConversion.IdeaKotlinUtils._
import darthorimar.languageConversion.LanguageConversions._

import scala.collection.JavaConverters._

class ConvertOnCopyPastePostProcessor extends CopyPastePostProcessor[ConverterTransferableData] {
  override def collectTransferableData(file: PsiFile, editor: Editor, startOffsets: Array[Int], endOffsets: Array[Int]): util.List[ConverterTransferableData] = {
    if (file == null || startOffsets == null || endOffsets == null) return List().asJava
    if (startOffsets.length != endOffsets.length) return List().asJava
    val converters = LanguageConverterExtension.EP_NAME.getExtensionList.asScala
    converters.filter(c => c != null).map(c => {
      val data = startOffsets.zip(endOffsets).flatMap((p: (Int, Int)) => {
        val range = new TextRange(p._1, p._2)
        val elementsInRange = file.elementsInRange(range)
        elementsInRange.filter(e => e != null).map(e => convertElement(c, e))
      }).toList
      if (data.isEmpty) return null
      new ConverterTransferableData(c, data)
    }).asJava
  }

  override def extractTransferableData(content: Transferable): util.List[ConverterTransferableData] = {
    if (content == null) return List().asJava
    val converters = LanguageConverterExtension.EP_NAME.getExtensionList.asScala
    converters.filter(
      c => content.isDataFlavorSupported(ConverterData.dataFlavor(c.languageFrom))
    ).map(
      c => content.getTransferData(ConverterData.dataFlavor(c.languageFrom)).asInstanceOf[ConverterTransferableData]
    ).asJava
  }

  override def processTransferableData(project: Project, editor: Editor, bounds: RangeMarker, caretOffset: Int,
                                       indented: Ref[lang.Boolean], values: util.List[ConverterTransferableData]): Unit = {
    if (project == null || values == null || editor == null || bounds == null) return
    val document = editor.getDocument
    val transferableData = values.asScala.head // was "single"
    val converter = transferableData.converter
    val dialog = new OnPasteConverterDialog(converter, project)
    if (dialog.showAndGet()) {
      for (data <- transferableData.data) {
        val res = converter.runConverterCommand(project, () => {
          val text = converter.convertInternalRepresentationToText(data.internalRepresentation, data.state, project)
          if (text == null) return
          document.replaceString(bounds.getStartOffset, bounds.getEndOffset, text._1)
          PsiDocumentManager.getInstance(project).commitDocument(document)
          val generatedCodeTextRange = new TextRange(bounds.getStartOffset, bounds.getEndOffset)
          val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
          if (psiFile == null) return
          val generatedElements = psiFile.elementsInRange(generatedCodeTextRange)
          generatedElements.foreach(e => {
            converter.runPostProcessOperations(e, text._2)
          })
          psiFile
        })
        if (res == null) showError("Error while converting pasted code", project)
      }
    }
  }

  private def convertElement[InternalRepresentation, ConverterState](converter: LanguageConverterExtension[InternalRepresentation, ConverterState], element: PsiElement): ConverterData[InternalRepresentation, ConverterState] = {
    val result = runReadAction(
      () => converter.convertPsiElementToInternalRepresentation(element)
    )
    if (result == null) return null
    if (result._1 == null || result._2 == null) return null
    ConverterData(element.getStartOffset, element.getEndOffset, result._1, result._2)
  }
}

class ConverterTransferableData(val converter: LanguageConverterExtension[Any, Any],
                                val data: List[ConverterData[Any, Any]]) extends TextBlockTransferableData {
  override def getFlavor: DataFlavor = ConverterData.dataFlavor(converter.languageFrom)

  override def getOffsetCount: Int = data.size * 2

  override def getOffsets(offsets: Array[Int], index: Int): Int = {
    var i: Int = index
    for (d <- data) {
      offsets(i) = d.startOffset
      offsets(i + 1) = d.endOffset
      i += 2
    }
    i
  }

  override def setOffsets(offsets: Array[Int], index: Int): Int = {
    var i: Int = index
    for (d <- data) {
      d.startOffset = offsets(i)
      d.endOffset = offsets(i + 1)
      i += 2
    }
    i
  }
}

case class ConverterData[InternalRepresentation, ConverterState](var startOffset: Int,
                                                                 var endOffset: Int,
                                                                 internalRepresentation: InternalRepresentation,
                                                                 state: ConverterState)

object ConverterData {
  def dataFlavor(toLanguage: Language): DataFlavor = {
    try {
      val dataClass = getClass
      new DataFlavor(
        DataFlavor.javaJVMLocalObjectMimeType + ";class=" + dataClass.getName,
        s"Converter${toLanguage.getDisplayName.capitalize}ReferenceData", dataClass.getClassLoader)
    } catch {
      case _: Throwable => null
    }
  }
}
