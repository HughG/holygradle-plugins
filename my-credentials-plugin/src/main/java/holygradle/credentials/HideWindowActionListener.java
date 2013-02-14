package holygradle.credentials;

import java.awt.*;
import java.awt.event.*;
 
class HideWindowActionListener implements ActionListener {
    private final Window window;
    private boolean fired = false;
    
    public HideWindowActionListener(Window window) {
        this.window = window;
    }
    
    public void actionPerformed(ActionEvent e) {
        fired = true;
        window.setVisible(false);
    }
    
    public boolean didFire() {
        return fired;
    }
}