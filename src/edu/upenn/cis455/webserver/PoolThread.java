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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.TimeZone;

import org.apache.log4j.Logger;

public class PoolThread extends Thread {
	private BlockingQueue taskQueue = null;
	private boolean isStopped = false;
	static final Logger logger = Logger.getLogger(PoolThread.class);
	static final String HTML_START = "<html>"
			+ "<title>HTTP Server in java</title>" + "<body>";
	static final String HTML_END = "</body>" + "</html>";
	BufferedReader inFromClient = null;
	DataOutputStream outToClient = null;
	private Socket socket = null;
	private String httpQueryString;
	ThreadPool threadPool;
	private String rootPath;
	private boolean continue_request = false;

	public PoolThread(BlockingQueue queue, ThreadPool threads, String path) {
		taskQueue = queue;
		threadPool = threads;
		rootPath = path;
	}

	public HashMap<String, String> getHeaders() throws IOException {
		String line = "";
		HashMap<String, String> headers = new HashMap<>();

		while (inFromClient.ready()) {
			line = inFromClient.readLine();
			if (line.isEmpty()) {
				continue;
			}
			String[] tmp = line.split(": ");
			if (tmp.length < 2) {
				// send header error response
				continue;
			} else {
				String key = tmp[0].trim();
				String value = tmp[1].trim();
				if (!headers.containsKey(key)) {
					headers.put(key, value);
				}
			}
		}

		return headers;
	}

	public void run() {
		String requestString = null;
		StringTokenizer tokenizer = null;
		String httpMethod = null;
		String httpVersion = null;
		String httpVersionNum = null;

		while (!isStopped()) {

			try {

				socket = (Socket) taskQueue.dequeue();
				// System.out.println("********" + socket.getPort());
			} catch (Exception e) {
				System.out.println("terminated while in waiting status");
				break;
			}
			// logger info
			logger.info("Successfully get the socket");
			if (socket == null) {
				continue;
			}
			// System.out.println("The Client " + socket.getInetAddress() + ":"
			// + socket.getPort() + " is connected");
			try {
				
				inFromClient = new BufferedReader(new InputStreamReader(
						socket.getInputStream()));
			} catch (IOException e) {

				logger.error("IOException occurs when getInputStream() method is called");
				try {
					socket.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				
			}
			try {
				
				outToClient = new DataOutputStream(socket.getOutputStream());
			} catch (IOException e) {

				logger.error("IOException occurs when getOutputStream() method is called");
				continue;
			}
			try {
				requestString = inFromClient.readLine();
			} catch (IOException e) {

				logger.error("IOException occurs when reading from input stream");
				try {
					inFromClient.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					logger.error("close failed");
				}
			}

			String headerLine = requestString; // GET / HTTP/1.1

			try {
				tokenizer = new StringTokenizer(headerLine);
				httpMethod = tokenizer.nextToken();
				httpQueryString = tokenizer.nextToken();
				httpVersion = tokenizer.nextToken();
				httpVersionNum = httpVersion.split("/")[1];
			} catch (NullPointerException e) {
				logger.error("NullPointerException occurs when headerline is null");
				continue;
			}


			// System.out.println("httpVersionNum " + httpVersionNum);
			StringBuffer responseBuffer = new StringBuffer();
			// responseBuffer.append("<b>This is the HTTP Server Home Page.... </b><BR>");
			responseBuffer.append("<b>Files in current root path</b><BR>");

			try {

				HashMap<String, String> map = getHeaders();

				if (map.containsKey("Expect")) {
					continue_request = true;
				}

				if (httpVersionNum.equals("1.1") && !map.containsKey("Host")) {
					try {
						sendResponse(400, "<b>400 Bad Request</b>", false,
								httpVersionNum);
					} catch (Exception e) {
						logger.error("400 response sent failed");
						continue;
					}
				}

				else if (!httpMethod.equals("GET")
						&& !httpMethod.equals("HEAD")) {
					try {
						sendResponse(501, "<b>501 Not Implemented</b>", false,
								httpVersionNum);
					} catch (Exception e) {
						logger.error("501 response sent failed");
						continue;
					}
				}

				else if (httpMethod.equals("GET")
						&& httpQueryString.equals("/shutdown")) {

					logger.info("start processing shutdown");

					(new Thread(new Runnable() {
						@Override
						public void run() {
							HttpServer.stop();
						}

					})).start();
					// outToClient.flush();
					return;
				}

				else if (httpMethod.equals("GET")
						&& httpQueryString.equals("/control")) {
					String name = "Ao Sun</BR>";
					String seasLogin = "SEAS Login: sunao1</BR>";
					String threadsInfo = "All threads in thread pool: <BR>";
					for (PoolThread thread : this.threadPool.getAllThreads()) {
						if (thread.getState() == Thread.State.RUNNABLE) {
							String threadURL = thread.getURL();
							threadsInfo += thread.getName() + thread.getState()
									+ ": " + threadURL + "<BR>";
						} else {
							threadsInfo += thread.getName() + thread.getState()
									+ "<BR>";
						}
					}
					String buttonlink = "<div class= \"link-button\"><a href=\"/shutdown\">Shutdown</a></div>"
							+ "<BR>";
					String controlPage = name + seasLogin + threadsInfo
							+ buttonlink;
					sendResponse(200, controlPage, false, httpVersionNum);
				}

				else {
					boolean headReq = httpMethod.equals("GET") ? false : true;

					String fileName = httpQueryString;

					if (!checkValid(fileName)) {
						try {
							sendResponse(403,
									"<b>The Request path is forbidden...</b>",
									false, httpVersionNum);

						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							socket.close();
						}
					}

					String filePath = this.rootPath + fileName;
					// System.out.println("filePath is " + filePath);
					// if now it is opening a file
					if (new File(filePath).isFile()) {

						if (headReq) {
							System.out.println("headReq!!");
							try {
								sendHeader(200, httpVersionNum);
							} catch (Exception e) {
								socket.close();
							}
						} else {
							try {

								if (socket.isClosed()) {
									continue;
								}
								sendResponse(200, filePath, true,
										httpVersionNum);
							} catch (Exception e) {
								logger.error("send 200 response failed");
								continue;
							}
						}
					} else if (new File(filePath).isDirectory()) {

						if (headReq) {
							sendHeader(200, httpVersionNum);
						} else {
							try {

								showFileForFolder(filePath, responseBuffer,
										httpVersionNum);
							} catch (Exception e) {
								//logger.error("####show file failed");
								e.printStackTrace();
								socket.close();
								
								continue;
							}
						}
					} else {
						try {
							sendResponse(
									404,
									"<b>404 The Requested resource not found...</b>",
									false, httpVersionNum);
						} catch (Exception e) {
							logger.error("send bad request response failed");
							continue;
						}
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				inFromClient.close();
				outToClient.close();
				socket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	public boolean checkValid(String path) {
		Stack<String> stack = new Stack<String>();
		String[] folders = path.split("/");
		for (String str : folders) {
			if (str.equals("..")) {
				if (stack.isEmpty()) {
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

	public void showFileForFolder(String path, StringBuffer buffer,
			String httpVersionNum) throws IOException {

		File folder = new File(path);
		File[] listOfFiles = folder.listFiles();
		for (File file : listOfFiles) {

			String filePath = path + file.getName();
			// System.out.println(filePath);
			String link = filePath.substring(rootPath.length());
			// System.out.println(link);
			String href = "<a href=" + "\"" + link + "/" + "\">"
					+ file.getName() + "</a>";
			buffer.append(href + "<BR>");

		}

		sendResponse(200, buffer.toString(), false, httpVersionNum);

	}

	public void sendHeader(int statusCode, String httpVersion) throws IOException {
		String statusLine = getStatusLine(statusCode, httpVersion);
		String time = "Date: " + getServerTime() + "\r\n";
		String serverdetails = "Server: Java HTTPServer\r\n";
		String contentType = "Content-Type: " + getContentType() + "\r\n";
		if (continue_request) {

			outToClient.writeBytes("HTTP/" + httpVersion + " 100 Continue\r\n");

		}

		outToClient.writeBytes(statusLine);
		outToClient.writeBytes(time);
		outToClient.writeBytes(serverdetails);
		outToClient.writeBytes(contentType);

	}

	public String getContentType() {
		String file = httpQueryString;
		if (!file.contains(".")) {
			return "";
		} else {
			String ret = "";
			String fileext = file.split("\\.")[1];
			switch (fileext) {
			case "txt":
				ret = "text/html";
				break;
			case "html":
				ret = "text/html";
				break;
			case "jpg":
				ret = "image/jpg";
				break;
			case "jpeg":
				ret = "image/jpeg";
				break;
			case "gif":
				ret = "image/gif";
				break;
			case "png":
				ret = "image/png";
				break;
			}
			return ret;
		}
	}

	public String getServerTime() {
		Calendar calendar = Calendar.getInstance();
		SimpleDateFormat dateFormat = new SimpleDateFormat(
				"EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		return dateFormat.format(calendar.getTime());
	}

	public String getStatusLine(int statusCode, String httpVersion) {
		String statusLine = null;
		switch (statusCode) {
		case 200:
			statusLine = "HTTP/" + httpVersion + " 200 OK" + "\r\n";
			break;
		case 400:
			statusLine = "HTTP/" + httpVersion + " 400 Bad Request" + "\r\n";
			break;
		case 404:
			statusLine = "HTTP/" + httpVersion + " 404 Not Found" + "\r\n";
			break;
		case 403:
			statusLine = "HTTP/" + httpVersion + " 403 Forbidden" + "\r\n";
			break;
		case 501:
			statusLine = "HTTP/" + httpVersion + " 501 Not Implemented"
					+ "\r\n";
			break;
		}
		return statusLine;
	}

	public void sendResponse(int statusCode, String responseString,
			boolean isFile, String httpVersion) throws IOException {
		String statusLine = null;
		String serverdetails = "Server: Java HTTPServer\r\n";
		String contentLengthLine = null;
		String fileName = null;
		String contentTypeLine = "Content-Type: text/html" + "\r\n";
		FileInputStream fin = null;

		statusLine = getStatusLine(statusCode, httpVersion);

		if (isFile) {
			fileName = responseString;
			fin = new FileInputStream(fileName);
			contentLengthLine = "Content-Length: "
					+ Integer.toString(fin.available()) + "\r\n";
			if (!fileName.endsWith(".htm") && !fileName.endsWith(".html")) {
				contentTypeLine = "Content-Type: \r\n";
			}

		} else {
			responseString = this.HTML_START + responseString + this.HTML_END;
			contentLengthLine = "Content-Length: " + responseString.length()
					+ "\r\n";
		}

		outToClient.writeBytes(statusLine);

		outToClient.writeBytes(serverdetails);

		outToClient.writeBytes(contentTypeLine);

		outToClient.writeBytes(contentLengthLine);
		outToClient.writeBytes("Connection: close\r\n");

		outToClient.writeBytes("\r\n");
		
		outToClient.flush();

		if (isFile) {

			if (!socket.isClosed())
				sendFile(fin, outToClient);

		} else {

			outToClient.writeBytes(responseString);

		}

	}

	public void sendFile(FileInputStream fin, DataOutputStream out)
			throws IOException {
		byte[] buffer = new byte[1024];
		int bytesRead;

		while ((bytesRead = fin.read(buffer)) != -1 && !socket.isClosed()) {

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
