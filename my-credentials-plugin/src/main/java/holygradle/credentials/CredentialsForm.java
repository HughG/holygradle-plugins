package holygradle.credentials;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.Timer;

public class CredentialsForm {
    public static Credentials getCredentialsFromUser(
        String frameTitle, Collection<String> instructions, String initialUserName
    ) {
        return getCredentialsFromUser(frameTitle, instructions, initialUserName, -1);
    }
    
    public static Credentials getCredentialsFromUser(
        String frameTitle, Collection<String> instructions, String initialUserName, int timeoutSeconds
    ) {
        Timer hideDialogTimer = new Timer();
        final JDialog frame = new JDialog();
        
        CancelTimerMouseListener cancelTimerMouseListener = new CancelTimerMouseListener(hideDialogTimer);
        HideWindowActionListener okActionListener = new HideWindowActionListener(frame);
        HideWindowActionListener cancelActionListener = new HideWindowActionListener(frame);
        
        frame.setTitle(frameTitle);
        Container pane = frame.getContentPane();

        frame.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;

        int yPos = 0;
        if (instructions != null) {
            for (String instruction : instructions) {
                c.gridwidth = 2;
                c.weightx = 1;
                c.gridx = 0;
                c.gridy = yPos++;
                c.ipadx = 10;
                pane.add( new JLabel(instruction), c);
            }
        }
        
        c.gridwidth = 1;
        c.weightx = 0.4;
        c.gridx = 0;
        c.gridy = yPos;
        c.ipadx = 10;
        pane.add( new JLabel("User name:"), c);

        JTextField userNameTextField = new JTextField(initialUserName);
        c.weightx = 0.6;
        c.gridx = 1;
        c.gridy = yPos++;
        c.ipadx = 250;
        userNameTextField.addMouseListener(cancelTimerMouseListener);
        pane.add(userNameTextField, c);

        c.weightx = 0.4;
        c.gridx = 0;
        c.gridy = yPos;
        c.ipadx = 10;
        pane.add( new JLabel("Password:"), c);

        final JPasswordField passwordField = new JPasswordField();
        c.weightx = 0.6;
        c.gridx = 1;
        c.gridy = yPos++;
        c.ipadx = 250;
        pane.add(passwordField, c);
        passwordField.addMouseListener(cancelTimerMouseListener);
        
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = yPos;
        c.ipadx = 50;
        c.anchor = GridBagConstraints.EAST;
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setPreferredSize(new Dimension(60, 20));
        cancelButton.addActionListener(cancelActionListener);   
        pane.add(cancelButton, c);
        
        c.weightx = 0.5;
        c.gridx = 1;
        c.gridy = yPos++;
        c.ipadx = 50;
        c.anchor = GridBagConstraints.WEST;
        JButton okButton = new JButton("OK");
        okButton.setPreferredSize(new Dimension(60, 20));
        okButton.addActionListener(okActionListener);     
        pane.add(okButton, c);
        
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e){
                passwordField.requestFocus();
            }
        }); 
        frame.setMinimumSize(new Dimension(200, 100));
        frame.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        frame.setLocationByPlatform(true);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.pack();
        frame.setAlwaysOnTop(true);
        
        HideDialogTimerTask timeoutTask = new HideDialogTimerTask(frame);
        if (timeoutSeconds > 0) {
            hideDialogTimer.schedule(timeoutTask, 1000 * timeoutSeconds);
        }
        
        frame.setVisible(true);
        hideDialogTimer.cancel();

        if (timeoutTask.didFire()) {
            throw new RuntimeException(
                "Timeout occurred while displaying a credentials dialog to the user. " +
                "If you are running Gradle directly, try re-running with the '--no-daemon' argument " +
                "until credentials are cached correctly. " +
                "(Now throwing an exception to kill the Gradle process because if this happens " +
                "to be an automated build then we don't want it to hang forever.)"
            );
        }
        
        if (okActionListener.didFire()) {
            return new Credentials(
                userNameTextField.getText(),
                new String(passwordField.getPassword())
            );
        }
        return null;
    }
}
