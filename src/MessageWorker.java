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
	private ByteBuffer buffer;

	public MessageWorker() {
		gson = new Gson();
		buffer = ByteBuffer.allocate(516);
	}

	public Message writeMessage(String input) {
		String s[] = input.split(" ");
		Message msg = new Message();

		if (s.length > 0)
			msg.operation = s[0];
		if (s.length > 1)
			msg.nickUser = s[1];
		if (s.length == 3)
			msg.opt = s[2];

		return msg;
	}

	public void sendMessage(Message msg, SocketChannel sock) {
		String output = gson.toJson(msg);
		byte[] message = new String(output).getBytes();
		ByteBuffer outBuffer = ByteBuffer.wrap(message);

		while (outBuffer.hasRemaining()) {
			try {
				sock.write(outBuffer);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		outBuffer.clear();
	}
	
	public String sendAndReceive(Message message, SocketChannel socket) {
		sendMessage(message, socket);

		try {
			socket.read(buffer);
		} catch (IOException e) {
			e.printStackTrace();
		}
		String response = new String(buffer.array(), StandardCharsets.US_ASCII);
		buffer.clear();

		return response;
	}
	
	public Message readMessage(ByteBuffer buffer) {
		String string = StandardCharsets.UTF_8.decode(buffer).toString();
		Message msg = gson.fromJson(string, Message.class);

		return msg;
	}
	
	public void sendResponse(String response, SocketChannel client, Selector selector) {
		byte[] message = new String(response).getBytes();
		ByteBuffer outBuffer = ByteBuffer.wrap(message);

		while (outBuffer.hasRemaining()) {
			try {
				client.write(outBuffer);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		outBuffer.clear();

		try {
			client.register(selector, SelectionKey.OP_READ);
		} catch (ClosedChannelException e) {
			e.printStackTrace();
		}
	}
}

class Message {
	public String operation;
	public String nickUser;
	public String opt;
}
