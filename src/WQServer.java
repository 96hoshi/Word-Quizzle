/**
 * @author Marta Lo Cascio
 * @matricola 532686
 * @project RCL - Word Quizzle
 */

import java.nio.*;
import java.nio.channels.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;

// Main Server class.
// Initialize essential structures and contains the main selector
// for all clients connections
public class WQServer {
	// Usage: WQServer

	public final static int PORT = 9999;

	public static void main(String[] args) {

		// Restore or make a new Database
		WQDatabase db = new WQDatabase();
		// Object responsible of sending and receiving TCP messages
		MessageWorker msgWorker = new MessageWorker();
		// Support structure to track online users
		ConcurrentHashMap<SocketChannel, String> onlineUsr = new ConcurrentHashMap<>();
		// Support structure to save user addresses 
		ConcurrentHashMap<String, InetSocketAddress> usrAddress = new ConcurrentHashMap<>();

		// Setting up RMI method for registration
		try {
			RegistrationTask regService = new RegistrationTask(db);
			// Export the object
			RegistrationRemote stub = (RegistrationRemote) UnicastRemoteObject.exportObject(regService, 6789);
			// Creation of a registry
			Registry registry = LocateRegistry.createRegistry(6789);
			registry.rebind("WQ-Registration", stub);
		} catch (RemoteException e) {
			e.printStackTrace();
		}

		System.out.println("Listening for connections on port " + PORT);
		ByteBuffer buffer = ByteBuffer.allocate(516);
		ServerSocketChannel serverChannel;
		Selector selector;
		try {
			// Set up the selector
			serverChannel = ServerSocketChannel.open();
			ServerSocket serverSock = serverChannel.socket();
			InetSocketAddress address = new InetSocketAddress(PORT);

			serverSock.bind(address);
			serverChannel.configureBlocking(false);
			selector = Selector.open();
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);
		} catch (IOException ex) {
			ex.printStackTrace();
			return;
		}
		// Module that parse and schedule clients requests
		TaskHandler handler = new TaskHandler(selector, db, onlineUsr, usrAddress);

		while (true) {
			try {
				selector.select();
			} catch (IOException ex) {
				ex.printStackTrace();
				break;
			}
			Set<SelectionKey> readyKeys = selector.selectedKeys();
			Iterator<SelectionKey> iterator = readyKeys.iterator();
			while (iterator.hasNext()) {
				SelectionKey key = iterator.next();
				iterator.remove();

				try {
					// Set up the connection
					if (key.isAcceptable()) {
						ServerSocketChannel server = (ServerSocketChannel) key.channel();
						SocketChannel client = server.accept();
						System.out.println("Accepted connection from " + client);
						client.configureBlocking(false);
						client.register(selector, SelectionKey.OP_READ);
					} else if (key.isReadable()) {
						// Read the data from client
						SocketChannel client = (SocketChannel) key.channel();
						int nread;
						boolean crash = false;
						
						while ((nread = client.read(buffer)) != 0 && !crash) {
                            if (nread == -1) {
                                // If the client crashed handle it
                            	System.out.println("Channel is closed by the client " + client);
                            	crash = true;
    							handler.forcedLogout(client);
    							key.cancel();
    							client.close();
                            }
                        }
						if (crash) {
							continue;
						}

						buffer.flip();
						Message input = msgWorker.readMessage(buffer);

						// Allow write operation on channel
						SelectionKey writeKey = client.register(selector, SelectionKey.OP_WRITE);
						writeKey.attach(input);
						
						buffer.clear();

					} else if (key.isWritable()) {
						SocketChannel client = (SocketChannel) key.channel();
						Message messageInput = (Message) key.attachment();

						// Let the handler parse client request, handle the request
						// and organize a proper response
						handler.parseClient(messageInput, client, key);
					}
				} catch (IOException ex) {
					key.cancel();
					try {
						key.channel().close();
					} catch (IOException cex) {
					}
				}
			}
		}
	}
}
