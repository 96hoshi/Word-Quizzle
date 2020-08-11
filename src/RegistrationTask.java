import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;

class RegistrationTask extends RemoteObject implements RegistrationRemote {

	private static final long serialVersionUID = 1L;

	private final int MAX_NICK = 32;
	private final int MAX_PSW = 256;

	public RegistrationTask(/*db*/) throws RemoteException {
		super();
		//this.db = db;
	}

	@Override
	public String registerUser(String nickUser, String password) throws RemoteException {
		// controllo che nickUser e password non siano nulli
		if (nickUser == null || nickUser.isEmpty() || nickUser.length() > MAX_NICK) {
			return "Invalid Username";
		}
		if (password == null || password.isEmpty() || password.length() > MAX_PSW) {
			return "Invalid password";
		} else {
			return "Registration completed!";
		}
		// controllo che nel db nuckUser non sia già presente o provo a inserire nel db
		// se è presente ritorno errore
		// altrimenti viene aggiunta al db [nick, Utente (struttura dati)]
	}

}

