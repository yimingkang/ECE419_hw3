import java.io.IOException;

public class SafeSocketSenderAckThread implements Runnable{
	public MSocket mSocket;
	
	public SafeSocketSenderAckThread(MSocket socket){
		this.mSocket = socket;
	}
	
	public void run(){
        MPacket received = null;
        
        while(true){
            try{
                received = (MPacket) mSocket.readObject();  
                if (received.event != MPacket.ACK){
                	System.out.println("ERROR: Expecting packet type ACK, got " + received.event + " instead");
                	System.exit(-1);
                }
                
                // Notify sender of ACK
                SafeSocketSenderThread.updateAck(received.ackNum);
                
            }catch(IOException e){
                e.printStackTrace();
            }catch(ClassNotFoundException e){
                e.printStackTrace();
            }            
        }
	}

}
