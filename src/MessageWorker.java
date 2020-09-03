
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

public class MessageWorker {

	private Gson gson;

	public MessageWorker() {
		gson = new Gson();
	}

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
		outBuffer.clear();
		return true;
	}

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
		buffer.clear();

		return response;
	}

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
				e.printStackTrace();
				return false;
			}
		}
		outBuffer.clear();

		if (isLogout) {
			try {
				System.out.println("Client disconnected " + client);
				client.close();
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		} else {
//			set the channel ready for reading 
			try {
				client.register(selector, SelectionKey.OP_READ);
			} catch (ClosedChannelException e) {
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}

	public synchronized boolean sendResponse(String response, SocketChannel client) {
		if (response == null || client == null)
			throw new NullPointerException();

		byte[] message = new String(response).getBytes();
		ByteBuffer outBuffer = ByteBuffer.wrap(message);

		while (outBuffer.hasRemaining()) {
			try {
				client.write(outBuffer);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}
}

class Message {
	public String operation;
	public String nick;
	public String opt;
	public int udpPort;
}
