package cpen221.mp3.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpen221.mp3.wikimediator.WikiMediator;

public class WikiMediatorServer {

	/**
	 * Rep Invariant
	 *
	 * - serverSocket != null
	 * - wikiMediator is not null and an instance of the WikiMediator class
	 * - numRequests is updated anytime the server connects or disconnect
	 * from a request
	 * - maxRequests is a set number of requests determined in the
	 * constructor of the number of requests that the server can handle at once.
	 * - stopServer is updated to true whenever a stop is called as a request
	 * from the client to stop the server from running any further requests.
	 *
	 * --------------------------------------------------
	 *
	 * Abstraction Function
	 *
	 *
	 *  WikiMediatorServer is a server that returns the output of the client
	 *  request as a response.
	 *  It accepts requests of the form: Request ::=
	 *  "id": id, "type": type, (the following may or may not appear
	 *  depending on the request) "query": query, "limit": limit,
	 *  "pageTitle1": pageTitle1, "pageTitle2": pageTitle2, "timeout":
	 *  timeout, "pageTitle": pageTitle, "timeWindowInSeconds":
	 *  timeWindowInSeconds, "timeLimitInSeconds: timeLimitInSeconds,
	 *  "maxItems": maxItems
	 *  where the values can be ints or Strings and requests and replies are
	 *  in the form of JsonObject.
	 *  An optional timeout can also be included.
	 *  and for each request, returns a reply of the form: Reply ::=  "id":
	 *  id, "status": status, "response": response
	 *  where response value can be String, int, or List<String> depending on
	 *  the request.
	 *  And error is returned in the response if the request fails to execute
	 *  and the status can be passed or failed.
	 *  WikiMediatorServer can handle multiple concurrent clients.
	 *
	 *
	 * ---------------------------------------------------
	 *
	 * Thread Safety Argument
	 *
	 * This class is thread-safe because it is synchronized:
	 * methods relating to reading the socket and incrementing or decrementing
	 * no. of requests are wrapped in synchronized blocks so there are no data
	 * races.
	 */

	private ServerSocket serverSocket;
	private WikiMediator wikiMediator;
	private static int numRequests;
	private static int maxRequests;
	private static boolean stopServer = false;

	/**
	 * Start a server at a given port number, with the ability to process
	 * upto n requests concurrently.
	 *
	 * @param port the port number to bind the server to, 9000 <= {@code port} <= 9999
	 * @param n the number of concurrent requests the server can handle, 0 < {@code n} <= 32
	 * @param wikiMediator the WikiMediator instance to use for the server, {@code wikiMediator} is not {@code null}
	 */
	public WikiMediatorServer(int port, int n, WikiMediator wikiMediator) {
		/* TODO: Implement this method */
		try {
			serverSocket = new ServerSocket(port);
			this.wikiMediator = wikiMediator;
			numRequests = 0;
			maxRequests = n;
		}
		catch (IOException e) {
			throw new RuntimeException();
		}
	}

	/**
	 * Run the server, listening for connections and handling them, and
	 * closing the server if requested.
	 *
	 */
	public void serve() {
		while (true) {
			try {
				// block until a client connects
				while (numRequests<maxRequests) {
					final Socket socket = serverSocket.accept();
					synchronized (this) {
						if (numRequests < maxRequests) {
							synchronized (this) {
								numRequests++;
								System.err.println("Current Requests: " + numRequests);
							}
							// create a new thread to handle that client
							Thread handler = new Thread(new Runnable() {
								public void run() {
									try {
										try {
											handle(socket);
											if (stopServer) {
												for (Thread t : Thread.getAllStackTraces().keySet()) {
													if (t.getState() == Thread.State.RUNNABLE) {
														t.interrupt();
														socket.close();
														serverSocket.close();
													}
												}
											}
										} finally {
											socket.close();
										}
									} catch (IOException ioe) {
										// this exception wouldn't terminate serve(),
										// since we're now on a different thread, but
										// we still need to handle it
										throw new RuntimeException();
									}
								}
							});
							// start the thread
							handler.start();
						}
					}
				}
			} catch (IOException e) {
				throw new RuntimeException();
			}
		}
	}

	/**
	 * Handle one client connection. Returns when client disconnects.
	 *
	 * @param socket socket where client is connected
	 */
	private void handle(Socket socket) {
		System.err.println("client connected");
		try{
			// get the socket's input stream, and wrap converters around it
			// that convert it from a byte stream to a character stream,
			// and that buffer it so that we can read a line at a time
			BufferedReader in = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			// similarly, wrap character => bytestream converter around the
			// socket output stream, and wrap a PrintWriter around that so
			// that we have more convenient ways to write Java primitive
			// types to it.
			PrintWriter out = new PrintWriter(new OutputStreamWriter(
					socket.getOutputStream()), true);
			try {
				JsonParser parser = new JsonParser();
				JsonObject response = new JsonObject();
				// each request is a single line containing a number
				for (String line = in.readLine(); line != null; line = in
						.readLine()) {
					JsonObject request=parser.parse(line).getAsJsonObject();
					System.err.println("request: " + line);
					response = getResponse(request);
					wikiMediator.writeDataToDisk();
					System.err.println("Result" + response.toString());
					out.println(response.toString() + "\r\n");
				}
			} catch (IOException ioe) {
				throw new RuntimeException();
				// important! our PrintWriter is auto-flushing, but if it were
				// not:
				// out.flush();
			} finally {
				out.close();
				in.close();
				System.err.println("client was disconnected");
				synchronized (this) {
					numRequests--;
					System.err.println("Current Requests: " + numRequests);
				}
			}
		} catch (IOException ioe) {
			throw new RuntimeException();
		}
	}

	/**
	 * returns a response from a specific client request, and checks if there is
	 * a timeout for the request during execution, also determining if the
	 * request is successful.
	 * @param request the request from the server client to execute a
	 *                WikiMediator task
	 * @return the server response for the client request
	 */
	private JsonObject getResponse(JsonObject request) {
		Gson gson = new Gson();
		JsonObject response = new JsonObject();
		String id = request.get("id").getAsString();
		String type = request.get("type").getAsString();
		List<String> output;
		if (type.equals("shortestPath")) {
			String startPage = request.get("pageTitle1").getAsString();
			String endPage = request.get("pageTitle2").getAsString();
			int timeout = request.get("timeout").getAsInt();
			try {
				output = wikiMediator.shortestPath(startPage, endPage, timeout);
				response.addProperty("id", id);
				response.addProperty("status", "success");
				response.addProperty("response", gson.toJson(output));
				return response;
			} catch (TimeoutException te) {
				response.addProperty("id",request.get("id").getAsString());
				response.addProperty("status","failed");
				response.addProperty("response", "Operation timed out");
				return response;
			}
		}
		if (request.has("timeout")) {
			int timeout=request.get("timeout").getAsInt();
			request.remove("timeout");
			ExecutorService executorService = Executors.newSingleThreadExecutor();
			Future<JsonObject> future =
					executorService.submit(new Callable<JsonObject>() {
						@Override
						public JsonObject call() throws Exception {
							return getResponse(request);
						}
					});
			executorService.shutdown();
			try {
				response = future.get(timeout, TimeUnit.SECONDS);
			} catch (ExecutionException | InterruptedException e) {
				future.cancel(true);
				executorService.shutdownNow();
				response.addProperty("id",
						request.get("id").getAsString());
				response.addProperty("status", "failed");
				response.addProperty("response",
						"Unable to execute operation");
				return response;
			} catch (TimeoutException te) {
				future.cancel(true);
				executorService.shutdownNow();
				response.addProperty("id",
						request.get("id").getAsString());
				response.addProperty("status","failed");
				response.addProperty("response",
						"Operation timed out");
				return response;
			}
		}
		if (type.equals("search")) {
			String query = request.get("query").getAsString();
			int limit = request.get("limit").getAsInt();
			output = wikiMediator.search(query, limit);
		} else if (type.equals("zeitgeist")) {
			int limit = request.get("limit").getAsInt();
			output = wikiMediator.zeitgeist(limit);
		} else if (type.equals("getPage")) {
			String pageTitle = request.get("pageTitle").getAsString();
			String content;
			content = wikiMediator.getPage(pageTitle);
			response.addProperty("id", id);
			response.addProperty("status", "success");
			response.addProperty("response", gson.toJson(content));
			return response;
		} else if (type.equals("trending")) {
			int timeLimitInSeconds = request.get("timeLimitInSeconds").getAsInt();
			int maxItems = request.get("maxItems").getAsInt();
			output = wikiMediator.trending(timeLimitInSeconds, maxItems);
		} else if (type.equals("windowedPeakLoad")) {
			int numWindows;
			if (request.has("timeWindowInSeconds")) {
				int timeWindowInSeconds =
						request.get("timeWindowInSeconds").getAsInt();
				numWindows = wikiMediator.windowedPeakLoad(timeWindowInSeconds);
			} else {
				numWindows = wikiMediator.windowedPeakLoad();
			}
			response.addProperty("id", id);
			response.addProperty("status", "success");
			response.addProperty("response", gson.toJson(numWindows));
			return response;
		} else {
			response.addProperty("id",id);
			response.addProperty("response", "bye");
			stopServer = true;
			return response;
		}
		response.addProperty("id", id);
		response.addProperty("status", "success");
		response.addProperty("response", gson.toJson(output));
		return response;
	}
}

