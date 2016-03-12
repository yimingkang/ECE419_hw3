import java.io.IOException;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;

public class SafeSocketListenerThread implements Runnable {
    private MSocket mSocket;
    private int nextExpected = 0;
    public SafeSocketSenderThread senderThread;
    private PriorityQueue<MPacket> packetPriorityQueue = 
    		new PriorityQueue<MPacket>(50, new packetSequenceComparator());
    public BlockingQueue<MPacket> orderdOutputQueue;
    public String myName;
	
    
    public class packetSequenceComparator implements Comparator<MPacket>
    {
        @Override
        public int compare(MPacket x, MPacket y)
        {
            return x.sequenceNumber - y.sequenceNumber;
        }
    }
    
    /* TODO FIXME move this to the ACK thread of SafeSocketSender

    if (received.event == MPacket.ACK){
    	// this is an ACK to a previously sent token, notify sender
    	this.notifySenderAck(received.ackNum);
    	continue;
    }

    public void notifySenderAck(int ackNum){
    	this.senderThread.updateAck(ackNum);
    }
    */

    public SafeSocketListenerThread(String name, MSocket socket, BlockingQueue<MPacket> q){
    	/* This thread fetches packets and replies with ACK
    	 * 
    	 */
        this.mSocket = socket;
        this.myName = name;
        this.orderdOutputQueue = q;
    }
    
    
    public void sendAck(int ackNum){
    	MPacket ackPacket = new MPacket(ackNum);
    	this.mSocket.writeObject(ackPacket);
    }
    
    public void processToken(MPacket token){
    	// 1- Fetch all packets off token
    	for(MPacket msg: token.eventQueue){
    		try {
				this.orderdOutputQueue.put(msg);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	
    	// 2- Delete everything mine from the head
    	token.removeMyMessages(this.myName);
    	
    	// 3- Offer token to sender thread
    	SafeSocketSenderThread.offerToken(token);
    }

    
    public void run() {
        MPacket received = null;
        
        while(true){
            try{
                received = (MPacket) mSocket.readObject();
                
                // ACK on all read objects
                this.sendAck(received.sequenceNumber);
                
                // correct the OO packets with a PQ
                this.packetPriorityQueue.offer(received);
                while (this.packetPriorityQueue.size() != 0 && this.packetPriorityQueue.peek().sequenceNumber == this.nextExpected){
                    this.nextExpected++;
                    received = this.packetPriorityQueue.poll();
                    System.out.println("Processing packet #" + received.sequenceNumber);
                    
                    // next packet goes straight to the output queue, packets here are ORDERED!!
                    this.processToken(received);
                }
            }catch(IOException e){
                e.printStackTrace();
            }catch(ClassNotFoundException e){
                e.printStackTrace();
            }            
        }
    }
}
