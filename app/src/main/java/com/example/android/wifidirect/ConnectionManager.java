package com.example.android.wifidirect;

import android.content.Context;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * Created by batman on 04/08/16.
 */
public class ConnectionManager {
    private static final ConnectionManager instance = new ConnectionManager();
    public Context context;
    public String hostAddress;
    public ServerSocketChannel mServerSocketChannel = null;
    public String mClientAddr = null;
    public String mServerAddr = null;
    public Selector mClientSelector = null;
    public Selector mServerSelector = null;
    public SocketChannel mClientSocketChannel = null;
    public SocketChannel mServerSocketChannelData = null;

    public boolean disconnectNow = false;

    public static ConnectionManager getInstance() {
        return instance;
    }


    /**
     * read out -1, connection broken, remove it from clients collection
     */
    public void onBrokenConnClient(SocketChannel schannel){
        try{
            String peeraddr = schannel.socket().getInetAddress().getHostAddress();
            //closeClient();
            Log.d(WiFiDirectActivity.TAG, "onBrokenConn : set null client channel after server down: " + peeraddr);
            schannel.close();
        }catch(Exception e){
            Log.e(WiFiDirectActivity.TAG, "onBrokenConn: close channel: " + e.toString());
        }
    }

    /**
     * write byte buf to the socket channel.
     */
    private int writeData(SocketChannel sChannel, String jsonString){
        byte[] buf = jsonString.getBytes();
        ByteBuffer bytebuf = ByteBuffer.wrap(buf);  // wrap the buf into byte buffer
        int nwritten = 0;
        try {
            //bytebuf.flip();  // no flip after creating from wrap.
            Log.d(WiFiDirectActivity.TAG, "writeData: start:limit = " + bytebuf.position() + " : " + bytebuf.limit());
            nwritten = sChannel.write(bytebuf);
        } catch (Exception e) {
            // Connection may have been closed
            Log.e(WiFiDirectActivity.TAG, "writeData: exception : " + e.toString());
            onBrokenConnClient(sChannel);
        }
        Log.d(WiFiDirectActivity.TAG, "writeData: content: " + new String(buf) + "  : len: " + nwritten);
        return nwritten;
    }

    /**
     * the device want to push out data.
     * If the device is client, the only channel is to the server.
     * If the device is server, it just pub the data to all clients for now.
     */
    public int pushOutDataToServer(final String jsonString){
        new Thread(new Runnable() {

            @Override
            public void run() {
                Log.d(WiFiDirectActivity.TAG, "SENDING DATA TO SERVER: " + jsonString);
                ConnectionManager.this.sendDataToServer(jsonString);
            }
        }).start();
        return 0;
    }

    /**
     * server publish data to all the connected clients
     */
    public void pushOutDataToClient(final String msg){
        new Thread(new Runnable() {

            @Override
            public void run() {
                Log.d(WiFiDirectActivity.TAG, "pubDataToAllClients : isServer ? " + true + " msg: " + msg );
                writeData(mServerSocketChannelData, msg);
            }
        }).start();
    }

    /**
     * whenever client write to server, carry the format of "client_addr : msg "
     */
    private int sendDataToServer(String jsonString) {
        if(mClientSocketChannel == null) {
            Log.d(WiFiDirectActivity.TAG, "sendDataToServer: channel not connected ! waiting...");
            return 0;
        }
        Log.d(WiFiDirectActivity.TAG, "sendDataToServer: " + mClientAddr + " -> "
                + " : " +  jsonString);
        return writeData(mClientSocketChannel, jsonString);
    }

}
