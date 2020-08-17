import java.nio.channels.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

import java.net.*;
import java.io.IOException;

public class WQClient {

	public final static int PORT = 9999;
	public final static String serverHost = "localhost";
	
	private MessageWorker msgWorker;
	private Scanner scan;
	private SocketChannel socket;
	private String nick;

	public WQClient() {
		msgWorker = new MessageWorker();
		scan = new Scanner(System.in);
		socket = null;
		nick = null;
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
				challenge(message);
				break;
//			ans_challenge y/n
			case "ans_challenge":
				if (args > 2) {
					System.out.println("Wrong usage");
					break;
				}
				if (message.nick == null) {
					System.out.println("Not a valid answer");
					break;
				}
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
				registry = LocateRegistry.getRegistry(serverHost, PORT + 1);
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

		private void request(Message message) {
			if (!socket.isConnected()) {
				System.out.println("You are not logged in");
				return;
			}

			message.opt = nick;
			String response = msgWorker.sendAndReceive(message, socket);
			if (response.trim().equals("Error: Connection ended")) {
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			System.out.println(response);

		}

		private void challenge(Message message) {
//		aspetta per la risposta del server per dirti che l'amico ha accettato la sfida
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
