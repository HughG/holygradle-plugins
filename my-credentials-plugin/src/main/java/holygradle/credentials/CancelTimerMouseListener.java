package holygradle.credentials;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.AbstractMap;
import java.util.Timer;
 
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