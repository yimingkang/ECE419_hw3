import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SafeSocket {
	public String myName;
	public int inPort;
	
	private BlockingQueue<MPacket> writeBuffer;
	private BlockingQueue<MPacket> readBuffer;
	
	public SafeSocket(String name, int serverPort, boolean tokenHolder) throws IOException, ClassNotFoundException{

		this.myName = name;
		this.writeBuffer = new LinkedBlockingQueue<MPacket>();
		this.readBuffer = new LinkedBlockingQueue<MPacket>();
		
		// contact naming server to get in and output port
		System.out.println("Contacting naming server to get inPort and outPort");
		Socket namingServer = new Socket("localhost", serverPort);
		
		// read 2 integers and close the socket
		ObjectInputStream reader = new ObjectInputStream(namingServer.getInputStream());

		int inPort = (Integer) reader.readObject();

        int outPort = (Integer) reader.readObject();
	    
	    System.out.println("First line is: " + inPort);
	    System.out.println("Second line is " + outPort);
	    
	    namingServer.close();
	    
	    System.out.println("Naming server returned inPort:" + inPort + ", outPort" + outPort);
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
