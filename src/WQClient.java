import java.nio.channels.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.net.*;
import java.io.IOException;

public class WQClient {

	public final static int PORT = 9999;
	public final static String serverHost = "localhost";
	
	private MessageWorker msgWorker;
	private Scanner scan;
	private SocketChannel socket;
	private String nick;
	private DatagramSocket udpSocket; 

	public WQClient() {
		msgWorker = new MessageWorker();
		scan = new Scanner(System.in);
		socket = null;
		nick = null;
		try {
			udpSocket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
//		Initialize global client information
		ClientStatus.invitations = new ConcurrentHashMap<String , DatagramPacket>();
		ClientStatus.setInGame(false);
		
//		Starting udpListener for challenge request
		UDPListener challengeReq = new UDPListener(udpSocket);
		Thread udpListener = new Thread(challengeReq);
		udpListener.start();
	}

	public static void main(String[] args) throws InterruptedException {
		WQClient client = new WQClient();
		
		while (true) {
			String input = client.scan.nextLine();
			
//			Analyze and send the message to the server
			client.parseInput(input);
		}
	}

	private void parseInput(String input) throws NullPointerException {
		if (input == null)
			throw new NullPointerException();
		
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
			case "challenge":
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
//			ans_challenge y/n
			case "Y":
			case "N":
				if (args > 1) {
					System.out.println("Not a valid answer");
					break;
				}
				answerChallenge(message);
				break;
			case "--help":
				printHelp();
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
			if (socket == null || !socket.isConnected()) {
				SocketAddress address = new InetSocketAddress(serverHost, PORT);
				try {
					socket = SocketChannel.open(address);
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				message.udpPort = udpSocket.getLocalPort();
				String response = msgWorker.sendAndReceive(message, socket);
				System.out.println(response);

//				TODO: rimediare in lettura, usando magari uno stream o un buffer della dimensione corretta
//				per evitare l'uso di trim()
				if (!response.trim().equals("Login succeeded!")) {
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
			if (!socket.isConnected()) {
				System.out.println("You are not logged in");
				return;
			}

			message.opt = nick;
			String response = msgWorker.sendAndReceive(message, socket);
			if (response.trim().equals("Error: Connection ended")) {
				nick = null;
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (response.trim().equals("Logout succeded!")) {
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
			if (!socket.isConnected()) {
				System.out.println("You are not logged in");
				return;
			}

			message.opt = nick;
			String response = msgWorker.sendAndReceive(message, socket);
			if (response.trim().equals("Error: Connection ended")) {
				nick = null;
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			System.out.println(response);
		}

		private void answerChallenge(Message message) {
			if (!socket.isConnected()) {
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
			byte[] responseData = message.operation.getBytes();
			DatagramPacket sendPacket = new DatagramPacket(responseData, responseData.length, IPAddress, port);
			try {
				udpSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (message.operation.equals("N")) {
				System.out.println("Challenge rejected!");
				ClientStatus.setInGame(false);
				return;
			} else {
				System.out.println("Challenge accepted! Wait for game starting...");
				ClientStatus.setInGame(false);
			}
		}

		private void printHelp() {
			System.out.println("usage: COMMAND [ARGS ...]\n"
					+ "Commands:" + "\n" + "\tregister <nickUser> <password>  registers a user" + "\n"
					+ "\tlogin <nickUser> <password> \tlogs in a user" + "\n"
					+ "\tlogout \t\t\t\tlogs out a user" + "\n"
					+ "\tadd_friend <nickFriend> \tadds nickFriend as a friend" + "\n"
					+ "\tfriend_list \t\t\tshows user's friends list" + "\n"
					+ "\tchallenge <nickFriend> \t\tsends a challenge request to a friend" + "\n"
					+ "\tscore  \t\t\t\tshows the user's score" + "\n"
					+ "\tranking  \t\t\tshows the user's ranking");
		}
	}


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
//					With this flag setted here a user can receive only one request per time
					ClientStatus.setInGame(true);
					Timer timer = new Timer();
					timer.schedule(new InvitationTimer(friend, receivePacket), TIMEOUT);
				}
			}

		}
	}

}

class InvitationTimer extends TimerTask {

	public String friend;
	public DatagramPacket receivePacket;

	public InvitationTimer(String friend, DatagramPacket receivePacket) {
		super();
		this.friend = friend;
		this.receivePacket = receivePacket;
	}

	public void run() {
		if (ClientStatus.getInvitations().contains(receivePacket) && ClientStatus.getInvitations().get(friend) == receivePacket) {
			ClientStatus.getInvitations().remove(friend);
			ClientStatus.setInGame(false);
			System.out.println("Your invitation from " + friend + " expired.");
		}
	}
}


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














