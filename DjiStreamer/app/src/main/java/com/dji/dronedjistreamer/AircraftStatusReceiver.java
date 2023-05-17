package com.dji.dronedjistreamer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class AircraftStatusReceiver extends BroadcastReceiver {

    private AircraftStatusListener mListener;

    public AircraftStatusReceiver(AircraftStatusListener listener) {
        mListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // Receives events on aircraft connection, disconnection, and change.
        // Notifies listener (MainActivity) about those connection events.
        mListener.onAircraftStatusChanged(intent.getAction());
    }

    public interface AircraftStatusListener {
        void onAircraftStatusChanged(String aircraftStatus);
    }
}
