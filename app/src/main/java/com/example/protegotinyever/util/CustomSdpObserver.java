package com.example.protegotinyever.util;

import android.util.Log;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public class CustomSdpObserver implements SdpObserver {
    private int rea = 1;
    private static final String TAG = "CustomSdpObserver";

    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        Log.d(TAG, "SDP created successfully: " + sessionDescription.type);
    }

    @Override
    public void onSetSuccess() {
        Log.d(TAG, "SDP set successfully.");
    }

    @Override
    public void onCreateFailure(String error) {
        Log.e(TAG, "Failed to create SDP: " + error);
    }

    @Override
    public void onSetFailure(String error) {
        Log.e(TAG, "Failed to set SDP: " + error);
    }
}
