package holygradle.credentials

import javax.swing.*
import java.awt.*
import java.awt.event.*
import java.util.Timer

internal class CredentialsForm {
    companion object {
        fun getCredentialsFromUser(
                frameTitle: String, instructions: Collection<String>?, initialUserName: String, timeoutSeconds: Int
        ): Credentials? {
            val hideDialogTimer = Timer()
            val frame = JDialog()

            val cancelTimerMouseListener = CancelTimerMouseListener(hideDialogTimer)
            val okActionListener = HideWindowActionListener(frame)
            val cancelActionListener = HideWindowActionListener(frame)

            frame.title = frameTitle
            val pane = frame.contentPane

            frame.layout = GridBagLayout()
            val c = GridBagConstraints()
            c.fill = GridBagConstraints.HORIZONTAL

            var yPos = 0
            if (instructions != null) {
                for (instruction in instructions) {
                    c.gridwidth = 2
                    c.weightx = 1.0
                    c.gridx = 0
                    c.gridy = yPos++
                    c.ipadx = 10
                    pane.add(JLabel(instruction), c)
                }
            }

            c.gridwidth = 1
            c.weightx = 0.4
            c.gridx = 0
            c.gridy = yPos
            c.ipadx = 10
            pane.add(JLabel("User name:"), c)

            val userNameTextField = JTextField(initialUserName)
            c.weightx = 0.6
            c.gridx = 1
            c.gridy = yPos++
            c.ipadx = 250
            userNameTextField.addMouseListener(cancelTimerMouseListener)
            pane.add(userNameTextField, c)

            c.weightx = 0.4
            c.gridx = 0
            c.gridy = yPos
            c.ipadx = 10
            pane.add(JLabel("Password:"), c)

            val passwordField = JPasswordField()
            c.weightx = 0.6
            c.gridx = 1
            c.gridy = yPos++
            c.ipadx = 250
            pane.add(passwordField, c)
            passwordField.addMouseListener(cancelTimerMouseListener)

            c.weightx = 0.5
            c.gridx = 0
            c.gridy = yPos
            c.ipadx = 50
            c.anchor = GridBagConstraints.EAST
            val cancelButton = JButton("Cancel")
            cancelButton.preferredSize = Dimension(60, 20)
            cancelButton.addActionListener(cancelActionListener)
            pane.add(cancelButton, c)

            c.weightx = 0.5
            c.gridx = 1
            c.gridy = yPos/*++*/
            c.ipadx = 50
            c.anchor = GridBagConstraints.WEST
            val okButton = JButton("OK")
            okButton.preferredSize = Dimension(60, 20)
            okButton.addActionListener(okActionListener)
            pane.add(okButton, c)

            frame.addWindowListener(object: WindowAdapter() {
                override fun windowOpened(e: WindowEvent){
                    passwordField.requestFocus()
                }
            })
            frame.minimumSize = Dimension(200, 100)
            frame.modalityType = Dialog.ModalityType.APPLICATION_MODAL
            frame.isLocationByPlatform = true
            frame.defaultCloseOperation = JFrame.HIDE_ON_CLOSE
            frame.pack()
            frame.isAlwaysOnTop = true

            val timeoutTask = HideDialogTimerTask(frame)
            if (timeoutSeconds > 0) {
                hideDialogTimer.schedule(timeoutTask, 1000L * timeoutSeconds)
            }

            frame.isVisible = true
            hideDialogTimer.cancel()

            if (timeoutTask.fired) {
                throw RuntimeException(
                        "Timeout occurred while displaying a credentials dialog to the user. " +
                                "If you are running Gradle directly, try re-running with the '--no-daemon' argument " +
                                "until credentials are cached correctly. " +
                                "(Now throwing an exception to kill the Gradle process because if this happens " +
                                "to be an automated build then we don't want it to hang forever.)"
                        )
            }

            if (okActionListener.fired) {
                return Credentials(userNameTextField.text, String(passwordField.password))
            }
            return null
        }
    }
}
