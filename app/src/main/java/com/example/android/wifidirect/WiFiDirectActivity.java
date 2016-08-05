/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wifidirect;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Bundle;
import android.os.Handler;

import java.util.List;

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
public class WiFiDirectActivity extends Activity implements P2PListener {

    public static final String TAG = "wifidirectdemo";
    private ProgressDialog progressDialog;
    private boolean isDiscoveringPeers;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // add necessary intent values to be matched.

        P2PManager.getInstance().initialize(this);
    }

    /** register the BroadcastReceiver with the intent values to be matched */
    @Override
    public void onResume() {
        super.onResume();
        discoverPeers();
    }

    public void discoverPeers() {
        isDiscoveringPeers = true;
        resetViews();
        ConnectionManager.getInstance().setDisconnectNow(false);
        P2PManager.getInstance().startReceiver();
        P2PManager.getInstance().discoverPeers();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = ProgressDialog.show(this, "Press back to cancel", "finding peers", true,
                true, new DialogInterface.OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {

                    }
                });
    }

    @Override
    public void onPause() {
        super.onPause();
        P2PManager.getInstance().stopReceiver();
    }

    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetViews() {
        DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_list);
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
        if (fragmentDetails != null) {
            fragmentDetails.resetViews();
        }
    }

    @Override
    public void onPeersAvailable(final List<WifiP2pDevice> wifiP2pDeviceList) {
        if(wifiP2pDeviceList.size() > 0) {
            isDiscoveringPeers = false;
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }

            DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager()
                    .findFragmentById(R.id.frag_list);
            fragmentList.onPeersAvailable(wifiP2pDeviceList);
        }
    }

    public void connectTo(WifiP2pDevice device) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = ProgressDialog.show(this, "Press back to cancel",
                "Connecting to :" + device.deviceAddress, true, true,
                        new DialogInterface.OnCancelListener() {

                            @Override
                            public void onCancel(DialogInterface dialog) {
                                P2PManager.getInstance().cancelDisconnect();
                            }
                        }
        );

        P2PManager.getInstance().connectTo(device);
    }

    public void disconnectClicked() {
        P2PManager.getInstance().disconnect();
    }

    public void onConnectionSuccessful() {
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragmentDetails.onConnectionSuccessful();
    }

    public void sendMessage() {
        P2PManager.getInstance().sendMessage();
    }

    public void onAfterDisconnect() {
        if(!isDiscoveringPeers) {
            Handler mainHandler = new Handler(this.getMainLooper());

            Runnable myRunnable = new Runnable() {
                @Override
                public void run() {
                    resetViews();
                    discoverPeers();
                } // This is your code
            };
            mainHandler.postDelayed(myRunnable, 1000);
        }
    }
}
