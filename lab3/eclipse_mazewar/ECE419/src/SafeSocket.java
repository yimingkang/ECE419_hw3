import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SafeSocket {
	public String myName;
	public int inPort;
	
	private BlockingQueue<MPacket> writeBuffer;
	private BlockingQueue<MPacket> readBuffer;
	
	public SafeSocket(String name, int inPort, int outPort, boolean tokenHolder) throws IOException{

		this.myName = name;
		this.writeBuffer = new LinkedBlockingQueue<MPacket>();
		this.readBuffer = new LinkedBlockingQueue<MPacket>();
		
		System.out.println("Starting SenderThread " + name);
		
		// have Sender thread connect to socket himself ==> impl retry!
		new Thread(new SafeSocketSenderThread("localhost", outPort, this.writeBuffer, tokenHolder)).start();
		
		
		System.out.println("Starting ListenerThread " + name);
		new Thread(new SafeSocketListenerThread(name, inPort, this.readBuffer)).start();
	}
	
	
	// API confronting to MSocket
	public void writeObject(MPacket packet){
		//System.out.println("writeObject got a packet, writing...");
		this.writeBuffer.add(packet);
	}
	
	// API confronting to MSocket
	public MPacket readObject() throws InterruptedException{
		return this.readBuffer.take();
	}
}
