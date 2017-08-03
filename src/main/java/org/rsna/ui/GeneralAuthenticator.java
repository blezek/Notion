/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 * An Authenticator that provides a JDialog
 * requesting a username and password.
 */
public class GeneralAuthenticator extends Authenticator implements ActionListener  {

	private JFrame parent;
	private JDialog dialog;
	private JButton okButton;
	private JButton quitButton;
	private JTextField username;
	private JPasswordField password;
	static final String ok = "ok";

	/**
	 * Class constructor; creates an instance of the GeneralAuthenticator
	 * and remembers the parent JFrame.
	 * @param parent the JFrame that is the parent of the Authenticator.
	 */
	public GeneralAuthenticator(JFrame parent) {
		this.parent = parent;
	}

	/**
	 * Get the PasswordAuthentication for this challenge;
	 * overrides the method in the Authenticator class.
	 */
	protected PasswordAuthentication getPasswordAuthentication() {
		dialog = new JDialog (parent, "Username and Password Required", true);

		// Make the UI Components.
		JLabel prompt = new JLabel ("Enter Username and Password for: " + getRequestingPrompt());
		JLabel usernameLabel = new JLabel("Username: ");
		username = new JTextField(20);
		username.setActionCommand(ok);
		username.addActionListener(this);
		JLabel passwordLabel = new JLabel("Password: ");
		password = new JPasswordField(20);
		password.setActionCommand(ok);
		password.addActionListener(this);
		password.setEchoChar('*');
		okButton = new JButton(" OK ");
		okButton.addActionListener(this);
		quitButton = new JButton("Quit");
		quitButton.addActionListener(this);

		// Make panels on which to put them.
		JPanel promptPanel = new JPanel(new BorderLayout());
		promptPanel.setBorder(BorderFactory.createEmptyBorder(5,0,10,0));
		promptPanel.add(prompt,BorderLayout.NORTH);

		JPanel userPanel = new JPanel(new GridLayout(2,1));
		userPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		JPanel p1 = new JPanel();
		p1.add(usernameLabel);
		p1.add(username);
		JPanel p2 = new JPanel();
		p2.add(passwordLabel);
		p2.add(password);
		userPanel.add(p1);
		userPanel.add(p2);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(5,0,0,0));
		buttonPanel.add(okButton);
		buttonPanel.add(quitButton);

		JPanel mainPanel = new JPanel(new BorderLayout());
		mainPanel.setBorder(BorderFactory.createEmptyBorder(5,5,0,5));
		mainPanel.add(promptPanel,BorderLayout.NORTH);
		mainPanel.add(userPanel,BorderLayout.CENTER);
		mainPanel.add(buttonPanel,BorderLayout.SOUTH);

		// Put them in the dialog.
		dialog.getContentPane().add(mainPanel);
		dialog.pack();
		dialog.setLocationRelativeTo(parent);
		dialog.setResizable(false);
		dialog.setVisible(true);

		//And return the result.
		return new PasswordAuthentication (username.getText(), password.getPassword());
	}

	/**
	 * The ActionListener implementation for the buttons.
	 */
	public void actionPerformed (ActionEvent e) {
		Object object = e.getSource();
		if (object == okButton) {
			dialog.dispose();
			dialog = null;
		}
		else if (object == quitButton) {
			System.exit(0);
		}
		else if (((object == username) || (object == password)) &&
				 e.getActionCommand().equals(ok)) {
			dialog.dispose();
			dialog = null;
		}
	}

}
