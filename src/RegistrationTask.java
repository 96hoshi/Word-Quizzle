
/**
 * @author Marta Lo Cascio
 * @matricola 532686
 * @project RCL - Word Quizzle
 */

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;

// Implementation of the interface for the remote method invocation.
// Checks if the username is not null, not empty and not more then 32 chars.
// Checks if the password is not null, not empty and not more then 256 chars.
// Registers a user if and only if the username is not already present in the database.
class RegistrationTask extends RemoteObject implements RegistrationRemote {

	private static final long serialVersionUID = 1L;

	private final int MAX_NICK = 32;
	private final int MAX_PSW = 256;

	private WQDatabase database;

	public RegistrationTask(WQDatabase database) throws RemoteException {
		super();
		this.database = database;
	}

	@Override
	public String registerUser(String nickUser, String password) throws RemoteException {
		// Check if username is not null, not empty and of limited length
		if (nickUser == null || nickUser.trim().isEmpty() || nickUser.length() > MAX_NICK) {
			return "Error: Invalid Username";
		}
		// Same check for password field
		if (password == null || password.trim().isEmpty() || password.length() > MAX_PSW) {
			return "Error: Invalid Password";
		}
		// In the end check if the user is already registered
		if (!database.addUser(nickUser, password)) {
			return "Error: Username already taken";
		}
		// If passed previous checks, the user is now registered to the service!
		return "Registration completed!";
	}

}
