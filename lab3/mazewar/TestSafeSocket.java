import java.io.IOException;

public class TestSafeSocket {
	public TestSafeSocket(){
		// Create a SafeSocket
	}
	
	public static void test(String name) throws IOException, InterruptedException, ClassNotFoundException{
        MPacket hello = new MPacket(name, MPacket.HELLO, MPacket.HELLO_INIT);
        hello.mazeWidth = 100;
        hello.mazeHeight = 200;
        
        SafeSocket sSock = null;
        
        //Receive response from server
		System.out.println("This thread has no token, waiting");
		sSock = new SafeSocket(name, "localhost", 7777, hello);
		
		sSock.start();
		int seq = 0;
		while(true){
			MPacket helloMessage = new MPacket(name, 100, 100);
			helloMessage.sequenceNumber = seq++;
			sSock.writeObject(helloMessage);
			
			Thread.sleep(1000);
		}
	}

	public static void main (String[] args) throws IOException, NumberFormatException, InterruptedException, ClassNotFoundException {
		// test functions
		// process 1	
				
		System.out.println("Starting " + args[0]);
		TestSafeSocket.test(args[0]);
	}
}
