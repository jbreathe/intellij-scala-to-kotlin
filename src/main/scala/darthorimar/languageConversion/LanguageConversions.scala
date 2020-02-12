package darthorimar.languageConversion

import com.intellij.notification.{NotificationDisplayType, NotificationType}
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.util.application.ApplicationUtilsKt
import org.jetbrains.plugins.scala.util.NotificationUtil

object LanguageConversions {

  implicit class LanguageConversion(converter: LanguageConverterExtension[_, _]) {
    def runConverterCommand[T](project: Project, command: () => T): T = {
      ApplicationUtilsKt.executeWriteCommand(
        project, s"Convert file from ${converter.languageFrom.getDisplayName} to ${converter.languageTo.getDisplayName}", null,
        () => {
          CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
          command()
        }
      )
    }
  }

  def showError(message: String, project: Project): Unit = {
    NotificationUtil.builder(project, message)
      .setDisplayType(NotificationDisplayType.BALLOON)
      .setNotificationType(NotificationType.WARNING)
      .setGroup("language.converter")
      .setTitle("Error while converting code").show()
  }

}
