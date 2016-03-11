/*
Copyright (C) 2004 Geoffrey Alan Washburn
   
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.
   
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
   
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
USA.
*/
  
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JOptionPane;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import javax.swing.BorderFactory;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.Hashtable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * The entry point and glue code for the game.  It also contains some helpful
 * global utility methods.
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: Mazewar.java 371 2004-02-10 21:55:32Z geoffw $
 */

public class Mazewar extends JFrame {

        /**
         * The default width of the {@link Maze}.
         */
        private final int mazeWidth = 20;

        /**
         * The default height of the {@link Maze}.
         */
        private final int mazeHeight = 10;

        /**
         * The default random seed for the {@link Maze}.
         * All implementations of the same protocol must use 
         * the same seed value, or your mazes will be different.
         */
        private final int mazeSeed = 42;

        /**
         * The {@link Maze} that the game uses.
         */
        private Maze maze = null;

        /**
         * The Mazewar instance itself. 
         */
        private Mazewar mazewar = null;
        private MSocket mSocket = null;
        private ObjectOutputStream out = null;
        private ObjectInputStream in = null;

        /**
         * The {@link GUIClient} for the game.
         */
        private GUIClient guiClient = null;
        
        
        /**
         * A map of {@link Client} clients to client name.
         */
        private Hashtable<String, Client> clientTable = null;

        /**
         * A queue of events.
         */
        private BlockingQueue eventQueue = null;
        
        /**
         * The panel that displays the {@link Maze}.
         */
        private OverheadMazePanel overheadPanel = null;

        /**
         * The table the displays the scores.
         */
        private JTable scoreTable = null;
        
        /** 
         * Create the textpane statically so that we can 
         * write to it globally using
         * the static consolePrint methods  
         */
        private static final JTextPane console = new JTextPane();
      
        /** 
         * Write a message to the console followed by a newline.
         * @param msg The {@link String} to print.
         */ 
        public static synchronized void consolePrintLn(String msg) {
                console.setText(console.getText()+msg+"\n");
        }
        
        /** 
         * Write a message to the console.
         * @param msg The {@link String} to print.
         */ 
        public static synchronized void consolePrint(String msg) {
                console.setText(console.getText()+msg);
        }
        
        /** 
         * Clear the console. 
         */
        public static synchronized void clearConsole() {
           console.setText("");
        }
        
        /**
         * Static method for performing cleanup before exiting the game.
         */
        public static void quit() {
                // Put any network clean-up code you might have here.
                // (inform other implementations on the network that you have 
                //  left, etc.)
                

                System.exit(0);
        }
       
        /** 
         * The place where all the pieces are put together. 
         */
        public Mazewar(String serverHost, int serverPort) throws IOException,
                                                ClassNotFoundException {
                super("ECE419 Mazewar");
                consolePrintLn("ECE419 Mazewar started!");
                
                // Create the maze
                maze = new MazeImpl(new Point(mazeWidth, mazeHeight), mazeSeed);
                assert(maze != null);
                
                // Have the ScoreTableModel listen to the maze to find
                // out how to adjust scores.
                ScoreTableModel scoreModel = new ScoreTableModel();
                assert(scoreModel != null);
                maze.addMazeListener(scoreModel);
                
                // Throw up a dialog to get the GUIClient name.
                String name = JOptionPane.showInputDialog("Enter your name");
                if((name == null) || (name.length() == 0)) {
                  Mazewar.quit();
                }
                
                mSocket = new MSocket(serverHost, serverPort);
                //Send hello packet to server
                MPacket hello = new MPacket(name, MPacket.HELLO, MPacket.HELLO_INIT);
                hello.mazeWidth = mazeWidth;
                hello.mazeHeight = mazeHeight;
                
                if(Debug.debug) System.out.println("Sending hello");
                mSocket.writeObject(hello);
                if(Debug.debug) System.out.println("hello sent");
                //Receive response from server
                MPacket resp = (MPacket)mSocket.readObject();
                if(Debug.debug) System.out.println("Received response from server");

                //Initialize queue of events
                eventQueue = new LinkedBlockingQueue<MPacket>();
                //Initialize hash table of clients to client name 
                clientTable = new Hashtable<String, Client>(); 
                
                // Create the GUIClient and connect it to the KeyListener queue
                //RemoteClient remoteClient = null;
                for(Player player: resp.players){  
                        if(player.name.equals(name)){
                        	if(Debug.debug)System.out.println("Adding guiClient: " + player);
                                guiClient = new GUIClient(name, eventQueue);
                                maze.addClientAt(guiClient, player.point, player.direction);
                                this.addKeyListener(guiClient);
                                clientTable.put(player.name, guiClient);
                        }else{
                        	if(Debug.debug)System.out.println("Adding remoteClient: " + player);
                                RemoteClient remoteClient = new RemoteClient(player.name);
                                maze.addClientAt(remoteClient, player.point, player.direction);
                                clientTable.put(player.name, remoteClient);
                        }
                }
                
                // Use braces to force constructors not to be called at the beginning of the
                // constructor.
                /*
                {
                        maze.addClient(new RobotClient("Norby"));
                        maze.addClient(new RobotClient("Robbie"));
                        maze.addClient(new RobotClient("Clango"));
                        maze.addClient(new RobotClient("Marvin"));
                }
                */

                
                // Create the panel that will display the maze.
                overheadPanel = new OverheadMazePanel(maze, guiClient);
                assert(overheadPanel != null);
                maze.addMazeListener(overheadPanel);
                
                // Don't allow editing the console from the GUI
                console.setEditable(false);
                console.setFocusable(false);
                console.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder()));
               
                // Allow the console to scroll by putting it in a scrollpane
                JScrollPane consoleScrollPane = new JScrollPane(console);
                assert(consoleScrollPane != null);
                consoleScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Console"));
                
                // Create the score table
                scoreTable = new JTable(scoreModel);
                assert(scoreTable != null);
                scoreTable.setFocusable(false);
                scoreTable.setRowSelectionAllowed(false);

                // Allow the score table to scroll too.
                JScrollPane scoreScrollPane = new JScrollPane(scoreTable);
                assert(scoreScrollPane != null);
                scoreScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Scores"));
                
                // Create the layout manager
                GridBagLayout layout = new GridBagLayout();
                GridBagConstraints c = new GridBagConstraints();
                getContentPane().setLayout(layout);
                
                // Define the constraints on the components.
                c.fill = GridBagConstraints.BOTH;
                c.weightx = 1.0;
                c.weighty = 3.0;
                c.gridwidth = GridBagConstraints.REMAINDER;
                layout.setConstraints(overheadPanel, c);
                c.gridwidth = GridBagConstraints.RELATIVE;
                c.weightx = 2.0;
                c.weighty = 1.0;
                layout.setConstraints(consoleScrollPane, c);
                c.gridwidth = GridBagConstraints.REMAINDER;
                c.weightx = 1.0;
                layout.setConstraints(scoreScrollPane, c);
                                
                // Add the components
                getContentPane().add(overheadPanel);
                getContentPane().add(consoleScrollPane);
                getContentPane().add(scoreScrollPane);
                
                // Pack everything neatly.
                pack();

                // Let the magic begin.
                setVisible(true);
                overheadPanel.repaint();
                this.requestFocusInWindow();
        }

        /*
        *Starts the ClientSenderThread, which is 
         responsible for sending events
         and the ClientListenerThread which is responsible for 
         listening for events
        */
        private void startThreads(){
                //Start a new sender thread 
                new Thread(new ClientSenderThread(mSocket, eventQueue)).start();
                //Start a new listener thread 
                new Thread(new ClientListenerThread(mSocket, clientTable)).start();    
        }

        
        /**
         * Entry point for the game.  
         * @param args Command-line arguments.
         */
        public static void main(String args[]) throws IOException,
                                        ClassNotFoundException{

             String host = args[0];
             int port = Integer.parseInt(args[1]);
             /* Create the GUI */
             Mazewar mazewar = new Mazewar(host, port);
             mazewar.startThreads();
        }
}
