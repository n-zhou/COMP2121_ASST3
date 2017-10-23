import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

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
        ConcurrentHashMap<ServerInfo, Date> serverStatus = new ConcurrentHashMap<ServerInfo, Date>();
        serverStatus.put(new ServerInfo(remoteHost, remotePort), new Date());
        //CASE 1 OF CATCH UP
        initialCatchup(serverStatus, blockchain);
        PeriodicCommitRunnable pcr = new PeriodicCommitRunnable(blockchain);
        Thread pct = new Thread(pcr);
        pct.start();
        //sends periodic heartbeats
        new Thread(new HeartBeat(serverStatus, localPort)).start();
        
        //sends periodic latestblocks
        new Thread(new LatestBlockRunnable(serverStatus, blockchain, localPort)).start();
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

    public static void initialCatchup(ConcurrentHashMap<ServerInfo, Date> serverStatus, Blockchain blockchain) {
    	ServerInfo server = null;
    	for(ServerInfo s : serverStatus.keySet())
    		server = s;
    	int length = 0;
    	Block block = null;
    	try {
    		Socket socket = new Socket();
    		socket.connect(new InetSocketAddress(server.getHost(), server.getPort()), 2000);
    		PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
    		pw.println("cu");
    		pw.flush();
    		ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
    		block = (Block) input.readObject();
    		if(block == null) {
    			pw.close();
    			input.close();
    			socket.close();
    			return;
    		}
    		blockchain.setLength(++length);
    		blockchain.setHead(block);
    		while(!base64(block.getPreviousHash()).equals(base64(new byte[32]))) {
    			socket = new Socket();
        		socket.connect(new InetSocketAddress(server.getHost(), server.getPort()), 2000);
        		pw = new PrintWriter(socket.getOutputStream(), true);
        		pw.println("cu|" +base64(block.getPreviousHash()));
        		input = new ObjectInputStream(socket.getInputStream());
    			block.setPreviousBlock((Block) input.readObject());
    			block = block.getPreviousBlock();
    			blockchain.setLength(++length);
    		}
    	} catch(IOException e) {

    	} catch(ClassNotFoundException e) {
    		e.printStackTrace();
    	}
    }
    
    public static String base64(byte[] bytes) {
    	return Base64.getEncoder().encodeToString(bytes);
    }
}
