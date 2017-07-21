package holygradle.credentials

import java.awt.*
import java.awt.event.*
 
class HideWindowActionListener(private val window: Window) : ActionListener {
    var fired = false
        private set
    
    override fun actionPerformed(e: ActionEvent) {
        fired = true
        window.isVisible = false
    }
}