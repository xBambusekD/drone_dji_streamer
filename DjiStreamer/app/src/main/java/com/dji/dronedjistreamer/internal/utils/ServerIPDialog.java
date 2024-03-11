package com.dji.dronedjistreamer.internal.utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatDialogFragment;

import com.dji.dronedjistreamer.R;

public class ServerIPDialog extends AppCompatDialogFragment {
    private EditText editServerIP;
    private EditText editServerPort;
    private EditText editServerRTMP;
    private ServerIPDialogListener listener;

    private String serverIP = "";
    private String serverPort = "";
    private String serverRTMP = "";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.server_ip_dialog, null);

        builder.setView(view)
                .setTitle("Connection to the Server")
                .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setPositiveButton("connect", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String ip = editServerIP.getText().toString();
                        String port = editServerPort.getText().toString();
                        String rtmp = editServerRTMP.getText().toString();
                        listener.applyInputs(ip, port, rtmp);
                    }
                });

        editServerIP = view.findViewById(R.id.edit_server_ip);
        editServerPort = view.findViewById(R.id.edit_server_port);
        editServerRTMP = view.findViewById(R.id.edit_server_rtmp);

        if(!serverIP.isEmpty()) {
            editServerIP.setHint(serverIP);
            editServerIP.setText(serverIP);
        }
        if(!serverPort.isEmpty()) {
            editServerPort.setHint(serverPort);
            editServerPort.setText(serverPort);
        }
        if(!serverRTMP.isEmpty()) {
            editServerRTMP.setHint(serverRTMP);
            editServerRTMP.setText(serverRTMP);
        }

        return builder.create();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            listener = (ServerIPDialogListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement ServerIPDialogListener");
        }
    }

    public interface ServerIPDialogListener {
        void applyInputs(String ip, String port, String rtmp);
    }

    public void SetHint(String serverIP, String serverPort, String serverRTMP) {
        this.serverIP = serverIP;
        this.serverPort = serverPort;
        this.serverRTMP = serverRTMP;
    }
}
