import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;

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
		// check if username is not null, not empty and of limited length
		if (nickUser == null || nickUser.trim().isEmpty() || nickUser.length() > MAX_NICK) {
			return "Error: Invalid Username";
		}
		if (password == null || password.trim().isEmpty() || password.length() > MAX_PSW) {
			return "Error: Invalid Password";
		}
		if (!database.addUser(nickUser, password)) {
			return "Error: Username already taken";
		}
		return "Registration completed!";
	}

}
