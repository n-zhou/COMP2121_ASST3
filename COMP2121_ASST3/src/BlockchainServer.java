import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Base64;
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
        catchup(serverStatus, blockchain);
        PeriodicCommitRunnable pcr = new PeriodicCommitRunnable(blockchain);
        Thread pct = new Thread(pcr);
        pct.start();
        new Thread(new HeartBeat(serverStatus, localPort)).start();
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
    
    public static void catchup(HashMap<ServerInfo, Date> serverStatus, Blockchain blockchain) {
    	ServerInfo server = null;
    	for(ServerInfo s : serverStatus.keySet())
    		server = s;
    	try {
    		Socket socket = new Socket();
    		socket.connect(new InetSocketAddress(server.getHost(), server.getPort()), 2000);
    		
    		PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
    		BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    		
    		Block block = null;
    		pw.println("cu");
    		pw.flush();
    		String line = reader.readLine();
    		String[] tokens = line.split("[|]");
    		int length = Integer.parseInt(tokens[2]);
    		String hash = tokens[3];
    		if(length == 0) {
    			
    			socket.close();
    		}
    		pw.println("cu|"+ hash);
    		pw.flush();
    		ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
    		block = (Block) input.readObject();
    		Block currentBlock = block;
    		socket.close();
    		while(!Base64.getEncoder().encodeToString(currentBlock.getPreviousHash()).equals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")) {
    			socket = new Socket();
    			socket.connect(new InetSocketAddress(server.getHost(), server.getPort()), 2000);
    			
    			pw = new PrintWriter(socket.getOutputStream(), true);
    			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    			
    			System.out.println(Base64.getEncoder().encodeToString(currentBlock.getPreviousHash()));
    			pw.println("cu|" + Base64.getEncoder().encodeToString(currentBlock.getPreviousHash()));
    			pw.flush();
    			input = new ObjectInputStream(socket.getInputStream());
    			currentBlock.setPreviousBlock((Block) input.readObject());
    			currentBlock = currentBlock.getPreviousBlock();
    			
    		}
    		blockchain.setHead(block);
    		blockchain.setLength(length);
    		pw.close();		
    		input.close();
    		socket.close();
    	} catch(IOException e) {
    		
    	} catch(ClassNotFoundException e) {
    		
    	}
    	Block b = blockchain.getHead();
    	if(b != null)
    		System.out.println("\nInitial Head hash: " + BlockchainServerRunnable.base64(b.calculateHash()));
    }
}
