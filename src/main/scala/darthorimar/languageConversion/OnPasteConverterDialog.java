package darthorimar.languageConversion;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class OnPasteConverterDialog extends DialogWrapper {
    private JPanel panel;
    private JButton buttonOK;
    private JLabel textBox;

    protected OnPasteConverterDialog(@NotNull LanguageConverterExtension<?, ?> converter, @Nullable final Project project) {
        super(project, true);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        setTitle(String.format("Convert %s to %s", converter.languageFrom().getDisplayName(), converter.languageTo().getDisplayName()));
        if (textBox != null) {
            textBox.setText(String.format("Content copied from %s. Do you want to convert it to %s code?", converter.languageFrom().getDisplayName(), converter.languageTo().getDisplayName()));
        }
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return panel;
    }
}
