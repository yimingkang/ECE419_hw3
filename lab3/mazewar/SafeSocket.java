import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SafeSocket {
	public String myName;
	public String serverHost;
	public MPacket helloPacket;
	public int inPort;
	public int outPort;
	public boolean tokenHolder;
	
	private BlockingQueue<MPacket> writeBuffer;
	private BlockingQueue<MPacket> readBuffer;
	
	public SafeSocket(String name, String serverHost, int serverPort, MPacket hello) throws IOException, ClassNotFoundException{

		this.myName = name;
		this.writeBuffer = new LinkedBlockingQueue<MPacket>();
		this.readBuffer = new LinkedBlockingQueue<MPacket>();
		this.serverHost = serverHost;
		
		// contact naming server to get in and output port
		System.out.println("Contacting naming server to get inPort and outPort");
		Socket namingServer = new Socket(serverHost, serverPort);
		
		// send hello
		ObjectOutputStream writer = new ObjectOutputStream(namingServer.getOutputStream());
		writer.writeObject((Object) hello); 
		
		// read hello response
		ObjectInputStream reader = new ObjectInputStream(namingServer.getInputStream());
		this.helloPacket = (MPacket) reader.readObject();
		
		// get the info we need off MPacket
		this.inPort = this.helloPacket.inPort;
		this.outPort = this.helloPacket.outPort;
		this.tokenHolder = this.helloPacket.isTokenHolder;
		
		System.out.format("Got naming server response with inPort=%d, outPort=%d\n", this.inPort, this.outPort);
	    namingServer.close();	    
	}
	
	public void start(){
		System.out.println("Starting SenderThread " + this.myName);
		
		// have Sender thread connect to socket himself ==> impl retry!
		new Thread(new SafeSocketSenderThread("localhost", this.outPort, this.writeBuffer, this.tokenHolder)).start();
		
		System.out.println("Starting ListenerThread " + this.myName);
		new Thread(new SafeSocketListenerThread(this.myName, inPort, this.readBuffer)).start();
	}
	
	public MPacket getHelloResponse(){
		return this.helloPacket;
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
