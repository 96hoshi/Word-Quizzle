import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
	private ConcurrentHashMap<String, InetSocketAddress> usrAddress;

	public TaskHandler(Selector selector, WQDatabase db, ConcurrentHashMap<SocketChannel, String> ou,
			ConcurrentHashMap<String, InetSocketAddress> ua) {
		this.selector = selector;
		this.database = db;
		this.onlineUsr = ou;
		this.usrAddress = ua;
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
				challengeTask(message, client);
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
		}
		response = "Logout succeeded!";
		onlineUsr.remove(client);

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
	
	private void challengeTask(Message message, SocketChannel client) {
		String friendname = message.nick;
		String username = message.opt;
		String response = null;
		final int challengeRequestTime = 8000;
		
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

//		sending udp request to friend
		DatagramSocket socket = null;
		DatagramPacket sendPacket = null;
		DatagramPacket receivePacket = null;
		try {
			socket = new DatagramSocket();
			InetSocketAddress address = usrAddress.get(friendname);
			byte[] sendData = new byte[64];
			byte[] receiveData = new byte[64];
			String sentence = username + " has challenged you!\nWhat is your answer? Y/N";
			sendData = sentence.getBytes();
			sendPacket = new DatagramPacket(sendData, sendData.length, address);
			try {
				socket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
			}

			socket.setSoTimeout(challengeRequestTime);
			receivePacket = new DatagramPacket(receiveData, receiveData.length);
			try {
				socket.receive(receivePacket);
			} catch (SocketTimeoutException e) {
				// timeout exception.
				response = friendname + " does not answer. Try later.";
				msgWorker.sendResponse(response, client, selector, false);
				socket.close();
				return;
			} catch (IOException e) {
				e.printStackTrace();
				socket.close();
			}
		} catch (SocketException e) {
			e.printStackTrace();
			socket.close();
		}

		String answer = new String(receivePacket.getData()).trim();
		switch (answer) {
			case "Y":
				response = friendname + " has accepted your request!";
				msgWorker.sendResponse(response, client, selector, false);
				break;
			case "N":
			default:
				response = friendname + " has rejected your request!";
				msgWorker.sendResponse(response, client, selector, false);
			break;
		}
	}

	public void forcedLogout(SocketChannel client) {
		onlineUsr.remove(client);
	}
}
