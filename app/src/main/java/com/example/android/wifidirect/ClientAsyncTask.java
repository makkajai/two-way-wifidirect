package com.example.android.wifidirect;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by batman on 04/08/16.
 */
public class ClientAsyncTask  extends AsyncTask<Void, Void, Integer> {

    private ConnectionManager instance;

    public ClientAsyncTask(Activity activity, View viewById, String hostAddress, ConnectionManager instance) {
        this.instance = instance;
        this.instance.context = activity;
        this.instance.hostAddress = hostAddress;
        this.instance.statusText = (TextView) viewById;
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
                this.instance.mClientChannels.clear();
            }
        }
    }

    @Override
    protected Integer doInBackground(Void... voids) {
        closeServer();   // close linger server.

        if(this.instance.mClientSocketChannel != null){
            Log.d(WiFiDirectActivity.TAG, "startClientSelector : client already connected to server: "
                    + this.instance.mClientSocketChannel.socket().getLocalAddress().getHostAddress());
            return -1;
        }

        try {
            // connected to the server upon start client.
            SocketChannel sChannel = connectTo(this.instance.hostAddress, 8988);

            this.instance.mClientSelector = Selector.open();
            this.instance.mClientSocketChannel = sChannel;
            this.instance.mClientAddr = this.instance.mClientSocketChannel.socket().getLocalAddress().getHostName();
            sChannel.register(this.instance.mClientSelector, SelectionKey.OP_READ);
            Log.d(WiFiDirectActivity.TAG, "startClientSelector : started: "
                    + this.instance.mClientSocketChannel.socket().getLocalAddress().getHostAddress());

            // start selector monitoring, blocking

            // Wait for events looper
            while (true) {
                try {
                    Log.d(WiFiDirectActivity.TAG, "select : selector monitoring: ");
                    this.instance.mClientSelector.select();   // blocked on waiting for event

                    Log.d(WiFiDirectActivity.TAG, "select : selector evented out: ");
                    // Get list of selection keys with pending events, and process it.
                    Iterator<SelectionKey> keys = this.instance.mClientSelector.selectedKeys().iterator();
                    while (keys.hasNext()) {
                        // Get the selection key, and remove it from the list to indicate that it's being processed
                        SelectionKey selKey = keys.next();
                        keys.remove();
                        Log.d(WiFiDirectActivity.TAG, "select : selectionkey: " + selKey.attachment());

                        try {
                            processSelectionKey(this.instance.mClientSelector, selKey);  // process the selection key.
                        } catch (IOException e) {
                            selKey.cancel();
                            Log.e(WiFiDirectActivity.TAG, "select : io exception in processing selector event: " + e.toString());
                        }
                    }
                } catch (Exception e) {  // catch all exception in select() and the following ops in mSelector.
                    Log.e(WiFiDirectActivity.TAG, "Exception in selector: " + e.toString());
//                    notifyConnectionService(MSG_SELECT_ERROR, null, null);
                    break;
                }
            }
            return 0;

        } catch(Exception e) {
            Log.e(WiFiDirectActivity.TAG, "startClientSelector : exception: " + e.toString());
            this.instance.mClientSelector = null;
            this.instance.mClientSocketChannel = null;

            return -1;
        }
    }


    /**
     * process the event popped to the selector
     */
    public void processSelectionKey(Selector selector, SelectionKey selKey) throws IOException {
        if (selKey.isValid() && selKey.isAcceptable()) {  // there is a connection to the server socket channel
            ServerSocketChannel ssChannel = (ServerSocketChannel)selKey.channel();
            SocketChannel sChannel = ssChannel.accept();  // accept the connect and get a new socket channel.
            sChannel.configureBlocking(false);

            // let the selector monitor read/write the accepted connections.
            SelectionKey socketKey = sChannel.register(selector, SelectionKey.OP_READ );
            socketKey.attach("accepted_client " + sChannel.socket().getInetAddress().getHostAddress());
            Log.d(WiFiDirectActivity.TAG, "processSelectionKey : accepted a client connection: " + sChannel.socket().getInetAddress().getHostAddress());
//            notifyConnectionService(MSG_NEW_CLIENT, sChannel, null);
        } else if (selKey.isValid() && selKey.isConnectable()) {   // client connect to server got the response.
            SocketChannel sChannel = (SocketChannel)selKey.channel();

            boolean success = sChannel.finishConnect();
            if (!success) {
                // An error occurred; unregister the channel.
                selKey.cancel();
                Log.e(WiFiDirectActivity.TAG, " processSelectionKey : finish connection not success !");
            }
            Log.d(WiFiDirectActivity.TAG, "processSelectionKey : this client connect to remote success: ");
//            notifyConnectionService(MSG_FINISH_CONNECT, sChannel, null);
            //mOutChannels.put(Integer.toString(sChannel.socket().getLocalPort()), sChannel);
        } else if (selKey.isValid() && selKey.isReadable()) {
            // Get channel with bytes to read
            SocketChannel sChannel = (SocketChannel)selKey.channel();
            Log.d(WiFiDirectActivity.TAG, "processSelectionKey : remote client is readable, read data: " + selKey.attachment());
            // we can retrieve the key we attached earlier, so we now what to do / where the data is coming from
            // MyIdentifierType myIdentifier = (MyIdentifierType)key.attachment();
            // myIdentifier.readTheData();
            doReadable(sChannel);
        } else if (selKey.isValid() && selKey.isWritable()) {
            // Not select on writable...endless loop.
            SocketChannel sChannel = (SocketChannel)selKey.channel();
            Log.d(WiFiDirectActivity.TAG, "processSelectionKey : remote client is writable, write data: ");
        }
    }

    /**
     * handle the readable event from selector
     */
    public void doReadable(SocketChannel schannel){
        String data = readData(schannel);
        Toast.makeText(this.instance.context, data, Toast.LENGTH_SHORT).show();
        if( data != null ){
            Bundle b = new Bundle();
            b.putString("DATA", data);
//            notifyConnectionService(MSG_PULLIN_DATA, schannel, b);
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
//                notifyConnectionService(MSG_BROKEN_CONN, sChannel, null);
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
//            notifyConnectionService(MSG_BROKEN_CONN, sChannel, null);
        }

        Log.d(WiFiDirectActivity.TAG, "readData: content: " + jsonString);
        return jsonString;
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
    public static SocketChannel createSocketChannel(String hostName, int port) throws IOException {
        // Create a non-blocking socket channel
        SocketChannel sChannel = SocketChannel.open();
        sChannel.configureBlocking(false);

        // Send a connection request to the server; this method is non-blocking
        sChannel.connect(new InetSocketAddress(hostName, port));
        return sChannel;
    }

}
