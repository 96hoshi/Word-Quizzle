import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;

public class MessageWorker {

	Gson gson;
	ByteBuffer outBuffer;

	public MessageWorker() {
		gson = new Gson();
	}

	public Message readMessage(ByteBuffer buffer) {
		String string = StandardCharsets.UTF_8.decode(buffer).toString();
		Message msg = gson.fromJson(string, Message.class);

		return msg;
	}

	public Message writeMessage(String input) {
		String s[] = input.split(" ");
		Message msg = new Message();

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
		outBuffer = ByteBuffer.wrap(message);

		while (outBuffer.hasRemaining()) {
			try {
				sock.write(outBuffer);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		outBuffer.clear();
	}
}

class Message {
	public String operation;
	public String nickUser;
	public String opt;
}
