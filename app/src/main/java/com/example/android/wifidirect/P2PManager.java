package com.example.android.wifidirect;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by batman on 04/08/16.
 */
public class P2PManager  implements WifiP2pManager.ChannelListener,
                                    WifiP2pManager.PeerListListener,
                                    WifiP2pManager.ConnectionInfoListener {

    private static final String TAG = "P2PManager";
    private static final P2PManager instance = new P2PManager();

    private boolean isWifiP2pEnabled;
    private boolean isServer;

    private Activity activity = null;

    private AsyncTask<Void, Void, Integer> task = null;

    private WifiP2pManager manager = null;

    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager.Channel channel = null;
    private BroadcastReceiver receiver = null;
    private WifiP2pDevice device = null;
    private List<WifiP2pDevice> peers = new ArrayList<>();

    public static P2PManager getInstance() {
        return instance;
    }

    public void initialize(Activity activity) {
        this.activity = activity;
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

    }

    public void startReceiver() {
        if(receiver == null) {
            manager = (WifiP2pManager) activity.getSystemService(Context.WIFI_P2P_SERVICE);
            channel = manager.initialize(activity, activity.getMainLooper(), null);
            receiver = new WiFiDirectBroadcastReceiver(manager, channel);
            activity.registerReceiver(receiver, intentFilter);
        }
    }

    public void stopReceiver() {
        if(receiver != null) {
            this.disconnectOnly();
            activity.unregisterReceiver(receiver);
            receiver = null;
            channel = null;
            manager = null;
        }
    }

    public void discoverPeers() {
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(int reasonCode) {
            }
        });
    }

    public void cancelDisconnect() {
        /*
         * A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (manager != null) {
            if (device == null
                    || device.status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else if (device.status == WifiP2pDevice.AVAILABLE
                    || device.status == WifiP2pDevice.INVITED) {
                manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                    }
                });
            }
        }
    }

    public void connect(WifiP2pConfig config) {
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
            }
        });

    }

    public void disconnect() {
        disconnectOnly();
        this.onChannelDisconnected();
    }

    private void disconnectOnly() {
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
            }

            @Override
            public void onSuccess() {
            }

        });
        terminateTask();
        this.device = null;
    }

    @Override
    public void onChannelDisconnected() {
        // we will try once more
        ConnectionManager.getInstance().setDisconnectNow(true);
        ((P2PListener)activity).onAfterDisconnect();
    }

    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    public boolean isWifiP2pEnabled() {
        return isWifiP2pEnabled;
    }

    public void setWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
        peers.clear();
        peers.addAll(wifiP2pDeviceList.getDeviceList());

        ((P2PListener)activity).onPeersAvailable(peers);
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if(info.groupFormed) {
            ConnectionManager.getInstance().setDisconnectNow(false);
            ((P2PListener)activity).onConnectionSuccessful();
            terminateTask();
        }
        if (info.groupFormed && info.isGroupOwner) {
            task = ConnectionManager.getInstance().new ServerAsyncTask(activity);
            task.execute();
            setIsServer(true);
        } else if (info.groupFormed) {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            task = ConnectionManager.getInstance().new ClientAsyncTask(activity, info.groupOwnerAddress.getHostAddress());
            task.execute();
            isServer = false;
        }
        //TODO: Should show the button to actually send the message -- DEEP
    }

    private void terminateTask() {
        if(task != null) {
            task.cancel(true);
            task = null;
        }
    }

    private void setIsServer(boolean isServer) {
        this.isServer = isServer;
    }

    public boolean isServer() {
        return isServer;
    }

    public void connectTo(WifiP2pDevice device) {
        this.device = device;
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        connect(config);
    }

    public void sendMessage() {
        if(isServer())
            ConnectionManager.getInstance().pushOutData("SERVERERE: Hello world from server!");
        else
            ConnectionManager.getInstance().pushOutData("CLIERNTE: Hello world from client!");

    }
}
