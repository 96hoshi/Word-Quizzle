
/**
 * @author Marta Lo Cascio
 * @matricola 532686
 * @project RCL - Word Quizzle
 */

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;

// Class responsible to send and receive all TCP messages from client to server
// and from server to client.
public class MessageWorker {

	private Gson gson;

	public MessageWorker() {
		gson = new Gson();
	}

	// Parse an input line form client and make it into a Message.
	// Message is a support structure
	public Message writeMessage(String input) {
		if (input == null)
			throw new NullPointerException();

		String s[] = input.split(" ");
		Message msg = new Message();

		if (s.length > 0)
			msg.operation = s[0];
		if (s.length > 1)
			msg.nick = s[1];
		if (s.length == 3)
			msg.opt = s[2];

		return msg;
	}

	// Send a JSON parsed Message to a selected socket
	public boolean sendMessage(Message msg, SocketChannel sock) {
		if (msg == null || sock == null)
			throw new NullPointerException();

		String output = gson.toJson(msg);
		byte[] message = new String(output).getBytes();
		ByteBuffer outBuffer = ByteBuffer.wrap(message);

		while (outBuffer.hasRemaining()) {
			try {
				sock.write(outBuffer);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}

	// Waits to read a TCP message from a selected socket
	public String receiveLine(SocketChannel sock) {
		if (sock == null)
			throw new NullPointerException();

		ByteBuffer buffer = ByteBuffer.allocate(516);

		int nread = 0;
		try {
			nread = sock.read(buffer);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (nread == -1) {
			return "ERROR";
		}
		String response = new String(buffer.array(), StandardCharsets.UTF_8).trim();
		return response;
	}

	// Send and receive a Message to and from a socket
	public String sendAndReceive(Message msg, SocketChannel sock) {
		if (msg == null || sock == null)
			throw new NullPointerException();

		sendMessage(msg, sock);
		ByteBuffer buffer = ByteBuffer.allocate(516);

		int nread = 0;
		try {
			nread = sock.read(buffer);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (nread == -1) {
			return "Error: Connection ended";
		}
		String response = new String(buffer.array(), StandardCharsets.UTF_8).trim();

		return response;
	}

	// Read informations from a buffer and put them in a Message type structure
	public Message readMessage(ByteBuffer buffer) {
		if (buffer == null)
			throw new NullPointerException();

		String string = StandardCharsets.UTF_8.decode(buffer).toString();
		Message msg = null;
		try {
			msg = gson.fromJson(string, Message.class);
		} catch (Exception e) {
			msg = null;
		}

		return msg;
	}

	// Method used from server, thread-safe.
	// Sends a response string to a client channel.
	// If isLogout flag is set to true then closes the connection.
	// If false, then registers the channel with the main selector to a read
	// operation, client hasn't finish yet!
	public synchronized boolean sendResponse(String response, SocketChannel client, Selector selector,
			boolean isLogout) {
		if (response == null || client == null || selector == null)
			throw new NullPointerException();

		byte[] message = new String(response).getBytes();
		ByteBuffer outBuffer = ByteBuffer.wrap(message);

		while (outBuffer.hasRemaining()) {
			try {
				client.write(outBuffer);
			} catch (IOException e) {
				return false;
			}
		}
		
		// Client has requested to logout, no more operation to do for them.
		if (isLogout) {
			try {
				System.out.println("Client disconnected " + client);
				client.close();
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		} else {
			// Set the channel ready for reading 
			try {
				client.register(selector, SelectionKey.OP_READ);
			} catch (ClosedChannelException e) {
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}

	// Method used from server, thread-safe.
	// Sends a response string to a client channel, with no settings for selector.
	public synchronized boolean sendResponse(String response, SocketChannel client) {
		if (response == null || client == null)
			throw new NullPointerException();

		byte[] message = new String(response).getBytes();
		ByteBuffer outBuffer = ByteBuffer.wrap(message);

		while (outBuffer.hasRemaining()) {
			try {
				client.write(outBuffer);
			} catch (IOException e) {
				return false;
			}
		}
		return true;
	}
}

// Support structure to represent a client request.
// Contains all fields that a client can send to the server
class Message {
	public String operation;
	public String nick;
	public String opt;
	public int udpPort;
}
