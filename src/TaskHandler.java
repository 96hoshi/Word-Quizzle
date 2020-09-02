import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.SortedSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TaskHandler {

	private final Selector selector;
	private WQDatabase database;
	private MessageWorker msgWorker;
	private ConcurrentHashMap<SocketChannel, String> onlineUsr;
	private ConcurrentHashMap<String, InetSocketAddress> usrAddress;
	private ThreadPoolExecutor tPool;

	public TaskHandler(Selector selector, WQDatabase db, ConcurrentHashMap<SocketChannel, String> ou,
			ConcurrentHashMap<String, InetSocketAddress> ua) {
		this.selector = selector;
		this.database = db;
		this.onlineUsr = ou;
		this.usrAddress = ua;
		msgWorker = new MessageWorker();
		
		int nThreads = 4;
		long keepAliveTime = 1;
		LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();
		tPool = new ThreadPoolExecutor(nThreads, nThreads + 2, keepAliveTime, TimeUnit.SECONDS,
				workQueue);
	}

	public void parseClient(Message message, SocketChannel client, SelectionKey key) {

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
				challengeTask(message, client, key);
				break;
			default:
				msgWorker.sendResponse("Invalid operation", client, selector, false);
				break;
			}
	}

	private void loginTask(Message message, SocketChannel client) {
		String username = message.nick;
		String password = message.opt;
		int udpPort = message.udpPort;
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
				response = "Error: Wrong password";
			}
		} else {
			response = "Error: Wrong username";
		}
		InetSocketAddress addr = new InetSocketAddress(client.socket().getInetAddress(), udpPort);
		usrAddress.put(username, addr);
		msgWorker.sendResponse(response, client, selector, false);
	}

	private void addFriendTask(Message message, SocketChannel client) {
		String friendname = message.nick;
		String username = message.opt;
		String response = null;
		
		if (friendname.equals(username)) {
			response = "Error: You cannot add yourself as friend!";
			msgWorker.sendResponse(response, client, selector, false);
			return;
		}

		if (database.findUser(friendname)) {
			if (database.updateFriendList(username, friendname)) {
				response = "Friendship " + username + " - " + friendname + " created!";
			} else {
				response = "Error: You are already friend with " + friendname;
			}
		} else {
			response = "Error: " + friendname + " is not a valid user";
		}

		msgWorker.sendResponse(response, client, selector, false);
	}

	private void logoutTask(Message message, SocketChannel client) {
		String username = message.opt;
		String response = null;

		if (!onlineUsr.remove(username, client)) {
			response = "Error: Invalid operation";
		} else {
			response = "Logout succeeded!";
		}

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
	
	private void challengeTask(Message message, SocketChannel client, SelectionKey key) {
		String friendname = message.nick;
		String username = message.opt;
		String response = null;

		if (friendname.equals(username)) {
			response = "Error: You cannot challenge yourself!";
			msgWorker.sendResponse(response, client, selector, false);
			return;
		}
		if (!database.findUser(friendname)) {
			response = "Error: Not a valid friendname";
			msgWorker.sendResponse(response, client, selector, false);
			return;
		}
		if (!database.getFriendList(username).contains(friendname)) {
			response = "Error: You are not friend with " + friendname;
			msgWorker.sendResponse(response, client, selector, false);
			return;
		}
		if (!onlineUsr.containsValue(friendname)) {
			response = "Error: Your friend is not online";
			msgWorker.sendResponse(response, client, selector, false);
			return;
		}

//		client.register(selector, 0); this throws closed channel exception
		key.interestOps(0);
		SocketChannel friendSock = null;
		for (SocketChannel sock : onlineUsr.keySet()) {
			if (onlineUsr.get(sock).equals(friendname)) {
				friendSock = sock;
				break;
			}
		}
		ChallengeTask challenge = new ChallengeTask(message, msgWorker, client, friendSock, selector, database, usrAddress);
		tPool.execute(challenge);
	}

	public void forcedLogout(SocketChannel client) {
		onlineUsr.remove(client);
	}
}
