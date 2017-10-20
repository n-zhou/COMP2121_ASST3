import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

public class HeartBeat implements Runnable {

	private HashMap<ServerInfo, Date> serversInfo;
	private int sequence;
	private int port;
	
	public HeartBeat(HashMap<ServerInfo, Date> serversInfo, int port) {
		this.serversInfo = serversInfo;
		this.port = port;
		sequence = 0;
	}
	
	@Override
	public void run() {
		while(true) {
			try {
				LinkedList<Thread> threads = new LinkedList<>();
				synchronized(serversInfo) {
					for(ServerInfo si : serversInfo.keySet()) {
						threads.add(new Thread(new Runnable() {
							@Override
							public void run() {
								try {
						            // create socket with a timeout of 2 seconds
						            Socket toServer = new Socket();
						            toServer.connect(new InetSocketAddress(si.getHost(), si.getPort()), 2000);
						            PrintWriter printWriter = new PrintWriter(toServer.getOutputStream(), true);

						            // send the message forward
						            printWriter.println(String.format("hb|%d|%d", port, sequence));
						            printWriter.flush();

						            // close printWriter and socket
						            printWriter.close();
						            toServer.close();
						        } catch (IOException e) {
						        }
							}
						}));
						threads.getLast().start();
					}
					for(Thread t : threads)
						try {
							t.join();
						} catch(InterruptedException e) {	
						}
					
				}
				Thread.sleep(2000);
				++sequence;
				Date date = new Date();
				
			}
			catch (InterruptedException e) {
				
			}
		}
	}
}
