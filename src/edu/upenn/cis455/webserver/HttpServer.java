package edu.upenn.cis455.webserver;

import java.io.IOException;
import java.net.*;

class HttpServer {
  
	static ServerSocket serverSocket;
	static ThreadPool threadPool;
	static boolean isTerminate;
	public static void main(String args[]) throws IOException
  {
    /* your code here */
	  int portNum = Integer.parseInt(args[0]);
	  String rootDirectory = args[1];
	  System.out.println(rootDirectory);
	  int noOfThreads = 10;
	  int maxNoOfTasks = 10;
	  org.apache.log4j.BasicConfigurator.configure();
	  serverSocket = new ServerSocket(portNum);
	  threadPool = new ThreadPool(noOfThreads, maxNoOfTasks, rootDirectory);
	  System.out.println("waiting connections....");
	  isTerminate = false; //create a flag in main thread
	  

	  while(!isTerminate) {
		  Socket socket = null;

		  try{
			  if(!isTerminate) {
				  socket = serverSocket.accept();
				  socket.setSoTimeout(10000); 
			  }
		  }catch(IOException e) {
			  System.err.println("Accept failed.");
		  }
		  
		  
		  
		  BlockingQueue taskQueue = threadPool.getTaskQueue();
		  if(taskQueue.size() == maxNoOfTasks) {
			  socket.getOutputStream().write("HTTP/1.0 505 Error\n\n".getBytes());
			  socket.getOutputStream().write("<html><body>Try again</body></html>\n\n".getBytes());
			  socket.close();
		  } else {
			  try {
				taskQueue.enqueue(socket);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		 }
		
	  }
	
	  threadPool.stop();
	  serverSocket.close();
	  
	  System.out.println("All threads terminated");
	  
  }
  
	public synchronized static void stop() {
		if(isTerminate) {
			return;
		}
		isTerminate = true;
		threadPool.stop();
		while (!threadPool.isStop()) {}
		try {
			serverSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
