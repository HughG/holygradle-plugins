package holygradle.credentials;

import java.awt.event.*;
import java.util.*;
 
class CancelTimerMouseListener implements MouseListener {
    private final Timer timer;

    public CancelTimerMouseListener(Timer timer) {
        this.timer = timer;
    }
    
    public void	mouseClicked(MouseEvent e) {
        timer.cancel();
    }
 
    public void mouseEntered(MouseEvent e) {
    }
    
    public void mouseExited(MouseEvent e) {
    }
    
    public void mousePressed(MouseEvent e) {
    }
    
    public void mouseReleased(MouseEvent e) {
    }
}