package it.acsys.aria2wrapper;

import com.siemens.pse.umsso.client.UmssoUserCredentials;
import com.siemens.pse.umsso.client.UmssoVisualizerCallback;


public class CommandLineCallback implements UmssoVisualizerCallback {
	private static UmssoUserCredentials userCredentials; 
	private int userAttempts = 0;
	private static CommandLineCallback commandLineCallback;
	
	
	public static CommandLineCallback getInstance(String username, char[] pwd) {

		if(commandLineCallback == null) {

			commandLineCallback = new CommandLineCallback(username, pwd);
		}

	    return commandLineCallback;

	}
	
	public CommandLineCallback(String username, char[] pwd) {
		userCredentials = new UmssoUserCredentials(username, pwd);
	}

	public UmssoUserCredentials showLoginForm(String message, String spResourceUrl, String idpUrl) {
		userAttempts++;
		if(userAttempts >= 2) {
			return null;
		}
		return userCredentials;
	}
}
