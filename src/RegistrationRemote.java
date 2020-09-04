/**
 * @author Marta Lo Cascio
 * @matricola 532686
 * @project RCL - Word Quizzle
 */

import java.rmi.Remote;
import java.rmi.RemoteException;

// Interface used for the remote method invocation.
// Registration to the Word Quizzle service is implemented by RMI
public interface RegistrationRemote extends Remote {

	public String registerUser(String nickUser, String password) throws RemoteException;

}
