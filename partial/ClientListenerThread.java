import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Hashtable;

public class ClientListenerThread implements Runnable {

    private MSocket mSocket  =  null;
    private Hashtable<String, Client> clientTable = null;

    public ClientListenerThread( MSocket mSocket,
                                Hashtable<String, Client> clientTable){
        this.mSocket = mSocket;
        this.clientTable = clientTable;
        if(Debug.debug) System.out.println("Instatiating ClientListenerThread");
    }

    public void run() {
        MPacket received = null;
        Client client = null;
        if(Debug.debug) System.out.println("Starting ClientListenerThread");
        while(true){
            try{
                received = (MPacket) mSocket.readObject();
                System.out.println("Received " + received);
                client = clientTable.get(received.name);
                if(received.event == MPacket.UP){
                    client.forward();
                }else if(received.event == MPacket.DOWN){
                    client.backup();
                }else if(received.event == MPacket.LEFT){
                    client.turnLeft();
                }else if(received.event == MPacket.RIGHT){
                    client.turnRight();
                }else if(received.event == MPacket.FIRE){
                    client.fire();
                }else{
                    throw new UnsupportedOperationException();
                }    
            }catch(IOException e){
                e.printStackTrace();
            }catch(ClassNotFoundException e){
                e.printStackTrace();
            }            
        }
    }
}
