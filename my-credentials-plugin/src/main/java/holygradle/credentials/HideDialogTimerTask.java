package holygradle.credentials;

import javax.swing.*;
import java.util.TimerTask;

class HideDialogTimerTask extends TimerTask {
    private final JDialog m_dialog;
    private boolean fired = false;
    
    public HideDialogTimerTask(JDialog dialog) {
        m_dialog = dialog;
    }
    
    public void run() {
        fired = true;
        m_dialog.setVisible(false);
    }
    
    public boolean didFire() {
        return fired;
    }
}
      