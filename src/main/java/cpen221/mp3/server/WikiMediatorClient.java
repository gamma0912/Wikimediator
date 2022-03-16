package cpen221.mp3.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import com.google.gson.JsonObject;

public class WikiMediatorClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    /**
     * Rep Invariant
     *
     * - socket, in, out != null
     *
     * --------------------------------------------------
     *
     * Abstraction Function
     *
     * A WikiMediatorClient object is an instance of a request from a client
     * calling the server.
     * The request is read in using a BufferReader and sent to the
     * server.
     * The request is then responded to in the server and returned to the
     * client to be read.
     * The client is used for testing purposes to create requests and
     * determined if the server is outputting the correct results.
     *
     * ---------------------------------------------------
     */


    /**
     * Make a WikiMediatorClient and connect it to a server running on
     * hostname at the specified port.
     *
     * @throws IOException if can't connect
     */
    public WikiMediatorClient(String hostname, int port) throws IOException {
        socket = new Socket(hostname, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    /**
     * Send a request to the server. Requires this is "open".
     *
     * @param request is a JsonObject that finds the response from the
     *                WikiMediatorServer
     * @throws IOException if network or server failure
     */
    public void sendRequest(JsonObject request) throws IOException {
        out.print(request.toString() + "\r\n");
        out.flush();
    }

    /**
     * Get a reply from the next request that was submitted.
     * Requires this is "open".
     *
     * @return the requested String response to the request
     * @throws IOException if network or server failure
     */
    public String getReply() throws IOException {
        return in.readLine();
    }

    /**
     * Closes the client's connection to the server.
     * This client is now "closed". Requires this is "open".
     *
     * @throws IOException if close fails
     */
    public void close() throws IOException {
        in.close();
        out.close();
        socket.close();
    }
}
