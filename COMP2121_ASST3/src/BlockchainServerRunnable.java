import java.io.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.LinkedList;

public class BlockchainServerRunnable implements Runnable{

    private Socket clientSocket;
    private Blockchain blockchain;
    private ConcurrentHashMap<ServerInfo, Date> serverStatus;

    public BlockchainServerRunnable(Socket clientSocket, Blockchain blockchain, ConcurrentHashMap<ServerInfo, Date> serverStatus) {
        this.clientSocket = clientSocket;
        this.blockchain = blockchain;
        this.serverStatus = serverStatus;
    }

    @Override
    public void run() {
        try {
            serverHandler(clientSocket.getInputStream(), clientSocket.getOutputStream());
            clientSocket.close();
        } catch (IOException e)  {

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
                		if(!serverStatus.containsKey(p) || tokens[2].equals("0")) {
                			broadcast(p);
                			serverStatus.put(p, new Date());
                		}
                    	break;
                    case "si":
                		p = new ServerInfo(tokens[2], Integer.parseInt(tokens[3]));
                    	ServerInfo origin = new ServerInfo(remoteIP, Integer.parseInt(tokens[1]));
                    	if(!serverStatus.containsKey(p)) {
                    		relay(p, origin);
                        	serverStatus.put(p, new Date());
                    	}
                    	break;
                    case "cu":
                    	ObjectOutputStream output = new ObjectOutputStream(clientSocket.getOutputStream());
                		if(tokens.length == 1) {
                			output.writeObject(blockchain.getHead());
                    	} else {
                    		synchronized(blockchain) {
	                    		Block find = blockchain.getHead();
	                    		//relies on sender to not ask for hash that isn't in the block
	                    		while(!base64(find.calculateHash()).equals(tokens[1])) {
	                    			if(base64(find.getPreviousHash()).equals(base64(new byte[32])))
	                    				break;
	                    			find = find.getPreviousBlock();
	                    		}
	                    		output.writeObject(find);
                    		}
                    	}
                		output.flush();
                    	return;
                    case "lb":
                    	synchronized(blockchain) {
                    		naiveCatchUp(inputLine);
                    	}
                    	break;
                    default:
                        outWriter.print("Error\n\n");
                        outWriter.flush();
                }

            }
        } catch (IOException e) {

        }
    }

    public void broadcast(ServerInfo p) {
    	LinkedList<Thread> threads = new LinkedList<>();
    	for(ServerInfo servers : serverStatus.keySet()) {
    		if(!servers.equals(p)) {
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

    public void naiveCatchUp(String line) {
    	String[] token = line.split("[|]");
    	int port = Integer.parseInt(token[1]);
    	int size = Integer.parseInt(token[2]);
    	String hash = token[3];
    	
    	//catch up not needed
		if(blockchain.getLength() > size)
    		return;
		//CASE 2 OF CATCH UP
    	if(blockchain.getLength() < size) {
    		Block head = getBlock(hash, port);
    		if(size == 1) {
    			blockchain.setHead(head);
    			blockchain.setLength(1);
    			return;
    		}
    		if(blockchain.getLength() == 0) {
    			Block current = head;
        		while(!base64(current.getPreviousHash()).equals(base64(new byte[32]))) {
        			current.setPreviousBlock(getBlock(base64(current.getPreviousHash()), port));
        			current = current.getPreviousBlock();
        		}
    		} else {
    			Block current = head;
        		while(!base64(current.getPreviousHash()).equals(base64(blockchain.getHead().calculateHash()))) {
        			current.setPreviousBlock(getBlock(base64(current.getPreviousHash()), port));
        			current = current.getPreviousBlock();
        		}
    		}


    		blockchain.setHead(head);
    		blockchain.setLength(size);	
    		return;
    	}
    	//catch up needed
    	if(size == 0)
    		return;

    	Block newHead = getBlock(hash, port);
    	Block oldHead = blockchain.getHead();
    	//if the block is the same
    	if(base64(oldHead.calculateHash()).equals(base64(newHead.calculateHash())))
    		return;
    	//CASE 3 OF CATCH UP
    	System.out.println("point");
    	if(compareHash(oldHead.calculateHash(), newHead.calculateHash())) {
    		System.out.println("decided");
    		ArrayList<Transaction> solid = newHead.getTransactions();
    		ArrayList<Transaction> old = blockchain.getPool();
    		old.addAll(oldHead.getTransactions());
    		for(Transaction t : solid)
    			old.remove(t);
    		blockchain.setHead(newHead);
    		newHead.setPreviousBlock(null);
    		newHead.setPreviousHash(new byte[32]);
    	} else {
    		System.out.println("not changed");
    		System.out.printf("%s\n%s\n%s\n", base64(blockchain.getHead().calculateHash()), 
    				base64(blockchain.getHead().getPreviousBlock().calculateHash()), base64(blockchain.getHead().getPreviousBlock().getPreviousHash()));
    	}
    }

    public Block getBlock(String hash, int port) {
    	String remoteIP = (((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress()).toString().replace("/", "");
    	Block ret = null;
    	try {
    		Socket socket = new Socket();
			socket.connect(new InetSocketAddress(remoteIP, port), 2000);

			PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			pw.println("cu|" + hash);
			pw.flush();
			ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
			ret = (Block) input.readObject();
			reader.close();
			input.close();
			socket.close();

    	} catch (IOException e) {

    	} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

    	return ret;
    }

    public static String base64(byte[] bytes) {
    	return Base64.getEncoder().encodeToString(bytes);
    }

    public static boolean compareHash(byte[] h1, byte[] h2) {
    	for(int i = 0; i < h1.length; ++i)
    		if(h1[i] != h2[i])
    			return h1[i] > h2[i];
    	return false;
    }

}
