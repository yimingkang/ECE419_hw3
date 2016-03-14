import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.net.ServerSocket;
import java.io.IOException;

public class MServerSocket{
    /*
    * This is the serverSocket equivalent to 
    * MSocket
    */
 
    private ServerSocket serverSocket = null;
    public BlockingQueue<Boolean> bq = null;
    
    /*
     *This creates a server socket
     */    
    public MServerSocket(int port, BlockingQueue<Boolean> bq) throws IOException{
        serverSocket = new ServerSocket(port);
        this.bq = bq;
    }
    
    public MSocket accept() throws IOException{
        Socket socket = serverSocket.accept(); 
        MSocket mSocket = new MSocket(socket, this.bq);
        return mSocket;
    }

}
