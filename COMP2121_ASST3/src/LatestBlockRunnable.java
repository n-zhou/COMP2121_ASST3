import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

public class LatestBlockRunnable implements Runnable {
	
	private HashMap<ServerInfo, Date> serverStatus;
	private Blockchain blockchain;
	private int port;
	
	public LatestBlockRunnable(HashMap<ServerInfo, Date> serverStatus, Blockchain blockchain, int port) {
		this.serverStatus = serverStatus;
		this.blockchain = blockchain;
		this.port = port;
	}
	
	@Override
	public void run() {
		while(true) {
			LinkedList<Thread> threads = new LinkedList<>();
			for(int i = 0; i < serverStatus.keySet().toArray().length; i++) {
				ServerInfo server = (ServerInfo) serverStatus.keySet().toArray()[i];
				threads.add(new Thread(new Runnable() {
					
					@Override
					public void run() {
						try {
							Socket socket = new Socket();
							socket.connect(new InetSocketAddress(server.getHost(), server.getPort()), 2000);
							PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);

				            // send the message forward
							if(blockchain.getLength() == 0)
								printWriter.println(String.format("lb|%d|%d|%s", port, blockchain.getLength(), 
					            		Base64.getEncoder().encodeToString(new byte[32])));
							else
								printWriter.println(String.format("lb|%d|%d|%s", port, blockchain.getLength(), 
										Base64.getEncoder().encodeToString(blockchain.getHead().calculateHash())));
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
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
