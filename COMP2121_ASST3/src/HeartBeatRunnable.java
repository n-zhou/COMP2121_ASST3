import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;

public class HeartBeatRunnable implements Runnable {
	
	HashMap<ServerInfo, Date> serverStatus;
	
	public HeartBeatRunnable(HashMap<ServerInfo, Date> serverStatus) {
		this.serverStatus = serverStatus;
	}
	
	@Override
	public void run() {
		try {
			for(ServerInfo si : serverStatus.keySet())
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							Socket socket = new Socket(si.getHost(), si.getPort());
							handler(socket.getInputStream(), socket.getOutputStream());
						} catch (ConnectException e) {
							
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} 
						
					}
					
					public void handler(InputStream is, OutputStream os) {
						
					}
					
				}).start();
			Thread.sleep(2000);
		} catch(InterruptedException e) {
			
		}
	}
	
	
	
}
