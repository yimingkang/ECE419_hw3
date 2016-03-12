import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class NamingServer {
    private static final int MAX_CLIENTS = 2;
    private static Socket[] mSocketList = null; //A list of MSockets
    private static MPacket[] helloPackets = null;

    public static void main(String args[]) throws IOException, ClassNotFoundException{
        int port = Integer.parseInt(args[0]);
        System.out.println("Starting naming server on port " + port);
        mSocketList = new Socket[MAX_CLIENTS];
        helloPackets = new MPacket[MAX_CLIENTS];
        
        int clientCount = 0;
        ServerSocket sSocket = new ServerSocket(port);

        for (clientCount = 0; clientCount < MAX_CLIENTS; clientCount++){
        	Socket client = sSocket.accept();
        	System.out.println("Client connected! # " + (clientCount + 1));
        	
        	// save the socket for later use
        	mSocketList[clientCount] = client;
        	ObjectInputStream reader = new ObjectInputStream(client.getInputStream());
        	
        	// first read the hello packet
        	MPacket helloPacket = (MPacket) reader.readObject();
        	helloPackets[clientCount] = helloPacket;
        }
        handleHello();
        sSocket.close();
    }
    
    
   public static void handleHello(){
        
        //The number of players
        int playerCount = mSocketList.length;
        Random randomGen = null;
        Player[] players = new Player[playerCount];
        MPacket hello = null;
        
        int socketBasePort = 8000;
        int nextAssignedPort = 8000;
        
        try{        
        	System.out.println("Generating player and player positions");
            for(int i=0; i<playerCount; i++){
                hello = helloPackets[i];
                
                //Sanity check 
                if(hello.type != MPacket.HELLO){
                    throw new InvalidObjectException("Expecting HELLO Packet");
                }
                
                if(randomGen == null){
                   randomGen = new Random(hello.mazeSeed); 
                }
                //Get a random location for player
                Point point =
                    new Point(randomGen.nextInt(hello.mazeWidth),
                          randomGen.nextInt(hello.mazeHeight));
                
                //Start them all facing North
                Player player = new Player(hello.name, point, Player.North);
                players[i] = player;
            }
            
            hello.event = MPacket.HELLO_RESP;
            hello.players = players;

            for(int i=0; i<playerCount; i++){
            	Socket client = mSocketList[i];
            	hello.inPort = nextAssignedPort;
            	nextAssignedPort++;
            	
            	System.out.println("Sending to player " + i);
            	
	        	if(i == playerCount - 1){
	        		// last one wraps around, last one is the token holder
	        		hello.outPort = socketBasePort;
	        		hello.isTokenHolder = true;
	        	}else{
	        		hello.outPort = nextAssignedPort;
	        		hello.isTokenHolder = false;
	        	}
	        	
            	ObjectOutputStream writer = new ObjectOutputStream(client.getOutputStream());
                writer.writeObject((Object) hello);
            }  
        }catch(IOException e){
            e.printStackTrace();
        }
    }

}
