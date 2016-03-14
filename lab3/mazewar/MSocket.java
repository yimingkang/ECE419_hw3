import java.net.Socket;
import java.io.StreamCorruptedException;
import java.io.OptionalDataException;
import java.io.IOException;
import java.io.EOFException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.ArrayList;
import java.util.Random;
import java.util.Collections;
import java.util.HashMap;
import java.util.Date;

public class MSocket{
    /*
     * This class is used as a wrapper around sockets and streams.
     * In addition to allowing network communication,
     * it tracks statistics and can add delays and packet reordering.
     */

    /*************Constants*************/
    //DELAY_WEIGHT, DELAY_THRESHOLD determine the distribution
    //of the delays

    //The weight associated with the delay
    //this roughly corresponds with the mean delay in
    //milliseconds, when the delay is non-zero
    //This should be a value between [0, inf), with
    // 1000 being a good value
    //To disable delays, set to 0.0
    public final double DELAY_WEIGHT = 10.0;

    //This roughly corresponds to the likelihood
    //of any delay. This should be a value between [0, inf)
    //A higher value corresponds to a lower likelihood
    //for delay
    public final double DELAY_THRESHOLD = 0.0;

    //The degree of packet reordereding caused by the network
    //value should be between [0, 1]
    //0 means ordered, 1 means high degree of reordering
    public final double UNORDER_FACTOR = 0.2;

    //Probability of a drop
    //Should be between [0, 1)
    //0 means no drops
    //for a large number of drops set to >0.5
    //Packets are only droped on send
    public final double DROP_RATE = 0.1;

    //Number of milli seconds after this MSocket is created
    //that packets are transmitted without network errors
    public final long ERROR_FREE_TRANSMISSION_PERIOD = 30000; //30 seconds

    //To disable all network errors set:
    //DELAY_WEIGHT = 0, DELAY_THRESHOLD = 0, UNORDER_FACTOR = 0, DROP_RATE = 0
    //To induced a large degree of network errors set:
    //DELAY_WEIGHT = 100, DELAY_THRESHOLD = 0,UNORDER_FACTOR = 1, DROP_RATE = 0.6

    /*************Member objects for communication*************/
    private Socket socket = null;
    private ObjectInputStream in = null;
    private ObjectOutputStream out = null;

    /*************Member objects for other tasks*************/
    //For adding errors, like delays and packet reorders
    private Random random = null;

    //The queue of packets to send
    private BlockingQueue egressQueue = null;
    //The queue of packets received
    private BlockingQueue ingressQueue = null;

    private ExecutorService executor = null;

    //Counters for number packets sent or received
    private int rcvdCount;
    private int sentCount;
    private HashMap<PairKey<String, Integer>, Boolean> rcvdEvent;

    private long rcvdBytes;
    private long sentBytes;

    //Time of creation of this MSocket
    private Date creationTime;

    /*************Helper Classes*************/

    /*
    * The following class is used for measuring size of packets
    * being sent and received
    */
    static class Sizeof{
        public static int sizeof(Object obj) throws IOException{

            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);

            objectOutputStream.writeObject(obj);
            objectOutputStream.flush();
            objectOutputStream.close();

            return byteOutputStream.toByteArray().length;

        }
    }

    /*
     *The following inner class asynchronously
     *receives packets and adds it to the ingressQueue
     */
     class Receiver implements Runnable{

        public void run(){
            try{

                Object incoming = in.readObject();
                while(incoming != null){
                    MPacket in_packet = (MPacket)incoming;
                    PairKey<String, Integer> pk = new PairKey<String, Integer>(in_packet.name, in_packet.sequenceNumber);
                    if (in_packet.type == 200 && !rcvdEvent.containsKey(pk)) {
                        rcvdEvent.put(pk, Boolean.TRUE);
                    }

                    if(Debug.debug) System.out.println("\nNumber of packets received: " + ++rcvdCount);
                    int size = Sizeof.sizeof(incoming);
                    rcvdBytes += size;
                    if(Debug.debug) System.out.println("Received Packet size is " + size + ". Total bytes receieved is " + rcvdBytes);
                    if(Debug.debug) System.out.println("Received packet: " + incoming);
                    if(Debug.debug) System.out.println("Received Event size is " + rcvdEvent.size());
                    if(Debug.debug) System.out.println("Average packets per event is " + (double)rcvdCount / (double)rcvdEvent.size());
                    if(Debug.debug) System.out.println("Average traffic size per event:" + (double)rcvdBytes / (double)rcvdEvent.size() + "\n");
                    ingressQueue.put(incoming);

                    incoming = in.readObject();
                }
            }catch(StreamCorruptedException e){
                System.out.println(e.getMessage());
                e.printStackTrace();
            }catch(OptionalDataException e){
                System.out.println(e.getMessage());
                e.printStackTrace();
            }catch(EOFException e){
                e.printStackTrace();
                close();
                System.out.println("Exiting");
                // System.exit(0);
            }catch(IOException e){
                e.printStackTrace();
            }catch(ClassNotFoundException e){
                e.printStackTrace();
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }
     }

    /*
     *The following inner class sends packets by reordering them
     and adding a delay.
     There are two ways to do this: 1) when a thread wakes up, it
     sends ALL packets in an order determined by UNORDER_FACTOR;
     2) when a thread wakes up it sends ONE packet based on UNORDER_FACTOR.
     This implementation uses the former.
    */
    class NetworkErrorSender implements Runnable{

        public void run(){
            try{
                int delay = getDelay();
                Thread.sleep(delay);
                ArrayList events = new ArrayList();

                //Drain the entire egress queue into the events list
                Object head = egressQueue.poll();
                while(head != null){
                    events.add(head);
                    head = egressQueue.poll();
                }

                //Now reorder the events based on the UNORDER_FACTOR
                if(UNORDER_FACTOR == 0.0){
                    //no reordering
                }else if(UNORDER_FACTOR > 0.0 && UNORDER_FACTOR < 1.0){
                    //swap first two elements, if there are at least 2 elements
                    if(events.size() >= 2){
                        Object first = events.remove(0);
                        Object second = events.remove(0);
                        events.add(0, first);
                        events.add(0, second); //shifts first to index 1
                    }
                }else{ //UNORDER_FACTOR == 1.0
                    //randomly permute the events list
                    Collections.shuffle(events);
                }

                //Now send all the events
                while(events.size() > 0){
                    //Packet is "sent", drops happen at the network
                    //i.e. count it regardless of whether it will actually be sent
                    if(Debug.debug) System.out.println("Number of packets sent: " + ++sentCount);
                    //Need to synchronize on the ObjectOutputStream instance; otherwise
                    //multiple writes may corrupt stream and/or packets
                    Object outgoing = events.remove(0);
                    int size = Sizeof.sizeof(outgoing);
                    sentBytes += size;
                    if(Debug.debug) System.out.println("Sent packet size is " + size + ". Total bytes sent is " + sentBytes);
                    if(!dropPacket()){
                        synchronized(out) {
                            out.writeObject(outgoing);
                            out.flush();
                            out.reset();
                        }
                    }else{
                        if(Debug.debug) System.out.println("Dropping Packet");
                    }

                }

            }catch(InterruptedException e){
                e.printStackTrace();
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    /*************Constructors*************/
    /*
     *This creates a regular socket
     */
    public MSocket(String host, int port) throws IOException{
        socket = new Socket(host, port);
        //NOTE: outputStream should be initialized before
        //inputStream, otherwise it will block
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());

        egressQueue = new LinkedBlockingQueue<Object>();
        ingressQueue = new LinkedBlockingQueue<Object>();
        random = new Random(/*seed*/);

        //Start the receiver thread
        //NOTE: This will keep updating the ingress queue
        (new Thread(new Receiver())).start();

        executor = Executors.newFixedThreadPool(10);

        rcvdCount = 0;
        rcvdEvent = new HashMap<PairKey<String, Integer>, Boolean>();
        sentCount = 0;
        rcvdBytes = 0;
        sentBytes = 0;

        creationTime = new Date();
    }

    //Similar to above, except takes an initialized socket
    //NOTE: This constructor is for internal use only
    public MSocket(Socket soc) throws IOException{
        socket = soc;

        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());

        egressQueue = new LinkedBlockingQueue<Object>();
        ingressQueue = new LinkedBlockingQueue<Object>();
        random = new Random(/*seed*/);

        (new Thread(new Receiver())).start();

        executor = Executors.newFixedThreadPool(10);

        rcvdCount = 0;
        rcvdEvent = new HashMap<PairKey<String, Integer>, Boolean>();
        sentCount = 0;
        rcvdBytes = 0;
        sentBytes = 0;

        creationTime = new Date();
    }


    /*************Helpers*************/

    //Generate a quasi-gaussian random delay
    private int getDelay(){
        double randGauss = random.nextGaussian();
        double delay = randGauss > DELAY_THRESHOLD ? randGauss * DELAY_WEIGHT : 0.0;
        return (int)delay;
    }

    //Get a random index
    private int getRandomIndex(int size){
        return random.nextInt(size);
    }

    //return boolean for whether
    //to drop packet or not
    private boolean dropPacket(){
        //Generates U~[0,1)
        double randDouble = random.nextDouble();
        return randDouble < DROP_RATE;
    }

    /*************Public Methods*************/

    /*
     Read incoming packet and induce network delays and packet
     reordering.
     NOTE: This method relies on the
     ingress queue being automatically updated by another thread
    */
    public synchronized Object readObject() throws IOException, ClassNotFoundException{

        //First check if we are in the grace period
        if((new Date()).getTime() - creationTime.getTime() <
                ERROR_FREE_TRANSMISSION_PERIOD){
            return readObjectNoError();
        }

        //The packet to be returned
        Object incoming = null;

        try{
            //Add a random delay
            int delay = getDelay();
            Thread.sleep(delay);

            //Return the head of the queue- no reordering
            if(UNORDER_FACTOR == 0.0 || ingressQueue.size() < 2){
                incoming = ingressQueue.take();
            //Return the second object in the queue- slight reordering
            }else if(UNORDER_FACTOR > 0.0 && UNORDER_FACTOR < 1.0){
                Object first = ingressQueue.take();
                //Take the second element
                incoming = ingressQueue.take();
                //put the first back in the queue
                ingressQueue.put(first);
            }else{//UNORDER_FACTOR == 1, high degree of reordering
                ArrayList events = new ArrayList();
                ingressQueue.drainTo(events);
                //get event at random index
                int idx = getRandomIndex(events.size());
                incoming = events.remove(idx);
                //put the rest back in the ingress queue
                while(events.size() > 0)
                    //Remove the head from events and insert it into the ingress queue
                    ingressQueue.put(events.remove(0));
            }
        }catch(InterruptedException e){
            e.printStackTrace();
        }
        return incoming;
    }


    //Writes the object, while inducing network delay, reordering, and packet drops
    public void writeObject(Object o) {

        //Check if we are within the grace period
        if((new Date()).getTime() - creationTime.getTime() <
                ERROR_FREE_TRANSMISSION_PERIOD){
            writeObjectNoError(o);
            return;
        }

        try{
            //Place packet in the queue, and later change the order of packets sent
            egressQueue.put(o);
        }catch(InterruptedException e){
            e.printStackTrace();
        }

        executor.submit(new NetworkErrorSender());
    }


    //This method is for reference and testing
    //it reads objects without inducing network errors
    //NOTE: THIS SHOULD NOT BE USED IN YOUR SOLUTION
    public synchronized Object readObjectNoError() throws IOException{
        Object incoming = null;
        try{
            incoming = ingressQueue.take();
        }catch(InterruptedException e){
            e.printStackTrace();
        }
        return incoming;
    }

    //This method is for reference and testing
    //it writes objects without inducing network errors
    //NOTE: THIS SHOULD NOT BE USED IN YOUR SOLUTION
    public void writeObjectNoError(Object o){
        try{
            out.writeObject(o);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    //Closes network objects, i.e. sockets, InputObjectStreams,
    // OutputObjectStream
    public void close() {
        try{
            in.close();
            out.close();
            socket.close();
         }catch(IOException e){
         }
    }

}
