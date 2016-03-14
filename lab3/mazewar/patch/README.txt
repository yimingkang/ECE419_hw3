ECE419 Lab 2 Patch
==================

# Client Side:

- MazeImpl.java: Changed so that missile movements are updated on the server
  side rather than the client side. Missile movement code has been taken out
  of the run() function and placed into a separate missileTick() function.

- Maze.java: The Maze abstract class updated to include the missleTick() 
  function.


# Server Side:

To avoid providing solution to other parts of the lab, no server side code
files are provided. But the general implementation strategy specific to handle
missile movements is as follows:

- Use a separate thread that adds a "missile tick" action to the queue every 
  200 ms (this time interval comes from handout). This is equivalent to sending
  a movement action from the server to clients.

- HINT: thread.sleep(200) will set a thread to sleep for 200ms.
