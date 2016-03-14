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
                //System.out.println("Got ack #" + received.ackNum);
                
                if (received.event != MPacket.ACK){
                	System.out.println(Thread.currentThread().getName()+"SenderAck: ERROR: Expecting packet type ACK, got " + received.event + " instead");
                	System.exit(-1);
                }
                
                // Notify sender of ACK
                SafeSocketSenderThread.updateAck(received.ackNum);
                
            }catch(Exception e){
            	System.out.println(Thread.currentThread().getName()+"SenderAck: Not so gracefully exit!");
            	this.mSocket.close();
            	return;
            }         
        }
	}

}
