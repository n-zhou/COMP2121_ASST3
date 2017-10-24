import java.util.Date;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class Remover implements Runnable {

	private ConcurrentHashMap<ServerInfo, Date> serverStatus;
	
	public Remover(ConcurrentHashMap<ServerInfo, Date> serverStatus) {
		this.serverStatus = serverStatus;
	}
	
	@Override
	public void run() {
		while(true) {
            for (Entry<ServerInfo, Date> entry : serverStatus.entrySet()) {
                // if greater than 2T, remove
                if (new Date().getTime() - entry.getValue().getTime() > 4000) {
                    serverStatus.remove(entry);
                }
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }
        }
	}

}
