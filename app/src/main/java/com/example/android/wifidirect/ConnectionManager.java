package com.example.android.wifidirect;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * Created by batman on 04/08/16.
 */
public class ConnectionManager {
    private static final String TAG = "ConnectionManager";
    private static final int P2P_PORT = 8988;
    private static final int SOCKET_SELECT_TIMEOUT = 500;
    private static final ConnectionManager instance = new ConnectionManager();
    public Context context;

    public String mServerAddr = null;

    public Selector selector = null;
    public SocketChannel socketChannel = null;

    public ServerSocketChannel mServerSocketChannel = null;

    private boolean disconnectNow = false;

    public static ConnectionManager getInstance() {
        return instance;
    }

    /**
     * read out -1, connection broken, remove it from clients collection
     */
    public void onBrokenConnClient(SocketChannel schannel) {
        try {
            String peeraddr = schannel.socket().getInetAddress().getHostAddress();
            //closeClient();
            Log.d(ConnectionManager.TAG, "onBrokenConn : set null client channel after server down: " + peeraddr);
            schannel.close();
        } catch (Exception e) {
            Log.e(ConnectionManager.TAG, "onBrokenConn: close channel: " + e.toString());
        }
    }

    /**
     * write byte buf to the socket channel.
     */
    private int writeData(SocketChannel sChannel, String jsonString) {
        byte[] buf = jsonString.getBytes();
        ByteBuffer bytebuf = ByteBuffer.wrap(buf);  // wrap the buf into byte buffer
        int nwritten = 0;
        try {
            //bytebuf.flip();  // no flip after creating from wrap.
            Log.d(ConnectionManager.TAG, "writeData: start:limit = " + bytebuf.position() + " : " + bytebuf.limit());
            nwritten = sChannel.write(bytebuf);
        } catch (Exception e) {
            // Connection may have been closed
            Log.e(ConnectionManager.TAG, "writeData: exception : " + e.toString());
            onBrokenConnClient(sChannel);
        }
        Log.d(ConnectionManager.TAG, "writeData: content: " + new String(buf) + "  : len: " + nwritten);
        return nwritten;
    }

    /**
     * the device want to push out data.
     * If the device is client, the only channel is to the server.
     * If the device is server, it just pub the data to all clients for now.
     */
    public int pushOutData(final String jsonString) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                Log.d(ConnectionManager.TAG, "SENDING DATA: " + jsonString);
                sendData(jsonString);
            }
        }).start();
        return 0;
    }

    /**
     * whenever client write to server, carry the format of "client_addr : msg "
     */
    private int sendData(String jsonString) {
        if (socketChannel == null) {
            Log.d(ConnectionManager.TAG, "sendData: channel not connected ! waiting...");
            return 0;
        }
        Log.d(ConnectionManager.TAG, "sendData  -> " + jsonString);
        return writeData(socketChannel, jsonString);
    }

    public boolean isDisconnectNow() {
        return disconnectNow;
    }

    public void setDisconnectNow(boolean disconnectNow) {
        this.disconnectNow = disconnectNow;
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public class ServerAsyncTask extends AsyncTask<Void, Void, Integer> {

        /**
         * @param context
         */
        public ServerAsyncTask(Context context) {
            ConnectionManager.this.context = context;
        }

        /**
         * create a server socket channel to listen to the port for incoming connections.
         */
        public ServerSocketChannel createServerSocketChannel(int port) throws IOException {
            // Create a non-blocking socket channel
            ServerSocketChannel ssChannel = ServerSocketChannel.open();
            ssChannel.configureBlocking(false);
            ServerSocket serverSocket = ssChannel.socket();
            serverSocket.bind(new InetSocketAddress(port));  // bind to the port to listen.
            return ssChannel;
        }


        @Override
        protected Integer doInBackground(Void... params) {
            try {
                closeConnection();   // close linger server.

                // create server socket and register to selector to listen OP_ACCEPT event
                ConnectionManager.this.mServerSocketChannel = createServerSocketChannel(P2P_PORT);
                // BindException if already bind.
                ConnectionManager.this.mServerAddr = ConnectionManager.this.mServerSocketChannel.socket().getInetAddress().getHostAddress();

                ConnectionManager.this.selector = Selector.open();
                SelectionKey acceptKey = ConnectionManager.this.mServerSocketChannel
                        .register(ConnectionManager.this.selector, SelectionKey.OP_ACCEPT);
                acceptKey.attach("accept_channel");

                processEvents(this);
                return 0;
            } catch (Exception e) {
                Log.e(ConnectionManager.TAG, e.getMessage());
                return -1;
            } finally {
                closeConnection();   // close linger server.
            }
        }
    }

    public class ClientAsyncTask  extends AsyncTask<Void, Void, Integer> {

        public ClientAsyncTask(Activity activity, String hostAddress) {
            ConnectionManager.this.context = activity;
            ConnectionManager.this.mServerAddr = hostAddress;
        }

        @Override
        protected Integer doInBackground(Void... voids) {

            try {
                closeConnection();   // close linger server.

                // connected to the server upon start client.
                SocketChannel sChannel = connectTo(ConnectionManager.this.mServerAddr, P2P_PORT);

                ConnectionManager.this.selector = Selector.open();
                ConnectionManager.this.socketChannel = sChannel;
                sChannel.register(ConnectionManager.this.selector, SelectionKey.OP_READ);
                Log.d(TAG, "startClientSelector : started: "
                        + ConnectionManager.this.socketChannel.socket().getLocalAddress().getHostAddress());

                // start selector monitoring, blocking
                processEvents(this);

                return 0;

            } catch(Exception e) {
                Log.e(TAG, "startClientSelector : exception: " + e.toString());
                return -1;
            } finally {
                closeConnection();   // close linger server.
            }
        }

        /**
         * create a socket channel and connect to the host.
         * after return, the socket channel guarantee to be connected.
         */
        public SocketChannel connectTo(String hostname, int port) throws Exception {
            SocketChannel sChannel = null;

            sChannel = createSocketChannel(hostname, port);  // connect to the remote host, port

            // Before the socket is usable, the connection must be completed. finishConnect().
            while (!sChannel.finishConnect()) {
                // blocking spin lock
            }

            // Socket channel is now ready to use
            return sChannel;
        }

        /**
         * Creates a non-blocking socket channel to connect to specified host name and port.
         * connect() is called on the new channel before it is returned.
         */
        public SocketChannel createSocketChannel(String hostName, int port) throws IOException {
            // Create a non-blocking socket channel
            SocketChannel sChannel = SocketChannel.open();
            sChannel.configureBlocking(false);

            // Send a connection request to the server; this method is non-blocking
            sChannel.connect(new InetSocketAddress(hostName, port));
            return sChannel;
        }
    }

    /**
     * a device can only be either group owner, or group client, not both.
     * when we start as client, close server, if existing due to linger connection.
     */
    public void closeConnection() {
        if (ConnectionManager.this.mServerSocketChannel != null) {
            try {
                ConnectionManager.this.mServerSocketChannel.close();
                ConnectionManager.this.selector.close();
            } catch (Exception ignored) {

            } finally {
                ConnectionManager.this.mServerSocketChannel = null;
                ConnectionManager.this.selector = null;
                ConnectionManager.this.mServerAddr = null;
            }
        }
        if(ConnectionManager.this.socketChannel != null ) {
            try{
                ConnectionManager.this.socketChannel.close();
                ConnectionManager.this.selector.close();
            }catch(Exception ignored){

            }finally{
                ConnectionManager.this.socketChannel = null;
                ConnectionManager.this.selector = null;
            }
        }
    }

    private void processEvents(AsyncTask<Void, Void, Integer> task) {
        // Wait for events looper
        while (!ConnectionManager.this.isDisconnectNow() && !task.isCancelled()) {
            try {
                Log.d(ConnectionManager.TAG, "select : selector monitoring: ");
                selector.select(SOCKET_SELECT_TIMEOUT);   // blocked on waiting for event

                Log.d(ConnectionManager.TAG, "select : selector evented out: ");
                // Get list of selection keys with pending events, and process it.
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    // Get the selection key, and remove it from the list to indicate that it's being processed
                    SelectionKey selKey = keys.next();
                    keys.remove();
                    Log.d(ConnectionManager.TAG, "select : selectionkey: " + selKey.attachment());

                    try {
                        String data = processSelectionKey(selector, selKey);  // process the selection key.
                    } catch (IOException e) {
                        selKey.cancel();
                        Log.e(ConnectionManager.TAG, "select : io exception in processing selector event: " + e.toString());
                    }
                }
            } catch (Exception e) {  // catch all exception in select() and the following ops in mSelector.
                Log.e(ConnectionManager.TAG, "Exception in selector: " + e.toString());
                break;
            }
        }
    }

    /**
     * process the event popped to the selector
     */
    public String processSelectionKey(Selector selector, SelectionKey selKey) throws IOException {
        if (selKey.isValid() && selKey.isAcceptable()) {  // there is a connection to the server socket channel
            ServerSocketChannel ssChannel = (ServerSocketChannel) selKey.channel();
            SocketChannel sChannel = ssChannel.accept();  // accept the connect and get a new socket channel.
            this.socketChannel = sChannel;
            sChannel.configureBlocking(false);

            // let the selector monitor read/write the accepted connections.
            SelectionKey socketKey = sChannel.register(selector, SelectionKey.OP_READ);
            socketKey.attach("accepted_client " + sChannel.socket().getInetAddress().getHostAddress());
            Log.d(ConnectionManager.TAG, "processSelectionKey : accepted a client connection: " + sChannel.socket().getInetAddress().getHostAddress());
        } else if (selKey.isValid() && selKey.isConnectable()) {   // client connect to server got the response.
            SocketChannel sChannel = (SocketChannel) selKey.channel();

            boolean success = sChannel.finishConnect();
            if (!success) {
                // An error occurred; unregister the channel.
                selKey.cancel();
                Log.e(ConnectionManager.TAG, " processSelectionKey : finish connection not success !");
            }
            Log.d(ConnectionManager.TAG, "processSelectionKey : this client connect to remote success: ");
        } else if (selKey.isValid() && selKey.isReadable()) {
            // Get channel with bytes to read
            SocketChannel sChannel = (SocketChannel) selKey.channel();
            Object attachment = selKey.attachment();
            Log.d(ConnectionManager.TAG, "processSelectionKey : remote client is readable, read data: " + attachment);
            // we can retrieve the key we attached earlier, so we now what to do / where the data is coming from
            // MyIdentifierType myIdentifier = (MyIdentifierType)key.attachment();
            // myIdentifier.readTheData();
            return doReadable(sChannel);
        } else if (selKey.isValid() && selKey.isWritable()) {
            // Not select on writable...endless loop.
            SocketChannel sChannel = (SocketChannel)selKey.channel();
            Log.d(TAG, "processSelectionKey : remote client is writable, write data: ");
        }
        return null;
    }

    /**
     * read data when OP_READ event
     */
    public String readData(SocketChannel sChannel) {
        ByteBuffer buf = ByteBuffer.allocate(1024 * 4);   // let's cap json string to 4k for now.
        byte[] bytes = null;
        String jsonString = null;

        try {
            buf.clear();  // Clear the buffer and read bytes from socket
            int numBytesRead = sChannel.read(buf);
            if (numBytesRead == -1) {
                // read -1 means socket channel is broken. remove it from the selector
                Log.e(ConnectionManager.TAG, "readData : channel closed due to read -1: ");
                sChannel.close();  // close the channel.
            } else {
                Log.d(ConnectionManager.TAG, "readData: bufpos: limit : " + buf.position() + ":" + buf.limit() + " : " + buf.capacity());
                buf.flip();  // make buffer ready for read by flipping it into read mode.
                Log.d(ConnectionManager.TAG, "readData: bufpos: limit : " + buf.position() + ":" + buf.limit() + " : " + buf.capacity());
                bytes = new byte[buf.limit()];  // use bytes.length will cause underflow exception.
                buf.get(bytes);
                // while ( buf.hasRemaining() ) buf.get();
                jsonString = new String(bytes);  // convert byte[] back to string.
            }
        } catch (Exception e) {
            Log.e(ConnectionManager.TAG, "readData : exception: " + e.toString());
        }

        Log.d(ConnectionManager.TAG, "readData: content: " + jsonString);
        return jsonString;
    }

    /**
     * handle the readable event from selector
     */
    public String doReadable(SocketChannel schannel) {
        final String data = readData(schannel);
        //TODO: DoSomething with the data here!
        return data;
    }
}
