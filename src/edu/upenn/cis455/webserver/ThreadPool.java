package edu.upenn.cis455.webserver;
import java.util.*;
import java.net.*;

public class ThreadPool {
	private BlockingQueue taskQueue;
	private List<PoolThread> threads = new ArrayList<PoolThread>();
	public boolean isStopped  = false;
	private String rootPath;
	
	public ThreadPool(int noOfThreads, BlockingQueue taskQueue, String rootPath) {
		this.rootPath = rootPath;
		this.taskQueue = taskQueue;
		for(int i = 0; i < noOfThreads; i++) {
			threads.add(new PoolThread(taskQueue, this, rootPath));
		}
		for(PoolThread thread: threads) {
			thread.start();
		}
	}
	
//	public synchronized BlockingQueue getTaskQueue() {
//		
//		return this.taskQueue;
//	}
	
//	public synchronized void execute(Runnable task) throws Exception {
//		if(this.isStopped) throw 
//			new IllegalStateException("ThreadPool is stopped");
//		this.taskQueue.enqueue(task);
//			
//	}
	
	public synchronized List<PoolThread> getAllThreads() {
		return this.threads;
	}
	
	public synchronized boolean isStop() {
		return this.isStopped;
	}
	
	public synchronized void stop() {
		for(PoolThread thread: threads) {
			thread.doStop();
		}
		this.isStopped = true;
	}
}
