import java.util.concurrent.BlockingQueue;
import java.io.IOException;
import java.util.PriorityQueue;

public class RingNode {
	public final String name;
	public MSocket senderSocket, receiverSocket;
	public BlockingQueue<MPacket> sendQueue, receiveQueue;
	public int inputPort, outputPort;
	public PriorityQueue<MPacket> reorderQueue; 
	
	
	public RingNode(String name){
		this.name = name;
        //new Thread(new SafeSocketSenderThread(mSocketList, eventQueue)).start();
        //new Thread(new SafeSocketListenerThread(mSocketList, eventQueue)).start();    

	}
	
	public void writeObject(MPacket packet){
		// 1- write to sendQueue
		// 2- return
		this.sendQueue.add(packet);
		return;
	}
	
    public synchronized MPacket readObject() throws IOException, ClassNotFoundException{
    	
    	return  (MPacket) new Object();	
	}

	

}
