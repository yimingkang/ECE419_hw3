import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class NamingServer {
    private static final int MAX_CLIENTS = 4;

    public static void main(String args[]) throws IOException{
        int port = Integer.parseInt(args[0]);
        System.out.println("Starting naming server on port " + port);
        int socketBasePort = 8000;
        int nextAssignedPort = 8000;
        
        int clientCount = 0;
        ServerSocket sSocket = new ServerSocket(port);
        ObjectOutputStream writer;

        for (clientCount = 0; clientCount < NamingServer.MAX_CLIENTS; clientCount++){
        	Socket client = sSocket.accept();
        	System.out.println("Client connected! # " + (clientCount + 1));

        	try{
	        	writer = new ObjectOutputStream(client.getOutputStream());
	        	
	        	// input port
	        	writer.writeObject((Object) nextAssignedPort);
	        	nextAssignedPort++;
	        	
	        	if(clientCount == NamingServer.MAX_CLIENTS - 1){
	        		// last one wraps around
	            	writer.writeObject((Object) socketBasePort);
	        	}else{
	            	writer.writeObject((Object) nextAssignedPort);
	        	}
	        	Thread.sleep(5000);
        	} catch (IOException e) {
        		System.out.println("Socket closed");
            } catch (InterruptedException e) {
				e.printStackTrace();
			}
        }
        sSocket.close();
    }

}
