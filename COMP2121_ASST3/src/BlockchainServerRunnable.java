import java.io.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class BlockchainServerRunnable implements Runnable{

    private Socket clientSocket;
    private Blockchain blockchain;
    private HashMap<ServerInfo, Date> serverStatus;

    public BlockchainServerRunnable(Socket clientSocket, Blockchain blockchain, HashMap<ServerInfo, Date> serverStatus) {
        this.clientSocket = clientSocket;
        this.blockchain = blockchain;
        this.serverStatus = serverStatus;
    }

    public void run() {
        try {
            serverHandler(clientSocket.getInputStream(), clientSocket.getOutputStream());
            clientSocket.close();
        } catch (IOException e) {
        }
    }

    public void serverHandler(InputStream clientInputStream, OutputStream clientOutputStream) {

        BufferedReader inputReader = new BufferedReader(
                new InputStreamReader(clientInputStream));
        PrintWriter outWriter = new PrintWriter(clientOutputStream, true);
        String localIp = (((InetSocketAddress) clientSocket.getLocalSocketAddress()).getAddress()).toString().replace("/", "");
        String remoteIP = (((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress()).toString().replace("/", "");
        try {
            while (true) {
                String inputLine = inputReader.readLine();
                if (inputLine == null) {
                    break;
                }

                String[] tokens = inputLine.split("\\|");
                switch (tokens[0]) {
                    case "tx":
                        if (blockchain.addTransaction(inputLine))
                            outWriter.print("Accepted\n\n");
                        else
                            outWriter.print("Rejected\n\n");
                        outWriter.flush();
                        break;
                    case "pb":
                        outWriter.print(blockchain.toString() + "\n");
                        outWriter.flush();
                        break;
                    case "cc":
                        return;
                    case "hb":
                    	ServerInfo p = new ServerInfo(remoteIP, Integer.parseInt(tokens[1]));
                    	synchronized(serverStatus) {
                    		if(!serverStatus.containsKey(p) || tokens[2].equals("0")) {
                    			broadcast(p);
                    			serverStatus.put(p, new Date());	
                    		}                 		
                    	}
                    	break;
                    case "si":
                    	synchronized(serverStatus) {
                    		p = new ServerInfo(tokens[2], Integer.parseInt(tokens[3]));
                        	ServerInfo origin = new ServerInfo(remoteIP, Integer.parseInt(tokens[1]));
                        	if(!serverStatus.containsKey(p)) {
                        		relay(p, origin);
                            	serverStatus.put(p, new Date());
                        	}
                    	}
                    	break;
                    case "lb":
                    	
                    	break;
                    default:
                        outWriter.print("Error\n\n");
                        outWriter.flush();
                }
                //to remove error on catching Interrupted Exception
                Thread.sleep(1);
            }
        } catch (IOException e) {
        } catch (InterruptedException e) {
        }
    }
    
    public void broadcast(ServerInfo p) {
    	LinkedList<Thread> threads = new LinkedList<>();
    	for(ServerInfo servers : serverStatus.keySet()) {
    		threads.add(new Thread(new Runnable() {
    			@Override
    			public void run() {
    				try {
    		            Socket toServer = new Socket();
    		            toServer.connect(new InetSocketAddress(servers.getHost(), servers.getPort()), 2000);
    		            PrintWriter printWriter = new PrintWriter(toServer.getOutputStream(), true);
    		            printWriter.println(String.format("si|%d|%s|%d", clientSocket.getLocalPort(), p.getHost(), p.getPort()));
    		            printWriter.flush();
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
    
    public void relay(ServerInfo p, ServerInfo o) {
    	LinkedList<Thread> threads = new LinkedList<>();
    	for(ServerInfo servers : serverStatus.keySet()) {
    		if(servers.equals(o) || servers.equals(p))
    			continue;
    		threads.add(new Thread(new Runnable() {
    			@Override
    			public void run() {
    				try {
    		            // create socket with a timeout of 2 seconds
    		            Socket toServer = new Socket();
    		            toServer.connect(new InetSocketAddress(servers.getHost(), servers.getPort()), 2000);
    		            PrintWriter printWriter = new PrintWriter(toServer.getOutputStream(), true);

    		            // send the message forward
    		            printWriter.println(String.format("si|%d|%s|%d", clientSocket.getLocalPort(), p.getHost(), p.getPort()));
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

}
