import java.io.InvalidObjectException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.Random;

public class ServerSenderThread implements Runnable {

    //private ObjectOutputStream[] outputStreamList = null;
    private MSocket[] mSocketList = null;
    private BlockingQueue eventQueue = null;
    private int globalSequenceNumber; 
    
    public ServerSenderThread(MSocket[] mSocketList,
                              BlockingQueue eventQueue){
        this.mSocketList = mSocketList;
        this.eventQueue = eventQueue;
        this.globalSequenceNumber = 0;
    }

    /*
     *Handle the initial joining of players including 
      position initialization
     */
    public void handleHello(){
        
        //The number of players
        int playerCount = mSocketList.length;
        Random randomGen = null;
        Player[] players = new Player[playerCount];
        if(Debug.debug) System.out.println("In handleHello");
        MPacket hello = null;
        try{        
            for(int i=0; i<playerCount; i++){
                hello = (MPacket)eventQueue.take();
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
            //Now broadcast the HELLO
            if(Debug.debug) System.out.println("Sending " + hello);
            for(MSocket mSocket: mSocketList){
                mSocket.writeObject(hello);   
            }
        }catch(InterruptedException e){
            e.printStackTrace();
            Thread.currentThread().interrupt();    
        }catch(IOException e){
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }
    
    public void run() {
        MPacket toBroadcast = null;
        
        handleHello();
        
        while(true){
            try{
                //Take packet from queue to broadcast
                //to all clients
                toBroadcast = (MPacket)eventQueue.take();
                //Tag packet with sequence number and increment sequence number
                toBroadcast.sequenceNumber = this.globalSequenceNumber++;
                if(Debug.debug) System.out.println("Sending " + toBroadcast);
                //Send it to all clients
                for(MSocket mSocket: mSocketList){
                    mSocket.writeObject(toBroadcast);
                }
            }catch(InterruptedException e){
                System.out.println("Throwing Interrupt");
                Thread.currentThread().interrupt();    
            }
            
        }
    }
}
