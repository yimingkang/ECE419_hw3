import java.awt.LinearGradientPaint;
import java.io.IOException;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;

public class SafeSocketListenerThread implements Runnable {
    private MSocket mSocket;
    private int nextExpected = 1;
    public SafeSocketSenderThread senderThread;
    private PriorityQueue<MPacket> packetPriorityQueue = 
    		new PriorityQueue<MPacket>(50, new packetSequenceComparator());
    public BlockingQueue<MPacket> orderdOutputQueue;
    public String myName;
    private MServerSocket mServerSocket = null;
    public int inPortIndex;
    public int[] Ports;
    public Player[] players;
	public int highestTokenSeq=0;
	public BlockingQueue<Boolean> errorQueue = null;
    
    public class packetSequenceComparator implements Comparator<MPacket>
    {
        @Override
        public int compare(MPacket x, MPacket y)
        {
            return x.sequenceNumber - y.sequenceNumber;
        }
    }

    public SafeSocketListenerThread(String name, Player[] players, int [] Ports, int inPortIndex, BlockingQueue<MPacket> q){
    	/* This thread fetches packets from ordered tokens and places them in q
    	 * 
    	 */

    	this.inPortIndex = inPortIndex;
    	this.Ports = Ports;
        this.myName = name;
        this.orderdOutputQueue = q;
        this.players = players;
        this.errorQueue = new LinkedBlockingQueue<Boolean>();
    }
    
    
    public void sendAck(int ackNum) throws InterruptedException, IOException, ExecutionException{
    	//System.out.println("Sending ACK #" + ackNum);
    	MPacket ackPacket = new MPacket(ackNum);
    	this.mSocket.writeObject(ackPacket);
    }
    
    public void processToken(MPacket token){
    	// 1- Fetch all packets off token
    	for(MPacket msg: token.eventQueue){
    		try {
				this.orderdOutputQueue.put(msg);
				System.out.format(Thread.currentThread().getName()+"Listener: Msg became available <%s, #%d>\n", msg.name, msg.sequenceNumber);
			} catch (InterruptedException e) {
				System.out.println(Thread.currentThread().getName()+"Listener: fail to put msg to eventQueue");
				e.printStackTrace();
			}
    	}
    	
    	// 2- Delete everything mine from the head
    	//System.out.println("Removing all msgs with myName");
    	token.removeMyMessages(this.myName);
    	
    	//System.out.println("Offering token to sender, prev. token seq #" + token.sequenceNumber);
    	// 3- Offer token to sender thread
    	while(!SafeSocketSenderThread.offerToken(token)){
    		try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				System.out.println(Thread.currentThread().getName()+"Listener: fail to sleep");
				e.printStackTrace();
			}
    	}
    }

    
    public void run() {
		System.out.println("SafeSocketListenerThread starting...");
		// create a socket and start listener thread
		try {
			this.mServerSocket = new MServerSocket(this.Ports[this.inPortIndex], this.errorQueue);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        try {
			this.mSocket = this.mServerSocket.accept();
		} catch (IOException e) {
			e.printStackTrace();
		}
    	
        MPacket received = null;
        
        while(true){
            System.out.println(Thread.currentThread().getName()+"Listener: -----0");
            try{
                received = (MPacket) mSocket.readObject();
            	System.out.println(Thread.currentThread().getName()+"Listener: Got a packet #" + received.sequenceNumber);
                if(this.mServerSocket.bq.size() == 1){
                	System.out.println(Thread.currentThread().getName()+"Listener: Got something in errorQueue");
                	this.errorQueue.take();
                	throw new Exception();
                }else if (this.mServerSocket.bq.size() > 1){
                	System.out.println(Thread.currentThread().getName()+"Listener: DNE, fuck this");
                	System.exit(-1);
                }
                
                System.out.println(Thread.currentThread().getName()+"Listener: -----1");
                
                // check that we've got TOKEN object
                if (received.event != MPacket.TOKEN){
                	System.out.println(Thread.currentThread().getName()+"Listener: ERROR: Expecting TOKEN obj but got " + received.event + " instead");
                	System.exit(-1);
                }
                                
                // ACK on all read objects
                this.sendAck(received.sequenceNumber);
                
                // correct the OO packets with a PQ
                if (received.sequenceNumber >= this.nextExpected){
                	// discard duplicates...basically
                    this.packetPriorityQueue.offer(received);
                }
                
                System.out.println(Thread.currentThread().getName()+"Listener: -----2");

                while (this.packetPriorityQueue.size() != 0 && this.packetPriorityQueue.peek().sequenceNumber == this.nextExpected){
                    this.nextExpected++;
                    received = this.packetPriorityQueue.poll();
                    //System.out.println("Processing packet #" + received.sequenceNumber);
                    
                    // next packet goes straight to the output queue, packets here are ORDERED!!
                    this.processToken(received);
                }
                System.out.println(Thread.currentThread().getName()+"Listener: -----3");
            }catch(Exception e){
            	System.out.println(Thread.currentThread().getName()+"Listener: Caught exception -- waiting for someone to connect");
            	if (this.errorQueue.size() == 1){
    				try {
						this.errorQueue.take();
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
            	}else if (this.errorQueue.size() > 1){
            		System.out.println("WTF!!!!!!");
            		System.exit(-1);
            	}
            	
            	this.mSocket.close();
            	this.mSocket = null;
            	try {
					this.mServerSocket = new MServerSocket(this.Ports[this.inPortIndex], this.errorQueue);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
                try {
        			this.mSocket = this.mServerSocket.accept();
        			System.out.println(Thread.currentThread().getName()+"Listener: Someone connected!");
        		} catch (Exception eprime) {
        			System.out.println(Thread.currentThread().getName()+"Listener: Error within an error!!");
        			eprime.printStackTrace();
        		}
                // TODO: FIXME:
                // 1- Add death message to event queue
                // 2- ???
                // 3- profit
            }
        }
    }
}
