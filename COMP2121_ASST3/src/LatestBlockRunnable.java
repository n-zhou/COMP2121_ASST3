import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;

public class LatestBlockRunnable implements Runnable {

	private ConcurrentHashMap<ServerInfo, Date> serverStatus;
	private Blockchain blockchain;
	private int port;

	public LatestBlockRunnable(ConcurrentHashMap<ServerInfo, Date> serverStatus, Blockchain blockchain, int port) {
		this.serverStatus = serverStatus;
		this.blockchain = blockchain;
		this.port = port;
	}

	@Override
	public void run() {
		while(true) {
			LinkedList<Thread> threads = new LinkedList<>();


			Block block = null;
			int length = 0;
			synchronized(blockchain) {
				block = blockchain.getHead();
				length = blockchain.getLength();
			}
			HashMap<ServerInfo, Date> servers = new HashMap<>(serverStatus);
			for(ServerInfo server : servers.keySet()) {
				Block b = block;
				int l = length;
				threads.add(new Thread(new Runnable() {

					@Override
					public void run() {
						try {
							Socket socket = new Socket();
							socket.connect(new InetSocketAddress(server.getHost(), server.getPort()), 2000);
							PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);

				            // send the message forward
							if(l == 0)
								printWriter.println(String.format("lb|%d|%d|%s", port, l,
				            		Base64.getEncoder().encodeToString(new byte[32])));
							else
								printWriter.println(String.format("lb|%d|%d|%s", port, l,
				            		Base64.getEncoder().encodeToString(b.calculateHash())));

							printWriter.println("cc");
				            printWriter.flush();

				            // close printWriter and socket
				            printWriter.close();
				            socket.close();
						} catch(IOException e) {

						}
					}
				}));
				threads.getLast().start();
			}
			for(Thread t : threads)
				try {
					t.join();
				} catch (InterruptedException e) {

				}
			try {
				Thread.sleep(2100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
