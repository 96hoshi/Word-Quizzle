
/**
 * @author Marta Lo Cascio
 * @matricola 532686
 * @project RCL - Word Quizzle
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// Class to create a BufferedReader which does not block indefinitely when reading
public class NonblockingBufferedReader {
	private final BlockingQueue<String> lines = new LinkedBlockingQueue<String>();
	private volatile boolean closed = false;
	private Thread backgroundReaderThread = null;

	public NonblockingBufferedReader(final BufferedReader bufferedReader) {
		backgroundReaderThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (!Thread.interrupted()) {
						String line = bufferedReader.readLine();
						if (line == null) {
							break;
						}
						lines.add(line);
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				} finally {
					closed = true;
				}
			}
		});
		backgroundReaderThread.setDaemon(true);
		backgroundReaderThread.start();
	}

//	Returns the next line, blocking for 500 milliseconds for input then returns null.
//	If the background reader thread is interrupted then returns null
	public String readLine() throws IOException {
		try {
			return closed && lines.isEmpty() ? null : lines.poll(500L, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			throw new IOException("The BackgroundReaderThread was interrupted!", e);
		}
	}

//	Closes this reader by interrupting the background reader thread
	public void close() {
		if (backgroundReaderThread != null) {
			backgroundReaderThread.interrupt();
			backgroundReaderThread = null;
		}
	}
}
