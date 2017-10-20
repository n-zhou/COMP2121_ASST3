import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

public class BlockchainServer {

    public static void main(String[] args) {

        if (args.length != 3) {
            return;
        }

        int localPort = 0;
        int remotePort = 0;
        String remoteHost = null;

        try {
            localPort = Integer.parseInt(args[0]);
            remoteHost = args[1];
            remotePort = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            return;
        }

        Blockchain blockchain = new Blockchain();
        HashMap<ServerInfo, Date> serverStatus = new HashMap<ServerInfo, Date>();
        serverStatus.put(new ServerInfo(remoteHost, remotePort), new Date());
        //catchup(serverStatus, blockchain);
        PeriodicCommitRunnable pcr = new PeriodicCommitRunnable(blockchain);
        Thread pct = new Thread(pcr);
        pct.start();
        new Thread(new HeartBeat(serverStatus, localPort)).start();
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(localPort);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new BlockchainServerRunnable(clientSocket, blockchain, serverStatus)).start();
            }
        } catch (IllegalArgumentException e) {
        } catch (IOException e) {
        } finally {
            try {
                pcr.setRunning(false);
                pct.join();
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException e) {
            } catch (InterruptedException e) {
            }
        }
    }
    
    public static void catchup(HashMap<ServerInfo, Date> serverStatus, Blockchain blockchain) {
    	ServerInfo server = null;
    	for(ServerInfo s : serverStatus.keySet())
    		server = s;
    	try {
    		Socket socket = new Socket();
    		socket.connect(new InetSocketAddress(server.getHost(), server.getPort()), 2000);
    		ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
    		ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
    		output.close();
    		input.close();
    		
    	} catch(IOException e) {
    		
    	}
    	
    }
}
