package darthorimar.languageConversion

import com.intellij.openapi.actionSystem.{ActionManager, DefaultActionGroup}
import com.intellij.openapi.components.ApplicationComponent

class LanguageConverterApplicationComponent extends ApplicationComponent {
  private val actionGroupNames: Set[String] = Set("RefactoringMenu", "EditorTabPopupMenu", "ProjectViewPopupMenu")

  override def initComponent(): Unit = {
    val converters = LanguageConverterExtension.EP_NAME.getExtensions
    for (converter <- converters) {
      registerConvertAction(converter)
    }
  }

  private def registerConvertAction(converter: LanguageConverterExtension[Any, Any]): Unit = {
    val actionManager = ActionManager.getInstance()
    val converterAction = new ConvertFileAction(converter)
    val actionId = s"${ConvertFileAction.ACTION_PREFIX}.${converter.languageFrom.getDisplayName.capitalize}To${converter.languageTo.getDisplayName.capitalize}"
    actionManager.registerAction(actionId, converterAction)

    def addToGroup(groupName: String) {
      val refactorActionGroup = actionManager.getAction(groupName).asInstanceOf[DefaultActionGroup]
      refactorActionGroup.add(converterAction)
    }

    for (actionGroupName <- actionGroupNames) {
      addToGroup(actionGroupName)
    }
  }

  override def getComponentName: String = "LanguageConverterApplicationComponent"
}
