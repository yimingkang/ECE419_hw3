import java.io.Serializable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MPacket implements Serializable {

    /*The following are the type of events*/
    public static final int HELLO = 100;
    public static final int ACTION = 200;

    /*The following are the specific action 
    for each type*/
    /*Initial Hello*/
    public static final int HELLO_INIT = 101;
    /*Response to Hello*/
    public static final int HELLO_RESP = 102;

    /*Action*/
    public static final int UP = 201;
    public static final int DOWN = 202;
    public static final int LEFT = 203;
    public static final int RIGHT = 204;
    public static final int FIRE = 205;
    
    // ACK
    public static final int ACK = 206;
    
    // TOKEN
    public static final int TOKEN = 207;

    // fix for projectile
    public static final int UPDATE_PROJECTILE = 208;

    
    //These fields characterize the event  
    public int type;
    public int event; 

    //The name determines the client that initiated the event
    public String name;
    
    //The sequence number of the event
    public int sequenceNumber;

    //These are used to initialize the board
    public int mazeSeed;
    public int mazeHeight;
    public int mazeWidth; 
    public Player[] players;
    
    // ack number
    public int ackNum;
    
    // in/out ports
    public int inPort;
    public int outPort;
    public boolean isTokenHolder;
    
    // event queue
    public BlockingQueue<MPacket> eventQueue = null;
    
    public MPacket(){
    	// TOKEN
    	this.eventQueue = new LinkedBlockingQueue<MPacket>();
    	this.type = 300;
    	this.event = MPacket.TOKEN;
    }
    
    public void addPacket(MPacket m){
    	if (this.eventQueue == null){
    		System.out.println("ERROR: Uninitialized eventQueue!");
    		System.exit(-1);
    	}
    	this.eventQueue.add(m);
    }

    
    public void removeMyMessages(String name){
    	if (this.eventQueue == null){
    		System.out.println("ERROR: Uninitialized eventQueue!");
    		System.exit(-1);
    	}
    	while(!this.eventQueue.isEmpty() && this.eventQueue.peek().name.equals(name)){
    		try {
				this.eventQueue.take();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    }
 
    
    public MPacket(int type, int event){
        this.type = type;
        this.event = event;
    }
    
    public MPacket(String name, int type, int event){
        this.name = name;
        this.type = type;
        this.event = event;
    }
    
    public MPacket (int ack){
    	// the special ACK constructor
    	this.ackNum = ack;
    	this.type = 100;
    	this.event = MPacket.ACK;
    }
    
    public String toString(){
        String typeStr;
        String eventStr;
        
        switch(type){
            case 100:
                typeStr = "HELLO";
                break;
            case 200:
                typeStr = "ACTION";
                break;
            case 300:
                typeStr = "TOKEN";
                break;
            default:
                typeStr = "ERROR";
                break;        
        }
        switch(event){
            case 101:
                eventStr = "HELLO_INIT";
                break;
            case 102:
                eventStr = "HELLO_RESP";
                break;
            case 201:
                eventStr = "UP";
                break;
            case 202:
                eventStr = "DOWN";
                break;
            case 203:
                eventStr = "LEFT";
                break;
            case 204:
                eventStr = "RIGHT";
                break;
            case 205:
                eventStr = "FIRE";
                break;
            case 206:
                eventStr = "ACK";
                break;
            case 207:
                eventStr = "TOKEN";
                break;
            case 208:
                eventStr = "UPDATE_PROJECTILE";
                break;
            default:
                eventStr = "ERROR";
                break;        
        }
        //MPACKET(NAME: name, <typestr: eventStr>, SEQNUM: sequenceNumber)
        String retString = String.format("MPACKET(NAME: %s, <%s: %s>, SEQNUM: %s)", name, 
            typeStr, eventStr, sequenceNumber);
        return retString;
    }

}
