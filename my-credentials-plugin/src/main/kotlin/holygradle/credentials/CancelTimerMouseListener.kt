package holygradle.credentials

import java.awt.event.*
import java.util.*
 
class CancelTimerMouseListener(val timer: Timer) : MouseListener {
    override fun mouseClicked(e: MouseEvent) {
        timer.cancel()
    }

    override fun mouseReleased(e: MouseEvent?) {
    }

    override fun mouseEntered(e: MouseEvent?) {
    }

    override fun mouseExited(e: MouseEvent?) {
    }

    override fun mousePressed(e: MouseEvent?) {
    }
}