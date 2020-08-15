import java.nio.*;
import java.nio.channels.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.net.*;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

public class WQServer {

	public static int PORT = 9999;

	public static void main(String[] args) {

		int nThreads = 4;
		long keepAliveTime = 1;
		LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();
		ThreadPoolExecutor tPool = new ThreadPoolExecutor(nThreads, 2*nThreads, keepAliveTime, TimeUnit.SECONDS,
				workQueue);
		
		WQDatabase db = new WQDatabase();
		

		// Setting RMI method for registration
		try {
			RegistrationTask regService = new RegistrationTask(db);
			// Export the object
			RegistrationRemote stub = (RegistrationRemote) UnicastRemoteObject.exportObject(regService, PORT + 1);
			// creation of a registry
			Registry registry = LocateRegistry.createRegistry(PORT + 1);
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
							key.cancel();
							client.close();
						} else {
							buffer.flip();
							MessageWorker m = new MessageWorker();
							Message input = m.readMessage(buffer);
							handleClient(input, client, selector, tPool, db);
							buffer.clear();
						}

						// Allow write operation on channel
//						client.register(selector, SelectionKey.OP_WRITE);

					} else if (key.isWritable()) {
						// Write the echo to the client
						System.out.println("Channel is Writable");
						SocketChannel client = (SocketChannel) key.channel();

						String output = "oook!";
						byte[] message = new String(output).getBytes();
						ByteBuffer outBuffer = ByteBuffer.wrap(message);

						while (outBuffer.hasRemaining()) {
							client.write(outBuffer);
						}
						outBuffer.clear();

						client.register(selector, SelectionKey.OP_READ);
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

	private static void handleClient(Message message, SocketChannel client, Selector selector,
			ThreadPoolExecutor tPool, WQDatabase db) {

		switch (message.operation) {
			case "login":
				tPool.execute(new LoginTask(message, client, selector, db));
				break;
			case "add_friend":
			case "logout":
			case "friend_list":
			case "score":
			case "ranking":
			case "challenge":
			case "ans_challenge":
			default:
				break;
		}
	}
}
