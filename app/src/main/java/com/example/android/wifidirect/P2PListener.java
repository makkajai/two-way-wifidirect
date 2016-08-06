package com.example.android.wifidirect;

import android.net.wifi.p2p.WifiP2pDevice;

import java.util.List;

public interface P2PListener {
    void onPeersAvailable(List<WifiP2pDevice> wifiP2pDeviceList);

    void onAfterDisconnect();

    void onConnectionSuccessful();
}
