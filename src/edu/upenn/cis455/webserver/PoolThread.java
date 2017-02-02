package edu.upenn.cis455.webserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Stack;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;


public class PoolThread extends Thread {
	private BlockingQueue taskQueue = null;
	private boolean isStopped = false;
	static final Logger logger = Logger.getLogger(PoolThread.class);
	static final String HTML_START = "<html>" + "<title>HTTP Server in java</title>" + "<body>";
	static final String HTML_END = "</body>" + "</html>";
	BufferedReader inFromClient = null;
	DataOutputStream outToClient = null;
	static Socket socket = null;
	String httpQueryString;
	ThreadPool threadPool;
	private String rootPath;
	
	public PoolThread(BlockingQueue queue, ThreadPool threads, String path) {
		taskQueue = queue;
		threadPool = threads;
		rootPath = path;
	}
	
	public void run() {
		String requestString = null;
		StringTokenizer tokenizer = null;
		String httpMethod = null;
		String httpVersion = null;
		while(!isStopped()) {

			try {
				System.out.println("********");
				socket = (Socket) taskQueue.dequeue();
				System.out.println("********" + socket.getPort());
			} catch(Exception e) {
				System.out.println("terminated while in waiting status");
				break;
			}
			//logger info
			logger.info("Successfully get the socket");
			if(socket == null) {
				continue;
			}
//			System.out.println("The Client " + socket.getInetAddress() + ":" + socket.getPort() + " is connected");
			try {
				inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				logger.error("IOException occurs when getInputStream() method is called");
				continue;
			}
			try {
				outToClient = new DataOutputStream(socket.getOutputStream());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				logger.error("IOException occurs when getOutputStream() method is called");
				continue;
			}
			try {
				requestString = inFromClient.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
				logger.error("IOException occurs when reading from input stream");
				continue;
			}

			String headerLine = requestString;
			try {
				tokenizer = new StringTokenizer(headerLine);
			} catch(NullPointerException e) {
				logger.error("NullPointerException occurs when headerline is null");
				continue;
			}
			
			try {
				httpMethod = tokenizer.nextToken();
			} catch(NullPointerException e) {
				logger.error("NullPointerException occurs when httpMethod is null");
				continue;
			}
			try {
				httpQueryString = tokenizer.nextToken();
			} catch(NullPointerException e) {
				logger.error("NullPointerException occurs when httpQueryString is null");
				continue;
			}
			
			try {
				httpVersion = tokenizer.nextToken();
			} catch(NullPointerException e) {
				logger.error("NullPointerException occurs when httpVersion is null");
				continue;
			}
			
			StringBuffer responseBuffer = new StringBuffer();
			responseBuffer.append("<b>This is the HTTP Server Home Page.... </b><BR>");
			responseBuffer.append("<b>Files in current root path</b><BR>");
			System.out.println("httpQueryString is " + httpQueryString);
			System.out.println("httpVersion is " + httpVersion);
			System.out.println("*The HTTP request string is ....");
			
			try {
//				while(inFromClient.ready()) {
//					responseBuffer.append(requestString + "<BR>");
//					System.out.println("**" + requestString);
//					requestString = inFromClient.readLine();
//				}
				if(httpMethod.equals("GET")) {
					if(httpQueryString.equals("/shutdown")) {
						System.out.println("**shutdown***");
						logger.info("start processing shutdown");
						//sendResponse(200, responseBuffer.toString(), false);
						(new Thread(new Runnable() {
							@Override
							public void run() {
								HttpServer.stop();
							}
							
						})).start();
						//outToClient.flush();
						return;
					}
					
					else if(httpQueryString.equals("/control")) {
						String name = "Ao Sun</BR>";
						String seasLogin = "SEAS Login: sunao1</BR>";
						String threadsInfo = "All threads in thread pool: <BR>";
						for(PoolThread thread: this.threadPool.getAllThreads()) {
							if(thread.getState() == Thread.State.RUNNABLE) {
								String threadURL = thread.getURL();
								threadsInfo += thread.getName() + thread.getState() + ": " + threadURL + "<BR>";
							} else {
								threadsInfo += thread.getName() + thread.getState() + "<BR>";
							}
						}
						String buttonlink = "<div class= \"link-button\"><a href=\"/shutdown\">Shutdown</a></div>" + "<BR>";
						String controlPage = name + seasLogin + threadsInfo + buttonlink;
						sendResponse(200,controlPage,false);
					}
					
				else {
						System.out.println("*******" + httpQueryString);
						String fileName = httpQueryString.replaceFirst("/", "");
						Stack<String> stack = new Stack<>();
						if(!checkValid(fileName, stack)) {
							try {
								sendResponse(403, "<b>The Request path is forbidden...</b>", false);
								
							} catch (Exception e) {
								e.printStackTrace();
							} finally {
								socket.close();
							}
						}
						String relativePath = getRelativePath(stack);
						String filePath = this.rootPath + "/" + fileName;
						System.out.println("filePath is " + filePath);
						//if now it is opening a file
						if(new File(filePath).isFile()) {
							try {
								sendResponse(200, filePath, true);
							} catch(Exception e) {
								e.printStackTrace();
							}
						} else if(new File(filePath).isDirectory()) {
							
							showFileForFolder(filePath, responseBuffer);
						}
						
						
						System.out.println("fileName&&&" + fileName);
						
//						if(new File(filePath).isFile()) {
//							
//							try {
//								sendResponse(200, filePath, true);
//							} catch (Exception e) {
//								// TODO Auto-generated catch block
//								e.printStackTrace();
//							}
//						} else {
//							try {
//								sendResponse(404, "<b>The Requested resource not found ....</b>", false);
//							} catch (Exception e) {
//								// TODO Auto-generated catch block
//								e.printStackTrace();
//							}
//						}
//					}
//				} else {
//					try {
//						sendResponse(404, "<b>The Requested resource not found ....</b>", false);
//					} catch (Exception e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
				}
//				
			}
			}catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				socket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public String getRelativePath(Stack stack) {
		String ret = "";
		while(!stack.isEmpty()) {
			ret += "/" + stack.pop();
		}
		return ret;
	}
	
	public boolean checkValid(String path, Stack<String> stack) {
		
		String[] folders = path.split("/");
		for(String str: folders) {
			if(str.equals("..")) {
				if(stack.isEmpty()) {
					return false;
				} else {
					stack.pop();
				}
			} else {
				stack.push(str);
			}
		}
		return true;
	}
	
	public void showFileForFolder(String path, StringBuffer buffer) {
			
		System.out.println("99999");
		File folder = new File(path);
		File[] listOfFiles = folder.listFiles();
		for(File file: listOfFiles) {
			String filePath =  "/" + file.getName();
			String href = "<a href=" + "\"" + filePath + "\">" + file.getName() + "</a>";
			buffer.append(href + "<BR>");
			
		}
		sendResponse(200, buffer.toString(), false);
	}
	
	public void sendResponse(int statusCode, String responseString, boolean isFile) {
		String statusLine = null;
		String serverdetails = "Server: Java HTTPServer";
		String contentLengthLine = null;
		String fileName = null;
		String contentTypeLine = "Content-Type: text/html" + "\r\n";
		FileInputStream fin = null;
//		if(statusCode == 200) {
//			statusLine = "HTTP/1.1 200 OK" + "\r\n";
//		} else {
//			statusLine = "HTTP/1.1 404 Not Found" + "\r\n";
//		}
		
		switch(statusCode) {
			case 200: statusLine = "HTTP/1.1 200 OK" + "\r\n";
					  break;
			case 404: statusLine = "HTTP/1.1 404 Not Found" + "\r\n";
					  break;
			case 403: statusLine = "HTTP/1.1 403 Forbidden" + "\r\n";
					  break;
		}
		
		
		if(isFile) {
			fileName = responseString;
			try {
				fin = new FileInputStream(fileName);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				contentLengthLine = "Content-Length: " + Integer.toString(fin.available()) + "\r\n";
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(!fileName.endsWith(".htm") && !fileName.endsWith(".html")) {
				contentTypeLine = "Content-Type: \r\n";
			}		
		} else {
			responseString = this.HTML_START + responseString + this.HTML_END;
			contentLengthLine = "Content-Length: " + responseString.length() + "\r\n";
		}
		
		
		try {
			outToClient.writeBytes(statusLine);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			outToClient.writeBytes(serverdetails);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			outToClient.writeBytes(contentTypeLine);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			outToClient.writeBytes(contentLengthLine);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			outToClient.writeBytes("Connection: close\r\n");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			outToClient.writeBytes("\r\n");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		if(isFile) {
			try {
				sendFile(fin, outToClient);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			try {
				outToClient.writeBytes(responseString);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		try {
			outToClient.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		System.out.println("near to close******");
	}
	
	public void sendFile(FileInputStream fin, DataOutputStream out) throws Exception {
		byte[] buffer = new byte[1024];
		int bytesRead;
		while((bytesRead = fin.read(buffer)) != -1) {
			out.write(buffer, 0, bytesRead);
		}
		fin.close();
	}
	
	public synchronized boolean isStopped() {
		return isStopped;
	}
	
	public synchronized String getURL() {
		return this.httpQueryString;
	}
	
	public synchronized void doStop() {
		isStopped = true;
		this.interrupt();
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
}
