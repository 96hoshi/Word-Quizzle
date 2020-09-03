/**
 * @author Marta Lo Cascio
 * @matricola 532686
 * @project RCL - Word Quizzle
 */

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RegistrationRemote extends Remote {

	public String registerUser(String nickUser, String password) throws RemoteException;

}
