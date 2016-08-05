package com.example.android.wifidirect;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
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
 * A simple server socket that accepts connection and writes some data on
 * the stream.
 */
public class ServerAsyncTask extends AsyncTask<Void, Void, Integer> {

    private ConnectionManager instance;

    /**
     * @param context
     * @param instance
     */
    public ServerAsyncTask(Context context, ConnectionManager instance) {
        this.instance = instance;
        this.instance.context = context;
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
            closeServer();   // close linger server.

            // create server socket and register to selector to listen OP_ACCEPT event
            this.instance.mServerSocketChannel = createServerSocketChannel(8988); // BindException if already bind.
            this.instance.mServerAddr = this.instance.mServerSocketChannel.socket().getInetAddress().getHostAddress();

            this.instance.mServerSelector = Selector.open();
            SelectionKey acceptKey = this.instance.mServerSocketChannel.register(this.instance.mServerSelector, SelectionKey.OP_ACCEPT);
            acceptKey.attach("accept_channel");

            // Wait for events looper
            while (!ConnectionManager.getInstance().isDisconnectNow() && !isCancelled()) {
                try {
                    Log.d(WiFiDirectActivity.TAG, "select : selector monitoring: ");
                    this.instance.mServerSelector.select(100);   // blocked on waiting for event

                    Log.d(WiFiDirectActivity.TAG, "select : selector evented out: ");
                    // Get list of selection keys with pending events, and process it.
                    Iterator<SelectionKey> keys = this.instance.mServerSelector.selectedKeys().iterator();
                    while (keys.hasNext()) {
                        // Get the selection key, and remove it from the list to indicate that it's being processed
                        SelectionKey selKey = keys.next();
                        keys.remove();
                        Log.d(WiFiDirectActivity.TAG, "select : selectionkey: " + selKey.attachment());

                        try {
                            processSelectionKey(this.instance.mServerSelector, selKey);  // process the selection key.
                        } catch (IOException e) {
                            selKey.cancel();
                            Log.e(WiFiDirectActivity.TAG, "select : io exception in processing selector event: " + e.toString());
                        }
                    }
                } catch (Exception e) {  // catch all exception in select() and the following ops in mSelector.
                    Log.e(WiFiDirectActivity.TAG, "Exception in selector: " + e.toString());
//                        notifyConnectionService(MSG_SELECT_ERROR, null, null);
                    break;
                }
            }
            return 0;
        } catch (IOException e) {
            Log.e(WiFiDirectActivity.TAG, e.getMessage());
            return -1;
        } finally {
            closeServer();   // close linger server.
        }
    }

    /**
     * a device can only be either group owner, or group client, not both.
     * when we start as client, close server, if existing due to linger connection.
     */
    public void closeServer() {
        if(this.instance.mServerSocketChannel != null ){
            try{
                this.instance.mServerSocketChannel.close();
                this.instance.mServerSelector.close();
            }catch(Exception e){

            }finally{
                this.instance.mServerSocketChannel = null;
                this.instance.mServerSelector = null;
                this.instance.mServerAddr = null;
            }
        }
    }

    /**
     * process the event popped to the selector
     */
    public void processSelectionKey(Selector selector, SelectionKey selKey) throws IOException {
        if (selKey.isValid() && selKey.isAcceptable()) {  // there is a connection to the server socket channel
            ServerSocketChannel ssChannel = (ServerSocketChannel)selKey.channel();
            SocketChannel sChannel = ssChannel.accept();  // accept the connect and get a new socket channel.
            ConnectionManager.getInstance().mServerSocketChannelData = sChannel;
            sChannel.configureBlocking(false);

            // let the selector monitor read/write the accepted connections.
            SelectionKey socketKey = sChannel.register(selector, SelectionKey.OP_READ );
            socketKey.attach("accepted_client " + sChannel.socket().getInetAddress().getHostAddress());
            Log.d(WiFiDirectActivity.TAG, "processSelectionKey : accepted a client connection: " + sChannel.socket().getInetAddress().getHostAddress());
//                notifyConnectionService(MSG_NEW_CLIENT, sChannel, null);
        } else if (selKey.isValid() && selKey.isConnectable()) {   // client connect to server got the response.
            SocketChannel sChannel = (SocketChannel)selKey.channel();

            boolean success = sChannel.finishConnect();
            if (!success) {
                // An error occurred; unregister the channel.
                selKey.cancel();
                Log.e(WiFiDirectActivity.TAG, " processSelectionKey : finish connection not success !");
            }
            Log.d(WiFiDirectActivity.TAG, "processSelectionKey : this client connect to remote success: ");
//                notifyConnectionService(MSG_FINISH_CONNECT, sChannel, null);
            //mOutChannels.put(Integer.toString(sChannel.socket().getLocalPort()), sChannel);
        } else if (selKey.isValid() && selKey.isReadable()) {
            // Get channel with bytes to read
            SocketChannel sChannel = (SocketChannel)selKey.channel();
            Object attachment = selKey.attachment();
            Log.d(WiFiDirectActivity.TAG, "processSelectionKey : remote client is readable, read data: " + attachment);
            // we can retrieve the key we attached earlier, so we now what to do / where the data is coming from
            // MyIdentifierType myIdentifier = (MyIdentifierType)key.attachment();
            // myIdentifier.readTheData();
            doReadable(sChannel);
        }
    }

    /**
     * read data when OP_READ event
     */
    public String readData(SocketChannel sChannel) {
        ByteBuffer buf = ByteBuffer.allocate(1024*4);   // let's cap json string to 4k for now.
        byte[] bytes = null;
        String jsonString = null;

        try {
            buf.clear();  // Clear the buffer and read bytes from socket
            int numBytesRead = sChannel.read(buf);
            if (numBytesRead == -1) {
                // read -1 means socket channel is broken. remove it from the selector
                Log.e(WiFiDirectActivity.TAG, "readData : channel closed due to read -1: ");
                sChannel.close();  // close the channel.
//                    notifyConnectionService(MSG_BROKEN_CONN, sChannel, null);
                // sChannel.close();
            } else {
                Log.d(WiFiDirectActivity.TAG, "readData: bufpos: limit : " + buf.position() + ":" + buf.limit() + " : " + buf.capacity());
                buf.flip();  // make buffer ready for read by flipping it into read mode.
                Log.d(WiFiDirectActivity.TAG, "readData: bufpos: limit : " + buf.position() + ":" + buf.limit() + " : " + buf.capacity());
                bytes = new byte[buf.limit()];  // use bytes.length will cause underflow exception.
                buf.get(bytes);
                // while ( buf.hasRemaining() ) buf.get();
                jsonString = new String(bytes);  // convert byte[] back to string.
            }
        }catch(Exception e){
            Log.e(WiFiDirectActivity.TAG, "readData : exception: " + e.toString());
//                notifyConnectionService(MSG_BROKEN_CONN, sChannel, null);
        }

        Log.d(WiFiDirectActivity.TAG, "readData: content: " + jsonString);
        return jsonString;
    }

    /**
     * handle the readable event from selector
     */
    public void doReadable(SocketChannel schannel){
        String data = readData(schannel);
        if( data != null ){
            Bundle b = new Bundle();
            b.putString("DATA", data);
//                notifyConnectionService(MSG_PULLIN_DATA, schannel, b);
        }
    }
}
