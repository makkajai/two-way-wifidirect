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

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment {

    private View mContentView = null;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mContentView = inflater.inflate(R.layout.device_detail, null);
        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        ((WiFiDirectActivity) getActivity()).disconnectClicked();
                    }
                });

        mContentView.findViewById(R.id.send_message).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        ((WiFiDirectActivity)getActivity()).sendMessage();
                    }
                });

        return mContentView;
    }

    /**
     * Updates the UI with device data
     *
     */
    public void onConnectionSuccessful() {
        this.getView().setVisibility(View.VISIBLE);
        mContentView.findViewById(R.id.send_message).setVisibility(View.VISIBLE);
    }

    /**
     * Clears the UI fields after a disconnectClicked or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.send_message).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }
}

