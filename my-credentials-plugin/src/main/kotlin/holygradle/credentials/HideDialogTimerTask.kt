package holygradle.credentials

import javax.swing.*
import java.util.TimerTask

class HideDialogTimerTask(private val dialog: JDialog) : TimerTask() {
    var fired = false
        private set
    
    override fun run() {
        fired = true
        dialog.isVisible = false
    }
}
