import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class SafeSocketSenderThread implements Runnable {
	public int sequenceNumber=1;
	public static AtomicInteger currentAck = new AtomicInteger(0);
	public MSocket mSocket=null;
	public BlockingQueue<MPacket> packetQueue;
	public static boolean hasToken = false;
	public static MPacket currentToken;
	public String host;
	public int outPortIndex;
	public int [] Ports;
	public BlockingQueue<Boolean> networkErrorQueue = null;
	
	public SafeSocketSenderThread(String host, int[] Ports, int outPortIndex, BlockingQueue<MPacket> packets, boolean tokenHolder){
		/*** Place all outbound MPackets in the blocking queue ***/

		this.host = host;
		this.outPortIndex = outPortIndex;
		this.Ports = Ports;
		this.packetQueue = packets;
		this.networkErrorQueue = new LinkedBlockingQueue<Boolean>();
		if (tokenHolder){
			System.out.println("This thread is a token holder, initializing with a token");
			// initialize with a token!
			SafeSocketSenderThread.currentToken = new MPacket();
			SafeSocketSenderThread.hasToken = true;
		}
	}
	
	public static boolean offerToken(MPacket token){
		// TODO: FIXME: make concurrent
		if (SafeSocketSenderThread.currentToken != null || SafeSocketSenderThread.hasToken){
			System.out.println("WARNING: offering token when there's a token already!");
			return false;
		}
		// gets the TOKEN
		SafeSocketSenderThread.currentToken = token;
		SafeSocketSenderThread.hasToken = true;
		return true;
	}
	
	public MPacket prepareToken(){
		// wait till we get a token
		while (!SafeSocketSenderThread.hasToken){
			System.out.println("Waiting for a token...");
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Got token!");
		
        MPacket toDownstream = SafeSocketSenderThread.currentToken;
        
        // transfer all MPackets onto token
        int nMessages = this.packetQueue.size();
    	for (int i = 0; i < nMessages; i++){
    		MPacket msg = null;
			try {
				msg = this.packetQueue.take();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    		toDownstream.addPacket(msg);
    	}

        // set sequence number
        toDownstream.sequenceNumber = this.sequenceNumber;
        
        // TODO: FIXME:
        
        /* 
         * Network delay MIGHT cause this token to loop around the ring before
         * an ACK is received. Therefore we need to make sure to clear 
         * this before any attempt to writeObject() is made, otherwise 
         * offerToken() will fail
         * 
         * This is very important!! 
         */
        SafeSocketSenderThread.currentToken = null;
    	SafeSocketSenderThread.hasToken = false;
        
        // off you go!
        return toDownstream;
	}
	
    public void run() {
		System.out.println("SafeSocketSenderThread starting...");
		// create socket and start sender thread
		while (this.mSocket == null){
			// sleep 0.5s until a connection can be made
			try {
				this.mSocket = new MSocket(this.host, this.Ports[this.outPortIndex], networkErrorQueue);
			} catch (IOException e) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}
		
		// Start a thread to handle ACKs
		new Thread(new SafeSocketSenderAckThread(this.mSocket)).start();
		MPacket toDownstream;
		
        while(true){
            try{                
                // Wait until we have a token to send
            	toDownstream = this.prepareToken();
        		
                // keep sending unless ACKed
        		//System.out.println("1- currentAck is " + SafeSocketSenderThread.currentAck + ", seqN is " + this.sequenceNumber );
            	
        		while (SafeSocketSenderThread.currentAck.get() != this.sequenceNumber){
        			System.out.println(Thread.currentThread().getName()+"Sender: inside while loop");
        			
        			try{
	            		if (SafeSocketSenderThread.currentAck.get() > this.sequenceNumber){
	            			System.out.println(Thread.currentThread().getName()+"Sender: ERROR: SEQUENCE NUMBER TOO BIG");
	            			System.exit(-1);
	            		}
	                	//System.out.println("Sending TOKEN #" + toDownstream.sequenceNumber);
	        			System.out.println(Thread.currentThread().getName()+"Sender: writeObject(toDownstream)");
	                    
	        			mSocket.writeObject(toDownstream);
	                    
	                    if (this.networkErrorQueue.size() == 1){
	                    	System.out.println(Thread.currentThread().getName()+"Sender: Exactly one event in errorqueue");
	                    	this.networkErrorQueue.take();
	                    	throw new Exception();
	                    }else if(this.networkErrorQueue.size() > 1){
	                    	System.out.println(Thread.currentThread().getName()+"Sender: DNE, fuck you");
	                    	System.exit(-1);
	                    }
	
	                    // re-send every 100ms
	                    Thread.sleep(1000);
	                    System.out.println(Thread.currentThread().getName()+"Sender:Checking for ack or resending");
        			}catch(Exception e){
        			   	this.outPortIndex = (this.outPortIndex + 1) % this.Ports.length;
                    	System.out.println(Thread.currentThread().getName()+"Sender: Trying to connect to new upstream " + this.Ports[this.outPortIndex]);
                    	this.mSocket.close();
                    	this.mSocket = null;
                		while (this.mSocket == null){
                			// sleep 0.1s until a connection can be made
            				this.networkErrorQueue = new LinkedBlockingQueue<Boolean>();
                			try {
                				this.mSocket = new MSocket(this.host, this.Ports[this.outPortIndex], this.networkErrorQueue);
                			} catch (IOException e1) {
                				try {
                					Thread.sleep(100);
                				} catch (InterruptedException e2) {
                					e1.printStackTrace();
                				}
                			}
                		}
                		// Start a thread to handle ACKs
                		new Thread(new SafeSocketSenderAckThread(this.mSocket)).start();
                		System.out.println(Thread.currentThread().getName()+"Sender: Connected to upstream!");
                		
                		// resend the current token
                		this.offerToken(toDownstream);
                		SafeSocketSenderThread.currentAck.getAndSet(0);
        			}
            	}
            	this.sequenceNumber++;
        		System.out.println(Thread.currentThread().getName()+"Sender: Out of while loop currentAck is " + SafeSocketSenderThread.currentAck + ", seqN is " + this.sequenceNumber );
            }catch(Exception e){
         
            }
            
        }
    }
	
	public static void updateAck(int ackNum){
		SafeSocketSenderThread.currentAck.getAndSet(Math.max(ackNum, SafeSocketSenderThread.currentAck.get()));
		System.out.println("Updating ACK to " + SafeSocketSenderThread.currentAck);
	}

}
