import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.SortedSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class TaskHandler {

	private final Selector selector;
	private WQDatabase database;
	private MessageWorker msgWorker;
	private ConcurrentHashMap<SocketChannel, String> onlineUsr;

	public TaskHandler(Selector selector, WQDatabase db, ConcurrentHashMap<SocketChannel, String> ou) {
		this.selector = selector;
		this.database = db;
		this.onlineUsr = ou;
		msgWorker = new MessageWorker();
	}

	public void parseClient(Message message, SocketChannel client) {

		switch (message.operation) {
			case "login":
				loginTask(message, client);
				break;
			case "add_friend":
				addFriendTask(message, client);
				break;
			case "logout":
				logoutTask(message, client);
				break;
			case "friend_list":
				friendListTask(message, client);
				break;
			case "score":
				scoreTask(message, client);
				break;
			case "ranking":
				rankingTask(message, client);
				break;
			case "challenge":
			case "ans_challenge":
			default:
				break;
			}
	}

	private void loginTask(Message message, SocketChannel client) {
		String username = message.nick;
		String password = message.opt;
		String response = null;

//		TODO: valutare se mandare direttamente login failed, invece di specificare
//		se l'user o la password sono sbagliate
		if (database.findUser(username)) {
			if (database.matchPassword(username, password)) {
				if (onlineUsr.put(client, username) != null) {
					response = "You are already logged in!";
				} else {
					response = "Login succeeded!";
				}
			} else {
				response = "Wrong password";
			}
		} else {
			response = "Wrong username";
		}
		msgWorker.sendResponse(response, client, selector, false);
	}

	private void addFriendTask(Message message, SocketChannel client) {
		String friendname = message.nick;
		String username = message.opt;
		String response = null;

		if (database.findUser(friendname)) {
			if (database.updateFriendList(username, friendname)) {
				response = "Firendship " + username + " - " + friendname + " created!";
			} else {
				response = "You are already friend with " + friendname;
			}
		} else {
			response = friendname + " is not a valid user";
		}

		msgWorker.sendResponse(response, client, selector, false);
	}

	private void logoutTask(Message message, SocketChannel client) {
		String username = message.opt;
		String response = null;

		if (!onlineUsr.remove(username, client)) {
			response = "Invalid operation";
		}
		response = "Logout succeeded!";

		msgWorker.sendResponse(response, client, selector, true);
	}

	private void friendListTask(Message message, SocketChannel client) {
		String username = message.opt;
		String response = null;

		LinkedHashSet<String> list = database.getFriendList(username);
		response = "Friends:" + Arrays.toString(list.toArray()).replaceAll("[\\[\\]\\s]", " ");

		msgWorker.sendResponse(response, client, selector, false);
	}

	private void scoreTask(Message message, SocketChannel client) {
		String username = message.opt;

		int score = database.getScore(username);
		String response = "Score: " + String.valueOf(score);
		msgWorker.sendResponse(response, client, selector, false);
	}

	private void rankingTask(Message message, SocketChannel client) {
		String username = message.opt;

		SortedSet<Entry<String, Integer>> map = database.getRanking(username);
		String response = "Ranking:" + Arrays.toString(map.toArray()).replaceAll("[\\[\\]\\s]", " ");
		msgWorker.sendResponse(response, client, selector, false);

	}

	public void forcedLogout(SocketChannel client) {
		onlineUsr.remove(client);
	}
}
