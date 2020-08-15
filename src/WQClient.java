import java.nio.channels.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

import java.net.*;
import java.io.IOException;

public class WQClient {

	public static int PORT = 9999;
	public final static String serverHost = "localhost";
	
	private MessageWorker msgWorker;
	private Scanner scan;
	private SocketChannel socket;

	public WQClient() {
		msgWorker = new MessageWorker();
		scan = new Scanner(System.in);
		socket = null;
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

		Message message = msgWorker.writeMessage(input);

//		Checks syntax and call the correct handler
		switch (message.operation) {
			case "register":
				if (message.nickUser == null || message.opt == null) {
					System.out.println("Select a valid username and password");
					break;
				}
				handleRegister(message.nickUser, message.opt);
				break;
			case "login":
				if (message.nickUser == null || message.opt == null) {
					System.out.println("Select a valid username and password");
					break;
				}
				handleLogin(message);
				break;
			case "add_friend":
				if (message.nickUser == null || message.opt == null) {
					System.out.println("Select a valid username and friendname");
					break;
				}
				handleRequest(message);
				break;
			case "logout":
			case "friend_list":
			case "score":
			case "ranking":
				if (input.length() > 1) {
					System.out.println("Wrong usage");
					break;
				}
				if (message.nickUser == null) {
					System.out.println("Select a valid username");
					break;
				}
				handleRequest(message);
				break;
			case "challenge":
				if (message.nickUser == null || message.opt == null) {
					System.out.println("Select a valid username and friendname");
					break;
				}
				handleChallenge(message);
				break;
			case "ans_challenge":
				if (message.nickUser == null) {
					System.out.println("Not a valid answer");
					break;
				}
			default:
				System.out.println("Wrong usage");
				break;
			}
		}

		private void handleRegister(String nickUser, String password) {
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

		private void handleLogin(Message message) {

			if (socket == null || !socket.isConnected()) {
				SocketAddress address = new InetSocketAddress(serverHost, PORT);
				try {
					socket = SocketChannel.open(address);
				} catch (IOException e) {
					e.printStackTrace();
				}

				String response = msgWorker.sendAndReceive(message, socket);
				System.out.println(response);

//				rimediare in lettura, usando magari uno stream o un buffer della dimensione corretta
//				per evitare l'uso di trim()
				if (!response.trim().equals("Login succeeded!")) {
					try {
						socket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

			} else if (socket.isConnected()) {
				System.out.println("You are already logged in");
				return;
			}
		}

		private void handleRequest(Message message) {
			if (!socket.isConnected()) {
				System.out.println("You are not logged in");
				return;
			}
			String response = msgWorker.sendAndReceive(message, socket);
			System.out.println(response);

		}

		private void handleChallenge(Message message) {
//		aspetta per la risposta del server per dirti che l'amico ha accettato la sfida
		}
	}
