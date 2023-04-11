package com.dji.dronedjistreamer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dji.dronedjistreamer.internal.utils.ServerIPDialog;
import com.dji.dronedjistreamer.internal.utils.ToastUtils;
import com.dji.dronedjistreamer.internal.utils.VideoFeedView;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.NotYetConnectedException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.gimbal.GimbalState;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.keysdk.FlightControllerKey;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.sdkmanager.LiveStreamManager;
import dji.sdk.useraccount.UserAccountManager;
import dji.thirdparty.org.java_websocket.WebSocket;
import dji.thirdparty.org.java_websocket.client.WebSocketClient;
import dji.thirdparty.org.java_websocket.exceptions.WebsocketNotConnectedException;
import dji.thirdparty.org.java_websocket.handshake.ServerHandshake;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.ux.widget.FPVWidget;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ServerIPDialog.ServerIPDialogListener {

    private static final String TAG = MainActivity.class.getName();

    private static final String liveShowUrl = "rtmp://147.229.14.181:1935/live/dji_mavic";
    private LiveStreamManager.OnLiveChangeListener listener;
    public static WebSocketClient webSocketClient;
    private Button startLiveShowBtn;
    private Button stopLiveShowBtn;
//    private Button isLiveShowOnBtn;
//    private Button showInfoBtn;
    private Button setServerIPBtn;

    private SharedPreferences sharedPreferences;

    private String serverIP = "";
    private String serverPort = "";
    private String serverRTMP = "";

    private GimbalState gimbalState;

    private Handler connectionHandler;
    private final int connectionRetry = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECORD_AUDIO
                    }
                    , 1);
        }

        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        serverIP = sharedPreferences.getString("serverIP", "");
        serverPort = sharedPreferences.getString("serverPort", "");
        serverRTMP = sharedPreferences.getString("serverRTMP", "rtmp://" + (serverIP.isEmpty() ? "server_ip" : serverIP) + ":1935/live/dji_mavic");

        connectionHandler = new Handler();

        if(!serverIP.isEmpty() && !serverPort.isEmpty()) {
            connectToServer(serverIP, serverPort);
//            if(webSocketClient == null) {
//                createWebSocketClient(serverIP, serverPort);
//                webSocketClient.connect();
//            }
        }
//        Aircraft aircraft = (Aircraft) DJISDKManager.getInstance().getProduct();
//        FlightController flightController = aircraft.getFlightController();

//        Handler handler = new Handler();
//        int delay = 100;
//        //DJISDKManager.getInstance().getFlightHubManager().getAircraftRealTimeFlightData();
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                //LocationCoordinate3D location = flightController.getState().getAircraftLocation();
//                //webSocketClient.send("DJI: altitude: " + location.getAltitude() + " | latitude: " + location.getLatitude() + " | longitude: " + location.getLongitude());
//                if(DJISDKManager.getInstance() != null) {
//                    if(DJISDKManager.getInstance().getProduct() != null) {
//                        Aircraft aircraft = (Aircraft) DJISDKManager.getInstance().getProduct();
//                        FlightControllerState state = aircraft.getFlightController().getState();
//                        LocationCoordinate3D location = state.getAircraftLocation();
//                        Attitude attitude = state.getAttitude();
//                        float compass = aircraft.getFlightController().getCompass().getHeading();
//
//                        webSocketClient.send("{\"DroneId\":\"DJI-" + aircraft.getModel() + "\",\"Altitude\":" + location.getAltitude() + ",\"Latitude\":"
//                                + location.getLatitude() + ",\"Longitude\":" + location.getLongitude()
//                                + ",\"Pitch\":" + attitude.pitch + ",\"Roll\":" + attitude.roll + ",\"Yaw\":" + attitude.yaw
//                                + ",\"Compass\":" + compass
//                                + ",\"VelocityX\":" + state.getVelocityX() + ",\"VelocityY\":" + state.getVelocityY() + ",\"VelocityZ\":" + state.getVelocityZ() + "}");
//                    }
//                }
//                handler.postDelayed(this, delay);
//            }
//        }, delay);

        ToastUtils.setResultToToast("MainActivity");

        startLiveShowBtn = (Button) findViewById(R.id.btn_start_live_show);
        stopLiveShowBtn = (Button) findViewById(R.id.btn_stop_live_show);
//        isLiveShowOnBtn = (Button) findViewById(R.id.btn_is_live_show_on);
//        showInfoBtn = (Button) findViewById(R.id.btn_show_info);
        setServerIPBtn = (Button) findViewById(R.id.btn_set_server_ip);

        startLiveShowBtn.setOnClickListener(this);
        stopLiveShowBtn.setOnClickListener(this);
//        isLiveShowOnBtn.setOnClickListener(this);
//        showInfoBtn.setOnClickListener(this);
        setServerIPBtn.setOnClickListener(this);

        listener = new LiveStreamManager.OnLiveChangeListener() {
            @Override
            public void onStatusChanged(int i) {
                ToastUtils.setResultToToast("status changed : " + i);
            }
        };
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        if(isLiveStreamManagerOn()) {
            DJISDKManager.getInstance().getLiveStreamManager().registerListener(listener);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if(isLiveStreamManagerOn()) {
            DJISDKManager.getInstance().getLiveStreamManager().unregisterListener(listener);
        }
    }

    private void startLiveShow() {
        //Toast.makeText(getApplicationContext(), "Start Live Show", Toast.LENGTH_LONG).show();

        if (!isLiveStreamManagerOn()) {
            return;
        }

        if(isLiveStreamManagerOn()) {
            DJISDKManager.getInstance().getLiveStreamManager().registerListener(listener);
        }

        //LiveStreamManager liveStreamManager = DJISDKManager.getInstance().getLiveStreamManager();

        ToastUtils.setResultToToast("Checking if streaming is on");
        if (DJISDKManager.getInstance().getLiveStreamManager().isStreaming()) {
            ToastUtils.setResultToToast("already started!");
            return;
        }
        new Thread() {
            @Override
            public void run() {
                DJISDKManager.getInstance().getLiveStreamManager().setLiveUrl(serverRTMP);
                int result = DJISDKManager.getInstance().getLiveStreamManager().startStream();
                DJISDKManager.getInstance().getLiveStreamManager().setStartTime();

                ToastUtils.setResultToToast("startLive:" + result +
                        "\n isVideoStreamSpeedConfigurable:" + DJISDKManager.getInstance().getLiveStreamManager().isVideoStreamSpeedConfigurable() +
                        "\n isLiveAudioEnabled:" + DJISDKManager.getInstance().getLiveStreamManager().isLiveAudioEnabled());
            }
        }.start();
    }

    private void stopLiveShow() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        DJISDKManager.getInstance().getLiveStreamManager().stopStream();
        ToastUtils.setResultToToast("Stop Live Show");
    }

    private void isLiveShowOn() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        ToastUtils.setResultToToast("Is Live Show On:" + DJISDKManager.getInstance().getLiveStreamManager().isStreaming());
    }

    private void showInfo() {
        if (!isLiveStreamManagerOn()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Video BitRate:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoBitRate()).append(" kpbs\n");
        sb.append("Audio BitRate:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveAudioBitRate()).append(" kpbs\n");
        sb.append("Video FPS:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoFps()).append("\n");
        sb.append("Video Cache size:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoCacheSize()).append(" frame");
        ToastUtils.setResultToToast(sb.toString());
    }

    private boolean isLiveStreamManagerOn() {
        if (DJISDKManager.getInstance().getLiveStreamManager() == null) {
            ToastUtils.setResultToToast("No live stream manager!");
            return false;
        }
        else {
            ToastUtils.setResultToToast("Live stream manager is ON!");
        }
        return true;
    }

    private void openServerIPDialog() {
        ServerIPDialog serverIPDialog = new ServerIPDialog();
        serverIPDialog.SetHint(serverIP, serverPort, serverRTMP);
        serverIPDialog.show(getSupportFragmentManager(), "server ip dialog");
    }

    private void connectToServer(String ip, String port) {
        if(webSocketClient != null) {
            if(webSocketClient.getReadyState() == WebSocket.READYSTATE.OPEN) {
                webSocketClient.close();
            }
        }

        // Stop all previous connection attempts
        connectionHandler.removeCallbacksAndMessages(null);

        // Create new websocket client
        createWebSocketClient(ip, port);

        connectionHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d("WEBSOCKET_STATE", webSocketClient.getReadyState().toString());

                // If the websocket is not connected, repeat the connection process
                if(webSocketClient.getReadyState() != WebSocket.READYSTATE.OPEN) {
                    if(webSocketClient.getReadyState() == WebSocket.READYSTATE.CLOSED) {
                        // Recreate websocket client
                        createWebSocketClient(ip, port);
                    }
                    webSocketClient.connect();

                    connectionHandler.postDelayed(this, connectionRetry);
                }
            }
        }, connectionRetry);
    }

    private void sendFlightData() {
        Handler handler = new Handler();
        int delay = 100;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean stop = false;
                if(DJISDKManager.getInstance() != null) {
                    if(DJISDKManager.getInstance().getProduct() != null) {
                        Gimbal gimbal = DJISDKManager.getInstance().getProduct().getGimbal();
                        gimbal.setStateCallback(new GimbalState.Callback() {
                            @Override
                            public void onUpdate(@NonNull GimbalState state) {
                                gimbalState = state;
                            }
                        });
                        stop = true;
                    }
                }
                if (!stop) {
                    handler.postDelayed(this, delay);
                }
            }
        }, delay);

        //DJISDKManager.getInstance().getFlightHubManager().getAircraftRealTimeFlightData();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //LocationCoordinate3D location = flightController.getState().getAircraftLocation();
                //webSocketClient.send("DJI: altitude: " + location.getAltitude() + " | latitude: " + location.getLatitude() + " | longitude: " + location.getLongitude());
                if(DJISDKManager.getInstance() != null) {
                    if(DJISDKManager.getInstance().getProduct() != null) {
                        Aircraft aircraft = (Aircraft) DJISDKManager.getInstance().getProduct();
                        FlightControllerState state = aircraft.getFlightController().getState();
                        LocationCoordinate3D location = state.getAircraftLocation();
                        Attitude attitude = state.getAttitude();
                        float compass = aircraft.getFlightController().getCompass().getHeading();
                        float altitude = state.getTakeoffLocationAltitude() + location.getAltitude();
                        dji.common.gimbal.Attitude gimbalAttitude = gimbalState.getAttitudeInDegrees();
                        Date currentTime = Calendar.getInstance().getTime();
                        SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

                        if (webSocketClient.getReadyState() == WebSocket.READYSTATE.OPEN) {
                            webSocketClient.send("{\"DroneId\":\"DJI-" + aircraft.getModel() + "\",\"Altitude\":" +
                                    location.getAltitude() + ",\"Latitude\":"
                                    + location.getLatitude() + ",\"Longitude\":" + location.getLongitude()
                                    + ",\"Pitch\":" + attitude.pitch + ",\"Roll\":" + attitude.roll + ",\"Yaw\":" + attitude.yaw
                                    + ",\"Compass\":" + compass
                                    + ",\"VelocityX\":" + state.getVelocityX() + ",\"VelocityY\":" + state.getVelocityY() + ",\"VelocityZ\":" + state.getVelocityZ()
                                    + ",\"GimbalPitch\":" + gimbalAttitude.getPitch() + ",\"GimbalRoll\":" + gimbalAttitude.getRoll() + ",\"GimbalYaw\":" + gimbalAttitude.getYaw()
                                    + ",\"GimbalYawRelative\":" + gimbalState.getYawRelativeToAircraftHeading()
                                    + ",\"TimeStamp\":" + timestampFormat.format(currentTime) + "}");
                        }
                    }
                }
                if(webSocketClient.getReadyState() == WebSocket.READYSTATE.OPEN) {
                    handler.postDelayed(this, delay);
                }
            }
        }, delay);
    }

    public void createWebSocketClient(String ip, String port) {
        URI uri;
        try {
            //uri = new URI("ws://147.229.14.181:5555");
            uri = new URI("ws://" + ip + ":" + port);
            //uri = new URI("ws://10.42.0.1:5555");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.i(TAG, "Connected to the DroCo server.");
                ToastUtils.setResultToToast("Connected to the DroCo server.");
                // If app successfully connected to the server, start sending flight data
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sendFlightData();
                    }
                });
            }

            @Override
            public void onMessage(String s) {

            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.i(TAG, "Connection to the DroCo server closed.");
                ToastUtils.setResultToToast("Connection to the DroCo server closed.");
            }

            @Override
            public void onError(Exception e) {
                Log.i(TAG, e.toString());
            }
        };
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_start_live_show:
                startLiveShow();
                break;
            case R.id.btn_stop_live_show:
                stopLiveShow();
                break;
//            case R.id.btn_is_live_show_on:
//                isLiveShowOn();
//                break;
//            case R.id.btn_show_info:
//                showInfo();
//                break;
            case R.id.btn_set_server_ip:
                openServerIPDialog();
                break;
        }
    }

    @Override
    public void applyInputs(String ip, String port, String rtmp) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("serverIP", ip);
        editor.putString("serverPort", port);
        editor.putString("serverRTMP", rtmp);
        editor.commit();

        serverIP = ip;
        serverPort = port;
        serverRTMP = rtmp;

        connectToServer(ip, port);
    }
}

