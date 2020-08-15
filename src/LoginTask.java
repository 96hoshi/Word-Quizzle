import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class LoginTask implements Runnable {

	private Message msg;
	private SocketChannel client;
	private Selector selector;
	private WQDatabase database;

	public LoginTask(Message message, SocketChannel client, Selector selector, WQDatabase db) {
		this.msg = message;
		this.client = client;
		this.selector = selector;
		this.database = db;
	}

	@Override
	public void run() {
		String username = msg.nickUser;
		String password = msg.opt;
		String response = null;
		MessageWorker msgWorker = new MessageWorker();

		if (database.findUser(username)) {
			if (database.matchPassword(username, password)) {
				response = "Login succeeded!";
			} else {
				response = "Wrong password";
			}
		} else {
			response = "Wrong username";
		}
		msgWorker.sendResponse(response, client, selector);
	}

}
