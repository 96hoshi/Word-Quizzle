
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

import java.net.*;
import java.io.IOException;

public class WQClient {
	
	public static int PORT = 9999;
	final static String serverHost = "localhost"; 

	public static void main(String[] args) throws InterruptedException {
		Scanner scan = new Scanner(System.in);

		try {
			SocketAddress address = new InetSocketAddress("localhost", PORT);
			SocketChannel client = SocketChannel.open(address);
			ByteBuffer serverBuffer = ByteBuffer.allocate(516);

			while(true) {
				String input = scan.nextLine();

//				Analyze and send the message to the server
				if (!parseInput(input, client))
					continue;

				client.read(serverBuffer);

				String response = new String(serverBuffer.array(), StandardCharsets.US_ASCII);
				System.out.println("Server: " + response);
				serverBuffer.clear();
			}
			
		} catch (IOException ex) {
			scan.close();
			ex.printStackTrace();
		}
	}
	
	private static boolean parseInput(String input, SocketChannel client) throws NullPointerException {
		if (input == null) throw new NullPointerException();
		System.out.println("Client " + client.toString() + ": " + input);
		
		MessageWorker msgWorker = new MessageWorker();
		Message message = msgWorker.writeMessage(input);
		
//		Checks syntax and call the correct handler
		switch (message.operation) {
			case "register":
				if (message.nickUser == null || message.opt == null) {
					System.out.println("Select a valid username and password");
					return false;
				}
				handleRegister(message.nickUser, message.opt);
				return false;
			case "login":
				if (message.nickUser == null || message.opt == null) {
					System.out.println("Select a valid username and password");
					return false;
				} break;
			case "add_friend":
				if (message.nickUser == null || message.opt == null) {
					System.out.println("Select a valid username and friendname");
					return false;
				} break;
			case "logout":
			case "friend_list":
			case "score":
			case "ranking":
				if (message.nickUser == null) {
					System.out.println("Select a valid username");
					return false;
				} break;
			case "challenge":
				if (message.nickUser == null || message.opt == null) {
					System.out.println("Select a valid username and friendname");
					return false;
				} else {
					msgWorker.sendMessage(message, client);
					handleChallenge(message, client);
					return false;
				}
			case "ans_challenge":
				break;
			default:
				System.out.println("Wrong usage");
				return false;
		}
		msgWorker.sendMessage(message, client);
		return true;
	}
	
	private static void handleRegister(String nickUser, String password) {

		Registry registry = null;
		RegistrationRemote remote = null;
		try {
			registry = LocateRegistry.getRegistry(serverHost, PORT+1);
			remote = (RegistrationRemote) registry.lookup("WQ-Registration");
			String response = remote.registerUser(nickUser, password);
			System.out.println(response);
		} catch (Exception e) {
            e.printStackTrace();
		}
	}
	
	public static void handleChallenge(Message message, SocketChannel client) {
//		aspetta per la risposta del server per dirti che l'amico ha accettato la sfida
	}
}
