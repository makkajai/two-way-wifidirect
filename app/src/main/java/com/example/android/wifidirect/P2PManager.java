package com.example.android.wifidirect;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by batman on 04/08/16.
 */
public class P2PManager  implements WifiP2pManager.ChannelListener, DeviceActionListener, WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener {

    private static final String TAG = "P2PManager";
    private static final P2PManager instance = new P2PManager();
    private Activity activity;
    private boolean isWifiP2pEnabled;
    private boolean isServer;

    public static P2PManager getInstance() {
        return instance;
    }

    private WifiP2pManager manager;
    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver = null;
    private WifiP2pDevice device;

    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();


    public void initialize(Activity activity) {
        this.activity = activity;
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) activity.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(activity, activity.getMainLooper(), null);
    }

    public void startReceiver() {
        receiver = new WiFiDirectBroadcastReceiver(manager, channel);
        activity.registerReceiver(receiver, intentFilter);
    }

    public void stopReceiver() {
        activity.unregisterReceiver(receiver);
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

    @Override
    public void showDetails(WifiP2pDevice device) {

    }

    @Override
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
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
            }

            @Override
            public void onSuccess() {
            }

        });
        ConnectionManager.getInstance().disconnectNow = true;
        //TODO: Should reset the data too -- DEEP
    }

    @Override
    public void onChannelDisconnected() {
        // we will try once more
        ConnectionManager.getInstance().disconnectNow = true;
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
        if (info.groupFormed && info.isGroupOwner) {
            new ServerAsyncTask(activity, ConnectionManager.getInstance())
                    .execute();
            P2PManager.getInstance().setIsServer(true);
        } else if (info.groupFormed) {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            new ClientAsyncTask(activity, info.groupOwnerAddress.getHostAddress(), ConnectionManager.getInstance()).execute();
            isServer = false;
        }
        //TODO: Should show the button to actually send the message -- DEEP
    }

    public void setIsServer(boolean isServer) {
        this.isServer = isServer;
    }

    public boolean isServer() {
        return isServer;
    }

    public void setServer(boolean isServer) {
        this.isServer = isServer;
    }
}
