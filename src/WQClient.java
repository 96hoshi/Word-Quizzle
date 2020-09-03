/**
 * @author Marta Lo Cascio
 * @matricola 532686
 * @project RCL - Word Quizzle
 */

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.ConcurrentHashMap;
import java.net.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class WQClient {

	public final static int PORT = 9999;
	public final static String serverHost = "localhost";
	private final static int CHALLENGE_TIME = 60000; 
	private final static int NUM_WORDS = 8;

	private MessageWorker msgWorker;
	private SocketChannel socket;
	private String nick;
	private DatagramSocket udpSocket;
	
	private NonblockingBufferedReader reader;

	public WQClient() {
		msgWorker = new MessageWorker();
		socket = null;
		nick = null;
		try {
			udpSocket = new DatagramSocket();
		} catch (SocketException e) {
			System.err.println("Cannot open UDP Socket");
			System.exit(1);
		}
		
		InputStreamReader fileInputStream = new InputStreamReader(System.in);
		BufferedReader bufferedReader = new BufferedReader(fileInputStream);
		reader = new NonblockingBufferedReader(bufferedReader);

//		Initialize global client information
		ClientStatus.invitations = new ConcurrentHashMap<String, DatagramPacket>();
		ClientStatus.setInGame(false);

//		Starting udpListener for challenge request
		UDPListener challengeReq = new UDPListener(udpSocket);
		Thread udpListener = new Thread(challengeReq);
		udpListener.start();
	}

	public static void main(String[] args) {
		WQClient client = new WQClient();

		if (args.length > 0) {
			if (args[0].equals("--help")) {
				client.printHelp();
			} else {
				System.err.println("Correct usage: WQClient [--help]");
				System.exit(0);
			}
		}

		String input = null;

//		Main client loop
		while (true) {
			try {
				while ((input = client.reader.readLine()) != null) {
//					Analyze and send the message to the server
					client.parseInput(input);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void parseInput(String input) throws NullPointerException {
		if (input == null) {
			System.out.println("Wrong usage");
			return;
		}

		int args = input.split(" ").length;

		if (args > 3) {
			System.out.println("Wrong usage");
			return;
		}

		Message message = msgWorker.writeMessage(input);

//		Checks syntax and call the correct handler
		switch (message.operation) {
			case "register":
				if (message.nick == null || message.opt == null) {
					System.out.println("Select a valid username and password");
					break;
				}
				register(message.nick, message.opt);
				break;
			case "login":
				if (message.nick == null || message.opt == null) {
					System.out.println("Select a valid username and password");
					break;
				}
				login(message);
				break;
			case "logout":
				if (args > 1) {
					System.out.println("Wrong usage");
					break;
				}
				logout(message);
				break;
			case "friend_list":
			case "score":
			case "ranking":
				if (args > 1) {
					System.out.println("Wrong usage");
					break;
				}
				request(message);
				break;
			case "add_friend":
				if (args > 2) {
					System.out.println("Wrong usage");
					break;
				}
				if (message.nick == null) {
					System.out.println("Select a valid friendname");
					break;
				}
				request(message);
				break;
			case "challenge":
				if (args > 2) {
					System.out.println("Wrong usage");
					break;
				}
				if (message.nick == null) {
					System.out.println("Select a valid friendname");
					break;
				}
				requestChallenge(message);
				break;
			case "Y":
			case "y":
			case "N":
			case "n":
				if (args > 1) {
					System.out.println("Not a valid answer");
					break;
				}
				answerChallenge(message);
				break;
			case "--help":
				printHelp();
				break;
			case "exit":
				exit();
				break;
			default:
				System.out.println("Wrong usage");
				break;
		}
	}

	private void register(String nickUser, String password) {
		Registry registry;
		RegistrationRemote remote;
		try {
			registry = LocateRegistry.getRegistry(serverHost, 6789);
			remote = (RegistrationRemote) registry.lookup("WQ-Registration");
			String response = remote.registerUser(nickUser, password);
			System.out.println(response);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void login(Message message) {
		if (socket == null || !socket.isConnected() || nick == null) {
			SocketAddress address = new InetSocketAddress(serverHost, PORT);
			try {
				socket = SocketChannel.open(address);
			} catch (IOException e) {
				e.printStackTrace();
			}

			message.udpPort = udpSocket.getLocalPort();
			String response = msgWorker.sendAndReceive(message, socket);
			System.out.println(response);

			if (!response.equals("Login succeeded!")) {
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				nick = message.nick;
			}

		} else if (socket.isConnected()) {
			System.out.println(nick + " is already logged in");
			return;
		}
	}

	private void logout(Message message) {
		if (socket == null || !socket.isConnected() || nick == null) {
			System.out.println("You are not logged in");
			return;
		}

		message.opt = nick;
		String response = msgWorker.sendAndReceive(message, socket);
		if (response.equals("Error: Connection ended")) {
			nick = null;
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (response.equals("Logout succeded!")) {
			nick = null;
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println(response);
	}

	private void request(Message message) {
		if (socket == null || !socket.isConnected() || nick == null) {
			System.out.println("You are not logged in");
			return;
		}

		message.opt = nick;
		String response = msgWorker.sendAndReceive(message, socket);
		if (response.equals("Error: Connection ended")) {
			nick = null;
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println(response);
	}

	private void requestChallenge(Message message) {
		if (socket == null || !socket.isConnected() || nick == null) {
			System.out.println("You are not logged in");
			return;
		}
		ClientStatus.setInGame(true);

		message.opt = nick;
		String response = msgWorker.sendAndReceive(message, socket);
		if (response.equals("Error: Connection ended")) {
			nick = null;
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if (response.equals("N")) {
			System.out.println("Your friend has rejected your invitation.");
			ClientStatus.setInGame(false);
			return;
		}
		if (response.equals("TIMEOUT")) {
			System.out.println("Your friend does not answer. Try later.");
			ClientStatus.setInGame(false);
			return;
		}
		if (response.equals("ERROR")) {
			System.out.println("An error has occurred. Try later.");
			ClientStatus.setInGame(false);
			return;
		}

//		If the client arrive here the challenged friend has accepted their request
		if (response.equals("Y")) {
			System.out.println("Your friend has accepted! Wait for game starting...");
	
			String serverResponse = msgWorker.receiveLine(socket);
			if (!serverResponse.equals("START")) {
				System.out.println("An error has occurred. Try later.");
				ClientStatus.setInGame(false);
				return;
			}
			challengeLogic();
		} else {
			System.out.println(response);
		}

	}

	private void answerChallenge(Message message) {
		if (socket == null || !socket.isConnected() || nick == null) {
			System.out.println("You are not logged in");
			return;
		}
		if (ClientStatus.getInvitations().isEmpty()) {
			System.out.println("There are not any invitations");
			return;
		}

		DatagramPacket receivePacket = null;
		for (String friend : ClientStatus.getInvitations().keySet()) {
			receivePacket = (DatagramPacket) ClientStatus.getInvitations().remove(friend);
			break;
		}

		InetAddress IPAddress = receivePacket.getAddress();
		int port = receivePacket.getPort();
		byte[] responseData = message.operation.toUpperCase().getBytes();
		DatagramPacket sendPacket = new DatagramPacket(responseData, responseData.length, IPAddress, port);
		try {
			udpSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (message.operation.toUpperCase().equals("N")) {
			System.out.println("Challenge rejected!");
			ClientStatus.setInGame(false);
			return;
		} else {
			System.out.println("Challenge accepted! Wait for game starting...");
			String serverResponse = msgWorker.receiveLine(socket);

			if (!serverResponse.equals("START")) {
				System.out.println("An error has occurred. Try later.");
				ClientStatus.setInGame(false);
				return;
			}
			challengeLogic();
		}
	}

//	Handle all messages with server during a challenge
	private void challengeLogic() {
		System.out.println("Game start!\nYou have 60 seconds to translate the following words:");

		int i = 0;
		String line = msgWorker.receiveLine(socket);

		final long startTime = System.currentTimeMillis();
		System.out.println(line);
		try {
			while ((System.currentTimeMillis() < startTime + CHALLENGE_TIME) && i < NUM_WORDS) {
				while ((line = reader.readLine()) != null) {
					byte[] message = line.getBytes();
					ByteBuffer outBuffer = ByteBuffer.wrap(message);

					while (outBuffer.hasRemaining()) {
						try {
							socket.write(outBuffer);
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					outBuffer.clear();
					i++;
					if (i == NUM_WORDS) {
						break;
					}
					line = msgWorker.receiveLine(socket);
					if (line.equals("ERROR")) {
						System.out.println("An error has occurred. Challenge interrupted.");
						ClientStatus.setInGame(false);
						return;
					}
					System.out.println(line);
				}
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		System.out.println("End of the game! Waiting for results...");

		String endGame = msgWorker.receiveLine(socket);
		if (endGame.equals("ERROR")) {
			System.out.println("An error has occurred. Challenge interrupted.");
			ClientStatus.setInGame(false);
			return;
		}
//		If the client arrives here, print score result.
		System.out.println(endGame);
		ClientStatus.setInGame(false);
	}

	private void printHelp() {
		System.out.println("usage: COMMAND [ARGS ...]\n"
				+ "Commands:" + "\n" + "\tregister <nickUser> <password>  registers a user" + "\n"
				+ "\tlogin <nickUser> <password> \tlogs in a user" + "\n"
				+ "\tlogout \t\t\t\tlogs out a user" + "\n"
				+ "\tadd_friend <nickFriend> \tadds nickFriend as a friend" + "\n"
				+ "\tfriend_list \t\t\tshows user's friends list" + "\n"
				+ "\tchallenge <nickFriend> \t\tsends a challenge request to a friend" + "\n"
				+ "\tscore  \t\t\t\tshows user's score" + "\n"
				+ "\tranking  \t\t\tshows user's ranking" + "\n"
				+ "\texit  \t\t\t\tcloses client");
	}

	private void exit() {
		if (socket == null) {
			reader.close();
			System.exit(0);
		}

		if (socket.isConnected()) {
			try {
				reader.close();
				socket.close();
				udpSocket.close();
			} catch (IOException e) {
				System.exit(1);
			}
		}
		System.exit(0);
	}
}

// Listener Class for friends invitations
class UDPListener implements Runnable {

	public final static int TIMEOUT = 8000;

	public DatagramSocket socket;

	public UDPListener(DatagramSocket udpSocket) {
		socket = udpSocket;
	}

	@Override
	public void run() {
		while (true) {
			byte[] receiveData = new byte[516];
			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			try {
				socket.receive(receivePacket);
			} catch (IOException e) {
				e.printStackTrace();
			}

			String sentence = new String(receivePacket.getData()).trim();
			String args[] = sentence.split(" ");
			String friend = args[0];
//			User is not in game
			if (!ClientStatus.isInGame()) {
//				friend doesn't already requested the challenge
				if (!ClientStatus.getInvitations().contains(friend)) {
					System.out.println("Notification: " + sentence);
					ClientStatus.invitations.put(friend, receivePacket);
//					With this flag a user can receive only one request per time
					ClientStatus.setInGame(true);

//					Set a timer for invitation expiry time
					Thread timer = new Thread() {
						@Override
						public void run() {
							String frnd = friend;
							DatagramPacket packet = receivePacket;
							try {
								Thread.sleep(TIMEOUT);
							} catch (InterruptedException e) {
								return;
							}
							if (ClientStatus.getInvitations().contains(packet)
									&& ClientStatus.getInvitations().get(frnd) == packet) {
								ClientStatus.getInvitations().remove(frnd);
								ClientStatus.setInGame(false);
								System.out.println("Your invitation from " + frnd + " expired.");
							}
						}
					};
					timer.start();
				}
			}
		}
	}

}

// Status class to handle challenges and invitations
class ClientStatus {
	public static boolean inGame;
	public static ConcurrentHashMap<String, DatagramPacket> invitations;

	public static synchronized boolean isInGame() {
		return inGame;
	}

	public static synchronized void setInGame(boolean value) {
		inGame = value;
	}

	public static synchronized ConcurrentHashMap<String, DatagramPacket> getInvitations() {
		return invitations;
	}
}
