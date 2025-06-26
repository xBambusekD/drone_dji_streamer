package com.dji.dronedjistreamer;

import android.Manifest;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.dji.dronedjistreamer.internal.utils.AltitudeDialog;
import com.dji.dronedjistreamer.internal.utils.BinaryFileHelper;
import com.dji.dronedjistreamer.internal.utils.FileHelper;
import com.dji.dronedjistreamer.internal.utils.ServerIPDialog;
import com.dji.dronedjistreamer.internal.utils.ToastUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Calendar;
import java.util.Date;

import dji.common.battery.BatteryState;
import dji.common.error.DJIError;
import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.gimbal.GimbalState;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.remotecontroller.HardwareState;
import dji.common.util.CommonCallbacks;
import dji.liveviewar.jni.Vector2;
import dji.midware.usb.P3.UsbAccessoryService;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.products.Aircraft;
import dji.sdk.remotecontroller.RemoteController;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.sdkmanager.LiveStreamManager;
import dji.common.flightcontroller.LocationCoordinate3D;

import dji.ux.widget.FPVOverlayWidget;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.websocket.WebsocketInbound;
import reactor.netty.http.websocket.WebsocketOutbound;
import reactor.util.retry.Retry;

import java.util.Base64;
import java.util.Objects;

public class MainActivity extends FragmentActivity
        implements View.OnClickListener,
        ServerIPDialog.ServerIPDialogListener,
        AircraftStatusReceiver.AircraftStatusListener,
        AltitudeDialog.AltitudeDialogListener,
        DJICodecManager.YuvDataCallback,
        AdapterView.OnItemSelectedListener {


    public static enum READYSTATE {
        NOT_YET_CONNECTED,
        CONNECTING,
        OPEN,
        CLOSING,
        CLOSED,
        FAILURE
    }

    private static final String TAG = MainActivity.class.getName();
    private static final int FLIGHT_INTERVAL = 4;


    private LiveStreamManager.OnLiveChangeListener listener;

    private WebsocketOutbound outboundClient;
    private Disposable connection;
    private READYSTATE connectionState = READYSTATE.CLOSED;

    private Button startLiveShowBtn;
    private Button stopLiveShowBtn;
    private Button setServerIPBtn;
    private Button setAltitudeBtn;

    private ToggleButton flightLogRecordToggle;

    private Canvas videoViewCanvas;
    private TextureView fpvJpegTextureView;
    private SharedPreferences sharedPreferences;

    private String serverIP = "";
    private String serverPort = "";

    private Gimbal gimbal;
    private GimbalState gimbalState;

    private BatteryState batterState;

    private Handler connectionHandler;
    private final int connectionRetry = 5;

    private Aircraft aircraft;
    private String aircraftSerialNumber;

    private boolean aircraftConnected = false;

    private RemoteController remoteController;

    private Pair<Integer, Integer> leftStick;
    private Pair<Integer, Integer> rightStick;


    AircraftStatusReceiver djiStatusReceiver;

    private String clientID;

    private boolean recordingOn = false;
    private boolean flightRecordingOn = false;
    private boolean carDetector = false;

    private float takeoffAltitude = Float.NaN;
//    private double latestKnownLatitude = 49.201851;
//    private double latestKnownLongitude = 16.603196;
    private double latestKnownLatitude = 49.227240;
    private double latestKnownLongitude = 16.597338;
    private double latestKnownLatitudeRaw = 49.227240;
    private double latestKnownLongitudeRaw = 16.597338;

    private DJICodecManager codecManager = null;
    private String latestJPEGframe = "";

    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener = null;

    private int flightDataInterval = 0;

    private FileHelper fileHelper = null;
    private BinaryFileHelper fileHelperBinary = null;

    private boolean logFlight = false;

    private Spinner calibrationPointsSpinner;

    private double latitudeOffset = 0;
    private double longitudeOffset = 0;

    private boolean droneControlEnabled = false;

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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        serverIP = sharedPreferences.getString("serverIP", "");
        serverPort = sharedPreferences.getString("serverPort", "");

        takeoffAltitude = Float.parseFloat(sharedPreferences.getString("altitude", "0f"));

        connectionHandler = new Handler();

        IntentFilter filter = new IntentFilter();
        filter.addAction(DemoApplication.FLAG_AIRCRAFT_CONNECTED);
        filter.addAction(DemoApplication.FLAG_AIRCRAFT_DISCONNECTED);
        filter.addAction(DemoApplication.FLAG_PRODUCT_CHANGED);
        filter.addAction(DemoApplication.FLAG_COMPONENT_CONNECTIVITY_CHANGED);

        djiStatusReceiver = new AircraftStatusReceiver(this);
        registerReceiver(djiStatusReceiver, filter);

        startLiveShowBtn = (Button) findViewById(R.id.btn_start_live_show);
        stopLiveShowBtn = (Button) findViewById(R.id.btn_stop_live_show);
        setServerIPBtn = (Button) findViewById(R.id.btn_set_server_ip);
        setAltitudeBtn = (Button) findViewById(R.id.btn_set_altitude);
        flightLogRecordToggle = (ToggleButton) findViewById(R.id.toggle_flight_log_record);

        startLiveShowBtn.setOnClickListener(this);
        stopLiveShowBtn.setOnClickListener(this);
        setServerIPBtn.setOnClickListener(this);
        setAltitudeBtn.setOnClickListener(this);
        flightLogRecordToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    fileHelper = new FileHelper(getApplicationContext(), "flight_log_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime()) + ".txt");
                    fileHelperBinary = new BinaryFileHelper(getApplicationContext(), "flight_log_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime()) + ".bin");
                    logFlight = true;
                } else {
                    if(logFlight && fileHelper != null && fileHelperBinary != null) {
                        closeLogFile();
                    }
                    logFlight = false;
                }
            }
        });

        fpvJpegTextureView = (TextureView) findViewById(R.id.fpv_jpeg_video_feed_texture);

        calibrationPointsSpinner = (Spinner) findViewById(R.id.calibration_points_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.calibrationPoints,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        calibrationPointsSpinner.setAdapter(adapter);
        calibrationPointsSpinner.setOnItemSelectedListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeConnection();
    }

    private void closeConnection() {
        if(logFlight && fileHelper != null && fileHelperBinary != null) {
            fileHelper.close();
            fileHelperBinary.close();
        }

        if(outboundClient != null) {
            outboundClient.sendClose() // Initiate a close handshake
                    .then()
                    .doOnSuccess(unused -> {
                        Log.d(TAG, "WebSocket close frame sent.");
                        // Clean up after successful close
                        outboundClient = null; // Clear the outbound client reference
                        connectionState = READYSTATE.CLOSED;
                    })
                    .doOnError(error -> Log.e(TAG, "Failed to send close frame", error))
                    .subscribe();
        }

        if(connection != null) {
            connection.dispose();
            connection = null;
        }
    }

    private void initPreviewerTextureView() {
        Log.d(TAG, "FPV: initPreviewerTextureView");
        fpvJpegTextureView.setVisibility(View.VISIBLE);
        if(fpvJpegTextureView.isAvailable()){
            Log.d(TAG, "FPV: texture view already available");
            if(codecManager == null) {
                Log.d(TAG, "FPV: creating codecManager");
                codecManager = new DJICodecManager(getApplicationContext(), fpvJpegTextureView.getSurfaceTexture(), fpvJpegTextureView.getWidth(), fpvJpegTextureView.getHeight(), UsbAccessoryService.VideoStreamSource.Camera);
            }
        }
        fpvJpegTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
                Log.d(TAG, "FPV: real onSurfaceTextureAvailable: width " + width + " height " + height);
                if(codecManager == null) {
                    Log.d(TAG, "FPV: creating codecManager");
                    codecManager = new DJICodecManager(getApplicationContext(), surfaceTexture, width, height, UsbAccessoryService.VideoStreamSource.Camera);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                if(codecManager != null) {
                    codecManager.cleanSurface();
                }
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

            }
        });
    }

    private void initVideoFeeder() {
        Log.d(TAG, "FPV: initVideoFeeder");
        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataListener = new VideoFeeder.VideoDataListener() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                //Log.d(TAG, "FPV: new image data received");
                if (codecManager != null) {
                    //Log.d(TAG, "FPV: sending image to codecManager");
                    codecManager.sendDataToDecoder(videoBuffer, size);
                }
            }
        };

        if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
            Log.d(TAG, "FPV: addVideoDataListener");
            VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(mReceivedVideoDataListener);
        }
    }

    private void startLiveShow() {
        runSyncedJPEGStream();
    }

    private void createCodecManager() {
        if(codecManager != null) {
            codecManager.enabledYuvData(true);
            codecManager.setYuvDataCallback(this);
        }
    }

    private void runSyncedJPEGStream() {
        createCodecManager();
    }

    private void stopLiveShow() {
        stopSyncedJPEGStream();
    }

    private void stopSyncedJPEGStream() {
        if(codecManager != null) {
            codecManager.cleanSurface();
            codecManager.destroyCodec();
            codecManager = null;
        }

        latestJPEGframe = "";
    }

    private void openServerIPDialog() {
        ServerIPDialog serverIPDialog = new ServerIPDialog();
        serverIPDialog.SetHint(serverIP, serverPort);
        serverIPDialog.show(getSupportFragmentManager(), "server ip dialog");
    }

    private void openAltitudeDialog() {
        AltitudeDialog altitudeDialog = new AltitudeDialog();
        altitudeDialog.SetHint(String.valueOf(takeoffAltitude));
        altitudeDialog.show(getSupportFragmentManager(), "altitude dialog");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void connectToServer(String ip, String port) {
        connection = HttpClient.create()
                .websocket()
                .uri("ws://" + ip + ":" + port + "/")
                .handle((inbound, outbound) -> {
                    this.outboundClient = outbound; // Save outbound for later use
                    return doServerHandshake(inbound, outbound)
                            .then(handleWebSocket(inbound, outbound));
                })
                .doOnSubscribe(subscription -> {
                    Log.d(TAG, "Connected to WebSocket server");
                })
                .doOnTerminate(() -> {
                    Log.d(TAG, "Disconnected from WebSocket server");
                    connectionState = READYSTATE.CLOSED;
                })
                .doOnError(error -> {
                    Log.e(TAG, "WebSocket connection failed", error);
                    connectionState = READYSTATE.FAILURE;
                })
                .retryWhen(Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(connectionRetry))
                        .doBeforeRetry(retrySignal -> Log.d(TAG, "Retrying connection...")))
                .subscribe();
    }

    private Mono<Void> handleWebSocket(WebsocketInbound inbound, WebsocketOutbound outbound) {
        // Log incoming messages
        inbound.receive()
                .asString()
                .doOnNext(message -> {
                    Log.d(TAG, "Received message: " + message);
                    try {
                        JSONObject receivedMsg = new JSONObject(message);
                        String msgType = receivedMsg.getString("type");
                        switch (msgType) {
                            case "hello_resp":
                                handleServerHandshake(receivedMsg.getJSONObject("data"));
                                break;
                            case "enable_control":
                                handleEnableControl(receivedMsg.getJSONObject("data"));
                                break;
                            case "control_command":
                                if(droneControlEnabled) {
                                    handleControlCommand(receivedMsg.getJSONObject("data"));
                                } else {
                                    Log.d("CONTROLS", "Drone control is not enabled!");
                                }
                                break;
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "On message received", e);
                    }
                })
                .subscribe();

        return Mono.never();
    }

    private void handleEnableControl(JSONObject msg) throws JSONException {
        boolean enable = msg.getBoolean("enable");

        FlightController flightController = aircraft.getFlightController();
        flightController.setVirtualStickModeEnabled(enable, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    flightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
                    flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
                    flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
                    flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
                    droneControlEnabled = enable;
                    Toast.makeText(getApplicationContext(),
                            "Virtual stick mode " + (enable ? "enabled" : "disabled") + " successfully",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Failed to " + (enable ? "enable" : "disable") + " virtual stick mode: " + djiError.getDescription(),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void handleControlCommand(JSONObject msg) throws JSONException {
        Log.d("CONTROLS", msg.toString());
        if (aircraft == null || aircraft.getFlightController() == null) {
            Log.e(TAG, "FlightController not available");
            return;
        }

        FlightController flightController = aircraft.getFlightController();

        // Parse input values, default to 0 if missing
        float pitch = (float) msg.optDouble("pitch", 0.0);       // Forward/backward (m/s)
        float roll = (float) msg.optDouble("roll", 0.0);         // Left/right (m/s)
        float yaw = (float) msg.optDouble("yaw", 0.0);           // Yaw rate (deg/s)
        float throttle = (float) msg.optDouble("throttle", 0.0); // Vertical speed (m/s)

        // According to DJI official documentation, the constructor for FlightControlData should be like this:
        //FlightControlData controlData = new FlightControlData(pitch, roll, yaw, throttle);
        // But when testing on DJI Mini 1, the roll is switched with pitch.
        FlightControlData controlData = new FlightControlData(roll, pitch, yaw, throttle);

        //Log.d("CONTROLS", controlData.toString());

        // Send command to the drone
        flightController.sendVirtualStickFlightControlData(controlData, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError != null) {
                    Log.e("CONTROLS", "Failed to send control data: " + djiError.getDescription());
                } else {
                    Log.d("CONTROLS", String.format("Sent control data - Pitch: %.2f, Roll: %.2f, Yaw: %.2f, Throttle: %.2f",
                            pitch, roll, yaw, throttle));
                }
            }
        });

        float gimbalPitch = (float) msg.optDouble("gimbal_pitch", 0.0);

        if (gimbal != null) {
            Rotation rotation = new Rotation.Builder()
                    .pitch(gimbalPitch)
                    .roll(0)
                    .yaw(0)
                    .time(1)
                    .mode(RotationMode.RELATIVE_ANGLE)
                    .build();

            gimbal.rotate(rotation, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) {
                        Log.e("CONTROLS", "Failed to rotate gimbal: " + djiError.getDescription());
                    } else {
                        Log.d("CONTROLS", "Gimbal rotated to pitch: " + gimbalPitch);
                    }
                }
            });
        } else {
            gimbal = aircraft.getGimbal();
        }
    }

    private void disableVirtualStickMode() {
        aircraft.getFlightController().setVirtualStickModeEnabled(false, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    Toast.makeText(getApplicationContext(), "Virtual stick mode disabled due to manual override", Toast.LENGTH_LONG).show();
                    Log.i("RC_OVERRIDE", "Virtual stick mode disabled");
                } else {
                    Log.e("RC_OVERRIDE", "Failed to disable virtual stick mode: " + djiError.getDescription());
                }
            }
        });
    }

    private double getTakeoffElevationFromGoogleMaps(double latitude, double longitude) {
        double result = Double.NaN;
        try {
            URL url = new URL("https://maps.googleapis.com/maps/api/elevation/json?locations=" + latitude + "%2C" + longitude + "&key=API_KEY");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                Log.i("GOOGLE_ELEVATION", response.toString());
            }
        } catch (MalformedURLException e) {
            Log.e(TAG, "Getting elevation from google maps", e);
        } catch (IOException e) {
            Log.e(TAG, "Getting elevation from google maps", e);
        }
//
//        OkHttpClient client = new OkHttpClient().newBuilder().build();
//        MediaType mediaType = MediaType.parse("text/plain");
//        RequestBody body = RequestBody.create("", mediaType);
//        HttpGet
//        Request request = new Request.Builder()
//                .url("https://maps.googleapis.com/maps/api/elevation/json?locations=" + latitude + "%2C" + longitude + "&key=API_KEY")
//                .method("GET", null)
//                .build();
//        try (Response response = client.newCall(request).execute()) {
//            String jsonData = response.body().string();
//            Log.i("GOOGLE_ELEVATION", jsonData);
//            JSONObject elevationResponse = new JSONObject(jsonData);
//            JSONArray elevationArray = elevationResponse.getJSONArray("results");
//            try {
//                JSONObject object = elevationArray.getJSONObject(0);
//                result = object.getDouble("elevation");
//                Log.i("GOOGLE_ELEVATION", String.valueOf(result));
//            } catch (NullPointerException e) {
//                e.printStackTrace();
//            }
//        } catch (JSONException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        return result;
    }


    private Mono<Void> doServerHandshake(WebsocketInbound inbound, WebsocketOutbound outbound) {
        JSONObject msg = new JSONObject();
        JSONObject data = new JSONObject();
        Log.d(TAG, "Trying to handshake");
        try {
            msg.put("type", "hello");
            data.put("ctype", 0);
            data.put("drone_name", aircraft.getModel());
            data.put("serial", aircraftSerialNumber);
            msg.put("data", data);

            if(logFlight && fileHelper != null && fileHelperBinary != null) {
                fileHelper.writeLine(msg.toString());
            }
            return outbound.sendString(Mono.just(msg.toString()))
                    .then()
                    .doOnSuccess(unused -> Log.d(TAG, "Message sent successfully"))
                    .doOnError(error -> Log.e(TAG, "Failed to send message", error));

        } catch (JSONException e) {
            Log.e(TAG, "Doing server handshake", e);
            return Mono.error(e);
        }
    }

    private void handleServerHandshake(JSONObject msg) throws JSONException {
        clientID = msg.getString("client_id");
        Log.i(TAG, msg.toString());
        connectionState = READYSTATE.OPEN;
    }

    private void initGimbalCallback() {
        if (gimbal == null) {
            gimbal = aircraft.getGimbal();
        }
        gimbal.setStateCallback(new GimbalState.Callback() {
            @Override
            public void onUpdate(@NonNull GimbalState state) {
                gimbalState = state;
            }
        });
    }

    private void initBatteryCallback() {
        aircraft.getBattery().setStateCallback(new BatteryState.Callback() {
            @Override
            public void onUpdate(BatteryState state) {
                batterState = state;
            }
        });
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
            case R.id.btn_set_server_ip:
                openServerIPDialog();
                break;
            case R.id.btn_set_altitude:
                openAltitudeDialog();
                break;
        }
    }

    private void closeLogFile() {
        fileHelper.close();
        fileHelperBinary.close();

        fileHelper = null;
        fileHelperBinary = null;

        Toast.makeText(getApplicationContext(), "Save file closed", Toast.LENGTH_LONG).show();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void applyInputs(String ip, String port) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("serverIP", ip);
        editor.putString("serverPort", port);
        editor.commit();

        serverIP = ip;
        serverPort = port;

        if(aircraftConnected) {
            if(connectionState == READYSTATE.OPEN) {
                closeConnection();
            }
            connectToServer(ip, port);
        }
    }

    @Override
    public void applyInputs(String altitude) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("altitude", altitude);
        editor.commit();

        takeoffAltitude = Float.parseFloat(altitude);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onAircraftStatusChanged(String aircraftStatus) {
        switch (aircraftStatus) {
            case DemoApplication.FLAG_AIRCRAFT_CONNECTED:
                aircraftConnected = true;
                aircraft = DemoApplication.getAircraftInstance();
                aircraft.getFlightController().getSerialNumber(new CommonCallbacks.CompletionCallbackWith<String>() {
                    @Override
                    public void onSuccess(String s) {
                        aircraftSerialNumber = s;
                    }

                    @Override
                    public void onFailure(DJIError djiError) {
                        aircraftSerialNumber = "";
                    }
                });
                // Aircraft connected, connect to the server and start sending data.
                if(!serverIP.isEmpty() && !serverPort.isEmpty()) {
                    connectToServer(serverIP, serverPort);
                }

                initPreviewerTextureView();
                initVideoFeeder();
                initGimbalCallback();
                initBatteryCallback();

                remoteController = aircraft.getRemoteController();
                remoteController.setHardwareStateCallback(new HardwareState.HardwareStateCallback() {
                    @Override
                    public void onUpdate(@NonNull HardwareState hardwareState) {
                        leftStick = Pair.create(hardwareState.getLeftStick().getHorizontalPosition(), hardwareState.getLeftStick().getVerticalPosition());
                        rightStick = Pair.create(hardwareState.getRightStick().getHorizontalPosition(), hardwareState.getRightStick().getVerticalPosition());

//                        Log.d("RC_Sticks", "Left Stick: (" + leftStickX + ", " + leftStickY + ")");
//                        Log.d("RC_Sticks", "Right Stick: (" + rightStickX + ", " + rightStickY + ")");

                        if (droneControlEnabled && isManualOverrideDetected(leftStick.first, leftStick.second, rightStick.first, rightStick.second)) {
                            Log.w("CONTROLS", "Manual input detected! Disabling virtual stick mode.");
                            disableVirtualStickMode(); // custom method that sets enable = false
                            droneControlEnabled = false;
                            // Optionally: notify Unity/server about the override
                        }
                    }
                });

                Log.i("MainActivity", "aircraft connected");
                break;
            case DemoApplication.FLAG_AIRCRAFT_DISCONNECTED:
                aircraftConnected = false;

                Log.i("MainActivity", "aircraft disconnected");
                break;
            case DemoApplication.FLAG_PRODUCT_CHANGED:
                Log.i("MainActivity", "product changed");
                break;
            case DemoApplication.FLAG_COMPONENT_CONNECTIVITY_CHANGED:
                Log.i("MainActivity", "component connectivity changed");
                break;
        }
    }

    private boolean isManualOverrideDetected(float lx, float ly, float rx, float ry) {
        float threshold = 0.05f; // small deadzone to ignore jitter
        return Math.abs(lx) > threshold || Math.abs(ly) > threshold ||
                Math.abs(rx) > threshold || Math.abs(ry) > threshold;
    }

    private byte[] parseToJPEG(byte[] yuvFrame, int width, int height) {

        if (yuvFrame.length < width * height) {
            return null;
        }
        int length = width * height;

        byte[] u = new byte[width * height / 4];
        byte[] v = new byte[width * height / 4];

        for (int i = 0; i < u.length; i ++) {
            u[i] = yuvFrame[length + i];
            v[i] = yuvFrame[length + u.length + i];
        }
        for (int i = 0; i < u.length; i++) {
            yuvFrame[length + 2 * i] = v[i];
            yuvFrame[length + 2 * i + 1] = u[i];
        }

        YuvImage yuvImage = new YuvImage(yuvFrame,
                ImageFormat.NV21,
                width,
                height,
                null);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        yuvImage.compressToJpeg(new Rect(0,
                0,
                width,
                height), 50, outputStream);

        return outputStream.toByteArray();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onYuvDataReceived(MediaFormat mediaFormat, ByteBuffer byteBuffer, int dataSize, int width, int height) {
        Log.i(TAG, "Received YUV frame");

        final byte[] bytes = new byte[dataSize];
        byteBuffer.get(bytes);
        byte[] jpegByte = parseToJPEG(bytes, width, height);

        //latestJPEGframe = Base64.getEncoder().encodeToString(jpegByte);

        if(jpegByte != null) {
            runOnUiThread(() -> {
                if (fpvJpegTextureView.isAvailable()) {
                    Bitmap image = BitmapFactory.decodeByteArray(jpegByte, 0, jpegByte.length);
                    Canvas canvas = fpvJpegTextureView.lockCanvas();
                    if(canvas != null) {
                        // Destination rectangle to fit the image to the full screen
                        Rect destRect = new Rect(0, 0, fpvJpegTextureView.getWidth(), fpvJpegTextureView.getHeight());

                        // Source rectangle (original image size)
                        Rect srcRect = new Rect(0, 0, image.getWidth(), image.getHeight());

                        canvas.drawBitmap(image, srcRect, destRect, null);
                        fpvJpegTextureView.unlockCanvasAndPost(canvas);
                    }
                }
            });
        }

        flightDataInterval++;

        //if(flightDataInterval >= FLIGHT_INTERVAL) {
            try {
                Log.d(TAG, "WSRS: " + String.valueOf(connectionState));
                FlightControllerState state = aircraft.getFlightController().getState();
                LocationCoordinate3D location = state.getAircraftLocation();
                Attitude attitude = state.getAttitude();
                float compass = aircraft.getFlightController().getCompass().getHeading();
                float relativeAltitude = location.getAltitude();
                float altitude = takeoffAltitude + relativeAltitude;
                dji.common.gimbal.Attitude gimbalAttitude = gimbalState.getAttitudeInDegrees();
                Date currentTime = Calendar.getInstance().getTime();
                SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS");
                latestKnownLatitudeRaw = Double.isNaN(location.getLatitude()) ? latestKnownLatitudeRaw : location.getLatitude();
                latestKnownLongitudeRaw = Double.isNaN(location.getLongitude()) ? latestKnownLongitudeRaw : location.getLongitude();

                latestKnownLatitude = latestKnownLatitudeRaw + latitudeOffset;
                latestKnownLongitude = latestKnownLongitudeRaw + longitudeOffset;

                int remainingBattery = batterState.getChargeRemainingInPercent();

                //if (connectionState == READYSTATE.OPEN) {
                    JSONObject msg = new JSONObject();
                    JSONObject data = new JSONObject();
                    JSONObject gps = new JSONObject();
                    JSONObject aircraftOrientation = new JSONObject();
                    JSONObject aircraftVelocity = new JSONObject();
                    JSONObject gimbalOrientation = new JSONObject();
                    JSONObject sticks = new JSONObject();
                    JSONObject leftStickJson = new JSONObject();
                    JSONObject rightStickJson = new JSONObject();
                    try {
                        msg.put("type", "data_broadcast");
                        data.put("client_id", clientID);
                        // Fill altitude
                        data.put("altitude", altitude);
                        data.put("relative_altitude", relativeAltitude);
                        // Fill GPS data
                        gps.put("latitude", latestKnownLatitude);
                        gps.put("longitude", latestKnownLongitude);
                        data.put("gps", gps);
                        // Fill aircraft orientation
                        aircraftOrientation.put("pitch", attitude.pitch);
                        aircraftOrientation.put("roll", attitude.roll);
                        aircraftOrientation.put("yaw", attitude.yaw);
                        aircraftOrientation.put("compass", compass);
                        data.put("aircraft_orientation", aircraftOrientation);
                        // Fill aircraft velocity
                        aircraftVelocity.put("velocity_x", state.getVelocityX());
                        aircraftVelocity.put("velocity_y", state.getVelocityY());
                        aircraftVelocity.put("velocity_z", state.getVelocityZ());
                        data.put("aircraft_velocity", aircraftVelocity);
                        // Fill aircraft orientation
                        gimbalOrientation.put("pitch", gimbalAttitude.getPitch());
                        gimbalOrientation.put("roll", gimbalAttitude.getRoll());
                        gimbalOrientation.put("yaw", gimbalAttitude.getYaw());
                        gimbalOrientation.put("yaw_relative", gimbalState.getYawRelativeToAircraftHeading());

                        // Fill stick data
                        leftStickJson.put("x", leftStick.first);
                        leftStickJson.put("y", leftStick.second);
                        rightStickJson.put("x", rightStick.first);
                        rightStickJson.put("y", rightStick.second);

                        sticks.put("left_stick", leftStickJson);
                        sticks.put("right_stick", rightStickJson);

                        data.put("gimbal_orientation", gimbalOrientation);

                        data.put("satellite_count", state.getSatelliteCount());
                        data.put("gps_signal_level", state.getGPSSignalLevel());
                        // Battery state
                        data.put("battery", remainingBattery);

                        data.put("sticks" , sticks);

                        // Fill timestamp
                        data.put("timestamp", timestampFormat.format(currentTime));

                        // Fill jpeg frame
                        //data.put("frame", latestJPEGframe);

                        // Put everything together
                        msg.put("data", data);

                        byte[] jsonBytes = msg.toString().getBytes(StandardCharsets.UTF_8);
                        byte[] jpegBytes = jpegByte;
                        ByteBuffer buffer = ByteBuffer.allocate(4 + jsonBytes.length + 4 + jpegBytes.length);
                        // 1. Put 4 bytes: JSON length
                        buffer.putInt(jsonBytes.length);
                        // 2. Put JSON bytes
                        buffer.put(jsonBytes);
                        // 3. Put 4 bytes: JPEG length
                        buffer.putInt(jpegBytes.length);
                        // 4. Put JPEG bytes
                        buffer.put(jpegBytes);
                        // 5. Send it
                        if (connectionState == READYSTATE.OPEN) {
                            outboundClient.sendByteArray(Mono.just(buffer.array()))
                                    .then()
                                    .doOnSuccess(unused -> Log.d(TAG, "Binary message sent successfully"))
                                    .doOnError(error -> {
                                        Log.e(TAG, "Failed to send binary message", error);
                                        connectionState = READYSTATE.FAILURE;
                                    })
                                    .subscribe();
                        }

                        if(logFlight && fileHelper != null && fileHelperBinary != null) {
                            fileHelperBinary.writeBinary(buffer.array());
                        }


                        flightDataInterval = 0;
                    } catch (Exception e) {
                        Log.e(TAG, "Sending flight data", e);
                    }
                //}
            } catch (Exception e) {
                Log.e(TAG, "Flight Data send exception", e);
            }
        //}
    }


    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
        Log.d(TAG, "Selected item: " + adapterView.getItemAtPosition(pos).toString());
        double latitudeCalibrationPoint = 0;
        double longitudeCalibrationPoint = 0;

        switch(adapterView.getItemAtPosition(pos).toString()) {
            case "none":
                latitudeCalibrationPoint = 0;
                longitudeCalibrationPoint = 0;
                break;
            case "1_brno":
                latitudeCalibrationPoint = 49.2272250;
                longitudeCalibrationPoint = 16.5974175;
                break;
            case "2_graz":
                latitudeCalibrationPoint = 47.058524;
                longitudeCalibrationPoint = 15.459548;
                break;
        }

        if (latitudeCalibrationPoint != 0 && longitudeCalibrationPoint != 0) {
            latitudeOffset = latitudeCalibrationPoint - latestKnownLatitudeRaw;
            longitudeOffset = longitudeCalibrationPoint - latestKnownLongitudeRaw;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        Log.d(TAG, "Nothing selected");
    }
}

