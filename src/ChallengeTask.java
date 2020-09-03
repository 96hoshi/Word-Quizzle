/**
 * @author Marta Lo Cascio
 * @matricola 532686
 * @project RCL - Word Quizzle
 */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChallengeTask implements Runnable {

	private static final int REQUEST_TIME = 8000;
	private static final int CHALLENGE_TIME = 60000;
	private static final int NUM_WORDS = 8;

	private Message message;
	private SocketChannel clientSock;
	private SocketChannel friendSock;
	private MessageWorker msgWorker;
	private Selector selector;
	private WQDatabase database;
	private ConcurrentHashMap<String, InetSocketAddress> usrAddress;
	private ConcurrentHashMap<SocketChannel, String> onlineUsr;

	private DatagramSocket udpSocket;
	private Selector challengeSel;
	private TranslationHandler translHandler;
	private HashMap<SocketChannel, ChallengeStatus> status;

	private Thread timeout;
	private boolean running = true;
	private boolean exit = false;

	public ChallengeTask(Message message, MessageWorker msgWorker, SocketChannel client, SocketChannel friendSock,
			Selector sel, WQDatabase db, ConcurrentHashMap<String, InetSocketAddress> ua,
			ConcurrentHashMap<SocketChannel, String> ou) {
		if (message == null || msgWorker == null || client == null || friendSock == null || sel == null || db == null
				|| ua == null || ou == null)
			throw new NullPointerException();

		this.message = message;
		this.clientSock = client;
		this.friendSock = friendSock;
		this.msgWorker = msgWorker;
		this.selector = sel;
		this.usrAddress = ua;
		this.database = db;
		this.onlineUsr = ou;

		udpSocket = null;
		translHandler = new TranslationHandler();
		status = new HashMap<SocketChannel, ChallengeStatus>();
		
		timeout = new Thread() {
			@Override
			public void run() {
				try {
					Thread.sleep(CHALLENGE_TIME);
				} catch (InterruptedException e) {
					return;
				}
				if(challengeSel != null)
					challengeSel.wakeup();
			}
		};
	}

	@Override
	public void run() {
		String friendname = message.nick;
		String username = message.opt;

//		Setting udp datagrams
		DatagramPacket sendPacket = null;
		DatagramPacket receivePacket = null;

//		Sending udp request to friend
		try {
			udpSocket = new DatagramSocket();
			udpSocket.setSoTimeout(REQUEST_TIME);

			InetSocketAddress address = usrAddress.get(friendname);
			String challengeReq = username + " has challenged you!\nWhat is your answer? Y/N";
			sendPacket = new DatagramPacket(challengeReq.getBytes(), challengeReq.length(), address);
			udpSocket.send(sendPacket);

//			Waiting for client answer
			byte[] receiveData = new byte[64];
			receivePacket = new DatagramPacket(receiveData, receiveData.length);
			try {
				udpSocket.receive(receivePacket);
			} catch (SocketTimeoutException e) {
				// If client does not answer handle timeout exception
				msgWorker.sendResponse("TIMEOUT", clientSock, selector, false);
				selector.wakeup();
				udpSocket.close();
				return;
			}
		} catch (IOException ioe) {
			msgWorker.sendResponse("ERROR", clientSock, selector, false);
			selector.wakeup();
			udpSocket.close();
			return;
		}
//		Parse friend answer
		String friendAnswer = new String(receivePacket.getData()).trim();
		if (!friendAnswer.equals("Y")) {
			msgWorker.sendResponse("N", clientSock, selector, false);
			selector.wakeup();
			return;
		}
		msgWorker.sendResponse(friendAnswer, clientSock);

//		From now on the challenge can begin
		status.put(clientSock, new ChallengeStatus());
		status.put(friendSock, new ChallengeStatus());

		LinkedList<String>[] words = null;

		try {
			friendSock.register(selector, 0);

//			Setting challenge selector
			challengeSel = Selector.open();

			clientSock.register(challengeSel, SelectionKey.OP_WRITE);
			friendSock.register(challengeSel, SelectionKey.OP_WRITE);
			words = translHandler.getWords();

//			See the chosen words
			for (int i = 0; i < words.length; i++)
				System.out.println(words[i]);

		} catch (ClosedChannelException cce) {
			cce.printStackTrace();
			closeChannels();
			return;
		} catch (IOException ioe) {
//			Translation Error
			msgWorker.sendResponse("ERROR", clientSock, selector, false);
			msgWorker.sendResponse("ERROR", friendSock, selector, false);
			selector.wakeup();
			closeChannels();
			return;
		}
//		The opponent accepted the challenge and they are both ready to start
		if (!sendToBoth("START")) {
			selector.wakeup();
			closeChannels();
			return;
		}

//		Setting timer
		final long startTime = System.currentTimeMillis();
		timeout.start();
		try {
			while (running) {
				challengeSel.select();

				if (System.currentTimeMillis() >= startTime + CHALLENGE_TIME) {
					break;
				}

				Set<SelectionKey> readyKeys = challengeSel.selectedKeys();
				Iterator<SelectionKey> itr = readyKeys.iterator();

				while (itr.hasNext()) {
					SelectionKey key = itr.next();
					itr.remove();

					if (!key.isValid()) {
						continue;
					} else if (key.isWritable()) {
						if (!sendWord(key, words)) {
							return;
						}
					} else if (key.isReadable()) {
						if (!readTranslation(key, words)) {
							exit = true;
						}
					}
				}
				// If both have translated all the words, exit
				int clientIndex = status.get(clientSock).getWordIndex();
				int friendIndex = status.get(friendSock).getWordIndex();

				if (clientIndex == NUM_WORDS && friendIndex == NUM_WORDS) {
					timeout.interrupt();
					running = false;
				}
			}
			if (exit == true) {
				sendError(clientSock);
				sendError(friendSock);
				closeChannels();
				return;
			}
			if (!sendScore(username, friendname)) {
				return;
			}

			challengeSel.close();
			udpSocket.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
			return;
		}

		try {
			clientSock.register(selector, SelectionKey.OP_READ);
			friendSock.register(selector, SelectionKey.OP_READ);
			selector.wakeup();
		} catch (ClosedChannelException e) {
			e.printStackTrace();
			return;
		}
	}

	private boolean sendToBoth(String response) {

		boolean client = msgWorker.sendResponse(response, clientSock);
		if(!client) {
			disconnectClient(clientSock);
		}
		boolean friend = msgWorker.sendResponse(response, friendSock);
		if (!friend) {
			disconnectClient(friendSock);
		}
		return client && friend;
	}

	private void sendError(SocketChannel sock) {
		if (!msgWorker.sendResponse("ERROR", sock)) {
			disconnectClient(sock);
			return;
		}
		try {
			sock.register(selector, SelectionKey.OP_READ);
			selector.wakeup();
		} catch (ClosedChannelException e) {
			return;
		}
	}
	private boolean sendWord(SelectionKey key, LinkedList<String>[] words) {
		SocketChannel sock = (SocketChannel) key.channel();

		if (exit == true) {
			sendError(sock);
			closeChannels();
			return false;
		}
		
		int index = status.get(sock).getWordIndex();
		String word = words[index].get(0);

		return msgWorker.sendResponse(word, sock, challengeSel, false);
	}

	private boolean readTranslation(SelectionKey key, LinkedList<String>[] words) {
		SocketChannel sock = (SocketChannel) key.channel();
		ByteBuffer buffer = ByteBuffer.allocate(516);

		int nread = 0;
		try {
			nread = sock.read(buffer);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		if (nread == -1) {
			disconnectClient(sock);
			return false;
		}
		String translation = new String(buffer.array(), StandardCharsets.UTF_8).toLowerCase().trim();
		ChallengeStatus challenger = (ChallengeStatus) status.get(sock);
		int index = challenger.getWordIndex();

		if (correctTranslation(translation, words, index)) {
			challenger.incrementCorrects();
			challenger.addScore(2);
		} else {
			challenger.incrementErrors();
			challenger.addScore(-1);
		}
		challenger.incrementWordIndex();

		try {
//			Set the client ready for the next word
			if (challenger.getWordIndex() < NUM_WORDS) {
				sock.register(challengeSel, SelectionKey.OP_WRITE);
			} else {
//				There are no more words
				sock.register(challengeSel, 0);
			}
		} catch (ClosedChannelException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

//	Check the translation correction
	private boolean correctTranslation(String translated, LinkedList<String>[] words, int index) {
		if (translated == null) {
			return false;
		}

		for (int i = 1; i < words[index].size(); i++) {
			if (translated.equals(words[index].get(i))) {
				return true;
			}
		}
		return false;
	}

//	Calculate challenger score and send result string
	private boolean sendScore(String username, String friendname) {
		int clientScore = status.get(clientSock).getScore();
		int friendScore = status.get(friendSock).getScore();
		String winner = null;

		if (clientScore > friendScore) {
			winner = username;
		} else if (friendScore > clientScore) {
			winner = friendname;
		}

		String clientResponse = makeScoreResponse(username, clientSock, clientScore, friendScore, winner);
		String friendResponse = makeScoreResponse(friendname, friendSock, friendScore, clientScore, winner);

		database.updateScore(username, status.get(clientSock).getScore());
		database.updateScore(friendname, status.get(friendSock).getScore());

		boolean client = msgWorker.sendResponse(clientResponse, clientSock);
		boolean friend = msgWorker.sendResponse(friendResponse, friendSock);

		return client && friend;
	}

	private String makeScoreResponse(String name, SocketChannel sock, int score, int enemyScore, String winner) {
		int corrects = status.get(sock).getCorrects();
		int errors = status.get(sock).getErrors();
		int remainings = NUM_WORDS - (corrects + errors);

		String endline = null;
		if (winner == null) {
			endline = "Draw!";
		} else if (winner.equals(name)) {
			endline = "Congratulation, you win! You have gained 3 extra points, for a total of " + (score + 3)
					+ " points!\n";
			status.get(sock).addScore(3);
		} else {
			endline = "You lose.\n";
		}

		String res = "You have translated " + corrects + " words correctly, " + errors + " wrongly and not answered to "
				+ remainings + ".\n" + "You have got " + score + " points.\n" + "Your friend got " + enemyScore
				+ " points.\n" + endline;

		return res;
	}

	private void closeChannels() {
		udpSocket.close();
		try {
			challengeSel.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void disconnectClient(SocketChannel sock) {
		try {
			sock.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		onlineUsr.remove(sock);
		System.out.println("Connection Error: client disconnected");
	}
}
