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

public class WQServer {

	public final static int PORT = 9999;

	public static void main(String[] args) {

		WQDatabase db = new WQDatabase();
		MessageWorker msgWorker = new MessageWorker();
		ConcurrentHashMap<SocketChannel, String> onlineUsr = new ConcurrentHashMap<>();
		ConcurrentHashMap<String, InetSocketAddress> usrAddress = new ConcurrentHashMap<>();

		// Setting RMI method for registration
		try {
			RegistrationTask regService = new RegistrationTask(db);
			// Export the object
			RegistrationRemote stub = (RegistrationRemote) UnicastRemoteObject.exportObject(regService, 6789);
			// creation of a registry
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
			// Set the channel
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
					// Set the connection
					if (key.isAcceptable()) {
						ServerSocketChannel server = (ServerSocketChannel) key.channel();
						SocketChannel client = server.accept();
						System.out.println("Accepted connection from " + client);
						client.configureBlocking(false);
						client.register(selector, SelectionKey.OP_READ);
					} else if (key.isReadable()) {
						// Read the data from client
						System.out.println("Channel is Readable");
						SocketChannel client = (SocketChannel) key.channel();

						if (client.read(buffer) == -1) {
							System.out.println("Channel is closed by the client " + client);
							handler.forcedLogout(client);
							key.cancel();
							client.close();
						} else {
							buffer.flip();
							Message input = msgWorker.readMessage(buffer);

							// Allow write operation on channel
							SelectionKey writeKey = client.register(selector, SelectionKey.OP_WRITE);
							writeKey.attach(input);
							buffer.clear();
						}
					} else if (key.isWritable()) {
						System.out.println("Channel is Writable");
						SocketChannel client = (SocketChannel) key.channel();
						Message messageInput = (Message) key.attachment();

						handler.parseClient(messageInput, client);
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
