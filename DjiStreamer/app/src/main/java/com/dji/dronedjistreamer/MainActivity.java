package com.dji.dronedjistreamer;

import android.Manifest;
import android.app.Activity;
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
import android.media.Image;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.dji.dronedjistreamer.internal.utils.AltitudeDialog;
import com.dji.dronedjistreamer.internal.utils.ServerIPDialog;
import com.dji.dronedjistreamer.internal.utils.ToastUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import dji.common.error.DJIError;
import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.gimbal.GimbalState;
import dji.common.util.CommonCallbacks;
import dji.midware.usb.P3.UsbAccessoryService;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.sdkmanager.LiveStreamManager;
import dji.sdk.sdkmanager.LiveVideoBitRateMode;
import dji.sdk.sdkmanager.LiveVideoResolution;
import dji.thirdparty.afinal.core.AsyncTask;
import dji.thirdparty.org.java_websocket.WebSocket;
import dji.thirdparty.org.java_websocket.client.WebSocketClient;
import dji.thirdparty.org.java_websocket.handshake.ServerHandshake;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.thirdparty.sanselan.util.IOUtils;
import dji.ux.widget.FPVWidget;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.Base64;

public class MainActivity extends FragmentActivity
        implements View.OnClickListener,
        ServerIPDialog.ServerIPDialogListener,
        AircraftStatusReceiver.AircraftStatusListener,
        AltitudeDialog.AltitudeDialogListener,
        DJICodecManager.YuvDataCallback {

    private boolean RunRTMPStream = false;

    private static final String TAG = MainActivity.class.getName();
    private static final int FLIGHT_INTERVAL = 4;

    private static final String liveShowUrl = "rtmp://147.229.14.181:1935/live/dji_mavic";
    private LiveStreamManager.OnLiveChangeListener listener;
    public static WebSocketClient webSocketClient;
    private Button startLiveShowBtn;
    private Button stopLiveShowBtn;
//    private Button isLiveShowOnBtn;
//    private Button showInfoBtn;
    private Button setServerIPBtn;
    private Button setAltitudeBtn;
    private Button startRecordingBtn;
    private Button startFlightRecordingBtn;
    private Button startCarDetectorBtn;
    private ImageView videoViewRectangles;
    private Canvas videoViewCanvas;
    //private FPVWidget fpvView;
    //private ImageView fpvJpegView;
    private TextureView fpvJpegTextureView;
    //private SurfaceView fpvJpegSurfaceView;
    //private SurfaceHolder fpvJpegSurfaceHolder;
//    private SurfaceHolder.Callback surfaceCallback;

    private SharedPreferences sharedPreferences;

    private String serverIP = "";
    private String serverPort = "";
    //private String serverRTMP = "";

    private GimbalState gimbalState;

    private Handler connectionHandler;
    private final int connectionRetry = 1000;

    private Aircraft aircraft;
    private String aircraftSerialNumber;

    private boolean aircraftConnected = false;

    AircraftStatusReceiver djiStatusReceiver;

    private String clientID;
    private String rtmpURL;

    private boolean recordingOn = false;
    private boolean flightRecordingOn = false;
    private boolean carDetector = false;

    private float takeoffAltitude = Float.NaN;
//    private double latestKnownLatitude = 49.201851;
//    private double latestKnownLongitude = 16.603196;
    private double latestKnownLatitude = 49.227240;
    private double latestKnownLongitude = 16.597338;

    private int yuvFrameCount;

    private DJICodecManager codecManager = null;
    private String latestJPEGframe = "";

    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener = null;

    private int flightDataInterval = 0;

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
        startRecordingBtn = (Button) findViewById(R.id.btn_start_recording);
        startFlightRecordingBtn = (Button) findViewById(R.id.btn_flight_data_recording);
        startCarDetectorBtn = (Button) findViewById(R.id.btn_start_car_detector);

        startLiveShowBtn.setOnClickListener(this);
        stopLiveShowBtn.setOnClickListener(this);
        setServerIPBtn.setOnClickListener(this);
        setAltitudeBtn.setOnClickListener(this);
        startRecordingBtn.setOnClickListener(this);
        startFlightRecordingBtn.setOnClickListener(this);
        startCarDetectorBtn.setOnClickListener(this);

        videoViewRectangles = (ImageView) findViewById(R.id.video_view_rectangles);
        //fpvView = (FPVWidget) findViewById(R.id.video_view_fpv_video_feed);
        //fpvJpegView = (ImageView) findViewById(R.id.fpv_jpeg_video_feed);
        fpvJpegTextureView = (TextureView) findViewById(R.id.fpv_jpeg_video_feed_texture);
        //fpvJpegSurfaceView = (SurfaceView) findViewById(R.id.fpv_jpeg_video_feed_surface);

        listener = new LiveStreamManager.OnLiveChangeListener() {
            @Override
            public void onStatusChanged(int i) {
                ToastUtils.setResultToToast("status changed : " + i);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        //initPreviewerSurfaceView();
        //initPreviewerTextureView();
    }

//    private void initPreviewerSurfaceView() {
//        Log.d(TAG, "initSurfaceView");
//        fpvJpegSurfaceHolder = fpvJpegSurfaceView.getHolder();
//        SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
//            @Override
//            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
//                Log.d(TAG, "real onSurfaceTextureAvailable: width " + fpvJpegSurfaceView.getWidth() + " height " + fpvJpegSurfaceView.getHeight());
//                codecManager = new DJICodecManager(getApplicationContext(), surfaceHolder, fpvJpegSurfaceView.getWidth(), fpvJpegSurfaceView.getHeight());
//            }
//
//            @Override
//            public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
//
//            }
//
//            @Override
//            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
//                if(codecManager != null) {
//                    codecManager.cleanSurface();
//                    codecManager.destroyCodec();
//                    codecManager = null;
//                }
//            }
//        };
//
//        fpvJpegSurfaceHolder.addCallback(surfaceCallback);
//    }

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
                Log.d(TAG, "FPV: new image data received");
                if (codecManager != null) {
                    Log.d(TAG, "FPV: sending image to codecManager");
                    codecManager.sendDataToDecoder(videoBuffer, size);
                }
            }
        };

        if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
            Log.d(TAG, "FPV: addVideoDataListener");
            VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(mReceivedVideoDataListener);
        }
    }

    public void AircraftConnected() {

    }

    public void AircraftDisconnected() {

    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        if(isLiveStreamManagerOn(false)) {
            DJISDKManager.getInstance().getLiveStreamManager().registerListener(listener);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if(isLiveStreamManagerOn(false)) {
            DJISDKManager.getInstance().getLiveStreamManager().unregisterListener(listener);
        }
    }

    private void startLiveShow() {
        if(RunRTMPStream) {
            runRTMPStream();
        } else {
            runSyncedJPEGStream();
        }
    }

    private void createCodecManager() {
//        if(codecManager != null) {
//            codecManager.cleanSurface();
//            codecManager.destroyCodec();
//            codecManager = null;
//        }

//        fpvJpegSurfaceHolder = fpvJpegSurfaceView.getHolder();
//        Log.d(TAG, "creating surfaceHolder");
//        fpvJpegSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
//            @Override
//            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
//                Log.d(TAG, "real onSurfaceTextureAvailable");
//                codecManager = new DJICodecManager(getApplicationContext(), surfaceHolder, 1280, 720, UsbAccessoryService.VideoStreamSource.Camera);
//                codecManager.enabledYuvData(true);
//                codecManager.setYuvDataCallback(MainActivity.this);
//            }
//
//            @Override
//            public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
//
//            }
//
//            @Override
//            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
//                if(codecManager != null) {
//                    codecManager.cleanSurface();
//                    codecManager.destroyCodec();
//                    codecManager = null;
//                }
//
//                latestJPEGframe = "";
//            }
//        });



//        codecManager = new DJICodecManager(getApplicationContext(), null, 1280, 720, UsbAccessoryService.VideoStreamSource.Camera);
////        codecManager = new DJICodecManager(getApplicationContext(), null, fpvJpegView.getWidth(), fpvJpegView.getHeight());
//
        if(codecManager != null) {
            codecManager.enabledYuvData(true);
            codecManager.setYuvDataCallback(this);
            //fpvJpegTextureView.setVisibility(View.GONE);
            //fpvJpegView.setVisibility(View.VISIBLE);
        }

//        // The callback for receiving the raw H264 video data for camera live view
//        mReceivedVideoDataListener = new VideoFeeder.VideoDataListener() {
//
//            @Override
//            public void onReceive(byte[] videoBuffer, int size) {
//                if (codecManager != null) {
//                    codecManager.sendDataToDecoder(videoBuffer, size);
//                }
//            }
//        };
    }

    private void runSyncedJPEGStream() {
        createCodecManager();
        //fpvView.setVisibility(View.GONE);
    }

    private void runRTMPStream() {
        //Toast.makeText(getApplicationContext(), "Start Live Show", Toast.LENGTH_LONG).show();

        if (!isLiveStreamManagerOn(false)) {
            return;
        }

        if(isLiveStreamManagerOn(false)) {
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
                ToastUtils.setResultToToast("Starting live stream.");
                DJISDKManager.getInstance().getLiveStreamManager().setLiveUrl(rtmpURL);
//                DJISDKManager.getInstance().getLiveStreamManager().setLiveVideoBitRateMode(LiveVideoBitRateMode.MANUAL);
//                DJISDKManager.getInstance().getLiveStreamManager().setLiveVideoResolution(LiveVideoResolution.VIDEO_RESOLUTION_1920_1080);
//                DJISDKManager.getInstance().getLiveStreamManager().setLiveVideoBitRate(4096);
                int result = DJISDKManager.getInstance().getLiveStreamManager().startStream();
                DJISDKManager.getInstance().getLiveStreamManager().setStartTime();

                ToastUtils.setResultToToast("startLive:" + result +
                        "\n isVideoStreamSpeedConfigurable:" + DJISDKManager.getInstance().getLiveStreamManager().isVideoStreamSpeedConfigurable() +
                        "\n isLiveAudioEnabled:" + DJISDKManager.getInstance().getLiveStreamManager().isLiveAudioEnabled());
            }
        }.start();
    }

    private void stopLiveShow() {
        if(RunRTMPStream) {
            stopRTMPStream();
        } else {
            stopSyncedJPEGStream();
        }
    }

    private void stopSyncedJPEGStream() {
        if(codecManager != null) {
            codecManager.cleanSurface();
            codecManager.destroyCodec();
            codecManager = null;
        }

        latestJPEGframe = "";
    }

    private void stopRTMPStream() {
        if (!isLiveStreamManagerOn(false)) {
            return;
        }
        DJISDKManager.getInstance().getLiveStreamManager().stopStream();
        ToastUtils.setResultToToast("Stop Live Show");
    }

    private void isLiveShowOn() {
        if (!isLiveStreamManagerOn(true)) {
            return;
        }
        ToastUtils.setResultToToast("Is Live Show On:" + DJISDKManager.getInstance().getLiveStreamManager().isStreaming());
    }

    private void showInfo() {
        if (!isLiveStreamManagerOn(true)) {
            return;
        }

        Log.i("STREAM_SIZE_INFO", String.valueOf(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoResolution().getWidth() + "x" + DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoResolution().getHeight()));
        StringBuilder sb = new StringBuilder();
        sb.append("Video BitRate:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoBitRate()).append(" kpbs");
        ToastUtils.setResultToToast(sb.toString());
        sb.delete(0, sb.length());
        sb.append("Audio BitRate:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveAudioBitRate()).append(" kpbs");
        ToastUtils.setResultToToast(sb.toString());
        sb.delete(0, sb.length());
        sb.append("Video FPS:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoFps());
        ToastUtils.setResultToToast(sb.toString());
        sb.delete(0, sb.length());
        sb.append("Video Resolution:").append(String.valueOf(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoResolution().getWidth())).append("x").append(String.valueOf(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoResolution().getHeight()));
        ToastUtils.setResultToToast(sb.toString());
        sb.delete(0, sb.length());
        sb.append("Video Cache size:").append(DJISDKManager.getInstance().getLiveStreamManager().getLiveVideoCacheSize()).append(" frame");
        ToastUtils.setResultToToast(sb.toString());
        sb.delete(0, sb.length());
    }

    private boolean isLiveStreamManagerOn(boolean displayMessage) {
        if (DJISDKManager.getInstance().getLiveStreamManager() == null) {
            if(displayMessage)
                ToastUtils.setResultToToast("No live stream manager!");
            return false;
        }
        else {
            if(displayMessage)
                ToastUtils.setResultToToast("Live stream manager is ON!");
        }
        return true;
    }

    private void openServerIPDialog() {
        ServerIPDialog serverIPDialog = new ServerIPDialog();
        serverIPDialog.SetHint(serverIP, serverPort, "rtmp://serverIP:1935/live/clientID");
        serverIPDialog.show(getSupportFragmentManager(), "server ip dialog");
    }

    private void openAltitudeDialog() {
        AltitudeDialog altitudeDialog = new AltitudeDialog();
        altitudeDialog.SetHint(String.valueOf(takeoffAltitude));
        altitudeDialog.show(getSupportFragmentManager(), "altitude dialog");
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

    private void sendFlightData() {
//        Handler handler = new Handler();
//        int delay = 100;

        Gimbal gimbal = aircraft.getGimbal();
        gimbal.setStateCallback(new GimbalState.Callback() {
            @Override
            public void onUpdate(@NonNull GimbalState state) {
                gimbalState = state;
            }
        });

//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    Log.d(TAG, "WSRS: " + String.valueOf(webSocketClient.getReadyState()));
//                    FlightControllerState state = aircraft.getFlightController().getState();
//                    LocationCoordinate3D location = state.getAircraftLocation();
//                    Attitude attitude = state.getAttitude();
//                    float compass = aircraft.getFlightController().getCompass().getHeading();
//                    //float altitude = state.getTakeoffLocationAltitude() + location.getAltitude();
//                    //float altitude = 221.5f + location.getAltitude();
//                    float altitude = takeoffAltitude + location.getAltitude();
//                    //float altitude = 343.3f + location.getAltitude();
//                    dji.common.gimbal.Attitude gimbalAttitude = gimbalState.getAttitudeInDegrees();
//                    Date currentTime = Calendar.getInstance().getTime();
//                    //SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
//                    SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS");
//                    latestKnownLatitude = Double.isNaN(location.getLatitude()) ? latestKnownLatitude : location.getLatitude();
//                    latestKnownLongitude = Double.isNaN(location.getLongitude()) ? latestKnownLongitude : location.getLongitude();
//
//                    if (webSocketClient.getReadyState() == WebSocket.READYSTATE.OPEN) {
//                        JSONObject msg = new JSONObject();
//                        JSONObject data = new JSONObject();
//                        JSONObject gps = new JSONObject();
//                        JSONObject aircraftOrientation = new JSONObject();
//                        JSONObject aircraftVelocity = new JSONObject();
//                        JSONObject gimbalOrientation = new JSONObject();
//                        try {
//                            msg.put("type", "data_broadcast");
//                            data.put("client_id", clientID);
//                            // Fill altitude
//                            data.put("altitude", altitude);
//                            // Fill GPS data
//                            gps.put("latitude", latestKnownLatitude);
//                            gps.put("longitude", latestKnownLongitude);
//                            data.put("gps", gps);
//                            // Fill aircraft orientation
//                            aircraftOrientation.put("pitch", attitude.pitch);
//                            aircraftOrientation.put("roll", attitude.roll);
//                            aircraftOrientation.put("yaw", attitude.yaw);
//                            aircraftOrientation.put("compass", compass);
//                            data.put("aircraft_orientation", aircraftOrientation);
//                            // Fill aircraft velocity
//                            aircraftVelocity.put("velocity_x", state.getVelocityX());
//                            aircraftVelocity.put("velocity_y", state.getVelocityY());
//                            aircraftVelocity.put("velocity_z", state.getVelocityZ());
//                            data.put("aircraft_velocity", aircraftVelocity);
//                            // Fill aircraft orientation
//                            gimbalOrientation.put("pitch", gimbalAttitude.getPitch());
//                            gimbalOrientation.put("roll", gimbalAttitude.getRoll());
//                            gimbalOrientation.put("yaw", gimbalAttitude.getYaw());
//                            gimbalOrientation.put("yaw_relative", gimbalState.getYawRelativeToAircraftHeading());
//                            data.put("gimbal_orientation", gimbalOrientation);
//
//                            // Fill timestamp
//                            data.put("timestamp", timestampFormat.format(currentTime));
//
//                            // Fill jpeg frame
//                            data.put("frame", latestJPEGframe);
//
//                            // Put everything together
//                            msg.put("data", data);
//
//                            //Log.i(TAG, "Sending flight data");
//                            Log.i(TAG, msg.toString());
//
//                            webSocketClient.send(msg.toString());
//                        } catch (Exception e) {
//                            Log.e(TAG, "Sending flight data", e);
//                        }
//
//                        handler.postDelayed(this, delay);
//                    }
//                } catch (Exception e) {
//                    Log.e(TAG, "Flight Data send exception", e);
//                }
//            }
//        }, delay);
    }

    private void doServerHandshake() {
        JSONObject msg = new JSONObject();
        JSONObject data = new JSONObject();
        try {
            msg.put("type", "hello");
            data.put("ctype", 0);
            data.put("drone_name", aircraft.getModel());
            data.put("serial", aircraftSerialNumber);
            msg.put("data", data);

            webSocketClient.send(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Doing server handshake", e);
        }
    }

    private void handleServerHandshake(JSONObject msg) throws JSONException {
        clientID = msg.getString("client_id");
        rtmpURL = "rtmp://" + serverIP + ":1935/live/" + clientID;
        //startLiveShow();
        Log.i(TAG, msg.toString());

        // If app successfully connected to the server, start sending flight data
//        Runnable flightDataSender = new Runnable() {
//            @Override
//            public void run() {
//                sendFlightData();
//            }
//        };
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sendFlightData();
            }
        });
    }

    private void handleVehicleDetecion(JSONObject msg) throws JSONException {
        Log.i("CAR_DETECTOR", msg.toString());
        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(10);

        videoViewCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

//        float multiplierX = fpvView.getWidth() / 1280f;
//        float multiplierY = fpvView.getHeight() / 720f;
//
//        JSONArray rects = msg.getJSONArray("rects");
//        for (int i = 0; i < rects.length(); i++) {
//            JSONObject rect = rects.getJSONObject(i);
//            float leftX = rect.getInt("x");
//            float topY = rect.getInt("y");
//            float rightX = leftX + rect.getInt("w");
//            float bottomY = topY - rect.getInt("h");
//
//            videoViewCanvas.drawRect(multiplierX * leftX, multiplierY * topY + 250, multiplierX * rightX, multiplierY * bottomY + 250, paint);
//        }
    }

    public void createWebSocketClient(String ip, String port) {
        URI uri;
        try {
            uri = new URI("ws://" + ip + ":" + port);
        } catch (URISyntaxException e) {
            Log.e(TAG, "Create websocket URI", e);
            return;
        }

        webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.i(TAG, "Connected to the DroCo server.");
                ToastUtils.setResultToToast("Connected to the DroCo server.");
                // If websocket connection opened, do the custom server handshake
                doServerHandshake();
            }

            @Override
            public void onMessage(String s) {
                try {
                    JSONObject receivedMsg = new JSONObject(s);
                    String msgType = receivedMsg.getString("type");
                    switch (msgType) {
                        case "hello_resp":
                            handleServerHandshake(receivedMsg.getJSONObject("data"));
                            break;
                        case "vehicle_detection_rects":
                            handleVehicleDetecion(receivedMsg.getJSONObject("data"));
                            break;
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "On message received", e);
                }
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
                //showInfo();
                break;
            case R.id.btn_set_server_ip:
                openServerIPDialog();
                break;
            case R.id.btn_set_altitude:
                openAltitudeDialog();
                break;
            case R.id.btn_start_recording:
                startRecording(v);
                break;
            case R.id.btn_flight_data_recording:
                startFlightDataRecording(v);
                break;
            case R.id.btn_start_car_detector:
                startCarDetector(v);
                break;
        }
    }

    private void startCarDetector(View v) {
//        carDetector = !carDetector;
//        v.setSelected(carDetector);
//
//        // Init drawing canvas
//        Bitmap bitmap = Bitmap.createBitmap(fpvView.getWidth(), fpvView.getHeight(), Bitmap.Config.ARGB_8888);
//        videoViewCanvas = new Canvas(bitmap);
//        videoViewRectangles.setImageBitmap(bitmap);
//
//        Log.i("CAR_DETECTOR", "width: " + bitmap.getWidth() + " .. height: " + bitmap.getHeight());
//
//        JSONObject msg = new JSONObject();
//        JSONObject data = new JSONObject();
//        try {
//            msg.put("type", "vehicle_detection_set");
//            data.put("drone_stream_id", clientID);
//            data.put("state", carDetector);
//            msg.put("data", data);
//
//            webSocketClient.send(msg.toString());
//        } catch (JSONException e) {
//            Log.e(TAG, "Start car detector", e);
//        }
    }

    private void startFlightDataRecording(View v) {
        flightRecordingOn = !flightRecordingOn;
        v.setSelected(flightRecordingOn);

        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "flight_data_save_set");
            msg.put("data", flightRecordingOn);

            webSocketClient.send(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Start flight data record", e);
        }
    }

    private void startRecording(View v) {
        recordingOn = !recordingOn;
        v.setSelected(recordingOn);

        JSONObject msg = new JSONObject();
        JSONObject data = new JSONObject();
        try {
            msg.put("type", "media_record_set");
            data.put("drone_stream_id", clientID);
            data.put("state", recordingOn);
            msg.put("data", data);

            webSocketClient.send(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Start recording", e);
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
        //serverRTMP = rtmp;

        if(aircraftConnected) {
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

                //initPreviewerSurfaceView();
                initPreviewerTextureView();
                initVideoFeeder();

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

        //Log.d("MainActivity", "Frame width x height: " + width + "x" + height);

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

        latestJPEGframe = Base64.getEncoder().encodeToString(jpegByte);

        if(jpegByte != null) {
            runOnUiThread(() -> {
                if (fpvJpegTextureView.isAvailable()) {
                    Bitmap image = BitmapFactory.decodeByteArray(jpegByte, 0, jpegByte.length);
                    Canvas canvas = fpvJpegTextureView.lockCanvas();
                    if(canvas != null) {
                        canvas.drawBitmap(image, 0, 0, null);
                        fpvJpegTextureView.unlockCanvasAndPost(canvas);
                    }
                }
            });
//            try {
//                byte[] imageByte = jpegByte;
//                Bitmap image = BitmapFactory.decodeByteArray(imageByte, 0, imageByte.length);
//
//                fpvJpegTextureView.;
//                fpvJpegView.setImageBitmap(image);
//            } catch (Exception e) {
//                Log.e(TAG, "ERROR ImageView Crash");
//                e.printStackTrace();
//                Log.e(TAG, e.toString());
//            }

        }

        flightDataInterval++;

        if(flightDataInterval >= FLIGHT_INTERVAL) {
            try {
                Log.d(TAG, "WSRS: " + String.valueOf(webSocketClient.getReadyState()));
                FlightControllerState state = aircraft.getFlightController().getState();
                LocationCoordinate3D location = state.getAircraftLocation();
                Attitude attitude = state.getAttitude();
                float compass = aircraft.getFlightController().getCompass().getHeading();
                //float altitude = state.getTakeoffLocationAltitude() + location.getAltitude();
                //float altitude = 221.5f + location.getAltitude();
                float altitude = takeoffAltitude + location.getAltitude();
                //float altitude = 343.3f + location.getAltitude();
                dji.common.gimbal.Attitude gimbalAttitude = gimbalState.getAttitudeInDegrees();
                Date currentTime = Calendar.getInstance().getTime();
                //SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
                SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS");
                latestKnownLatitude = Double.isNaN(location.getLatitude()) ? latestKnownLatitude : location.getLatitude();
                latestKnownLongitude = Double.isNaN(location.getLongitude()) ? latestKnownLongitude : location.getLongitude();

                if (webSocketClient.getReadyState() == WebSocket.READYSTATE.OPEN) {
                    JSONObject msg = new JSONObject();
                    JSONObject data = new JSONObject();
                    JSONObject gps = new JSONObject();
                    JSONObject aircraftOrientation = new JSONObject();
                    JSONObject aircraftVelocity = new JSONObject();
                    JSONObject gimbalOrientation = new JSONObject();
                    try {
                        msg.put("type", "data_broadcast");
                        data.put("client_id", clientID);
                        // Fill altitude
                        data.put("altitude", altitude);
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
                        data.put("gimbal_orientation", gimbalOrientation);

                        // Fill timestamp
                        data.put("timestamp", timestampFormat.format(currentTime));

                        // Fill jpeg frame
                        data.put("frame", latestJPEGframe);

                        // Put everything together
                        msg.put("data", data);

                        //Log.i(TAG, "Sending flight data");
                        Log.i(TAG, msg.toString());

                        webSocketClient.send(msg.toString());

                        flightDataInterval = 0;
                    } catch (Exception e) {
                        Log.e(TAG, "Sending flight data", e);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Flight Data send exception", e);
            }
        }


//        //In this demo, we test the YUV data by saving it into JPG files.
//        //DJILog.d(TAG, "onYuvDataReceived " + dataSize);
//        if (yuvFrameCount++ % 30 == 0 && byteBuffer != null) {
//            final byte[] bytes = new byte[dataSize];
//            byteBuffer.get(bytes);
//            //DJILog.d(TAG, "onYuvDataReceived2 " + dataSize);
//            AsyncTask.execute(new Runnable() {
//                @Override
//                public void run() {
//                    // two samples here, it may has other color format.
//                    int colorFormat = mediaFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
//                    switch (colorFormat) {
//                        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
//                            //NV12
//                            if (Build.VERSION.SDK_INT <= 23) {
//                                oldSaveYuvDataToJPEG(bytes, width, height);
//                            } else {
//                                newSaveYuvDataToJPEG(bytes, width, height);
//                            }
//                            break;
//                        case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
//                            //YUV420P
//                            newSaveYuvDataToJPEG420P(bytes, width, height);
//                            break;
//                        default:
//                            break;
//                    }
//                }
//            });
//        }
    }

//    // For android API <= 23
//    private void oldSaveYuvDataToJPEG(byte[] yuvFrame, int width, int height){
//        Log.i("MainActivity", "oldSaveYuvDATA");
//
//        if (yuvFrame.length < width * height) {
//            //DJILog.d(TAG, "yuvFrame size is too small " + yuvFrame.length);
//            return;
//        }
//
//        byte[] y = new byte[width * height];
//        byte[] u = new byte[width * height / 4];
//        byte[] v = new byte[width * height / 4];
//        byte[] nu = new byte[width * height / 4]; //
//        byte[] nv = new byte[width * height / 4];
//
//        System.arraycopy(yuvFrame, 0, y, 0, y.length);
//        for (int i = 0; i < u.length; i++) {
//            v[i] = yuvFrame[y.length + 2 * i];
//            u[i] = yuvFrame[y.length + 2 * i + 1];
//        }
//        int uvWidth = width / 2;
//        int uvHeight = height / 2;
//        for (int j = 0; j < uvWidth / 2; j++) {
//            for (int i = 0; i < uvHeight / 2; i++) {
//                byte uSample1 = u[i * uvWidth + j];
//                byte uSample2 = u[i * uvWidth + j + uvWidth / 2];
//                byte vSample1 = v[(i + uvHeight / 2) * uvWidth + j];
//                byte vSample2 = v[(i + uvHeight / 2) * uvWidth + j + uvWidth / 2];
//                nu[2 * (i * uvWidth + j)] = uSample1;
//                nu[2 * (i * uvWidth + j) + 1] = uSample1;
//                nu[2 * (i * uvWidth + j) + uvWidth] = uSample2;
//                nu[2 * (i * uvWidth + j) + 1 + uvWidth] = uSample2;
//                nv[2 * (i * uvWidth + j)] = vSample1;
//                nv[2 * (i * uvWidth + j) + 1] = vSample1;
//                nv[2 * (i * uvWidth + j) + uvWidth] = vSample2;
//                nv[2 * (i * uvWidth + j) + 1 + uvWidth] = vSample2;
//            }
//        }
//        //nv21test
//        byte[] bytes = new byte[yuvFrame.length];
//        System.arraycopy(y, 0, bytes, 0, y.length);
//        for (int i = 0; i < u.length; i++) {
//            bytes[y.length + (i * 2)] = nv[i];
//            bytes[y.length + (i * 2) + 1] = nu[i];
//        }
////        Log.d(TAG,
////                "onYuvDataReceived: frame index: "
////                        + DJIVideoStreamDecoder.getInstance().frameIndex
////                        + ",array length: "
////                        + bytes.length);
//        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
//            screenShot(bytes, getApplicationContext().getExternalFilesDir("DJI").getPath() + "/DJI_ScreenShot", width, height);
//        } else {
//            screenShot(bytes, Environment.getExternalStorageDirectory() + "/DJI_ScreenShot", width, height);
//        }
//    }
//
//    private void newSaveYuvDataToJPEG(byte[] yuvFrame, int width, int height){
//        Log.i("MainActivity", "newSaveYuvData");
//        if (yuvFrame.length < width * height) {
//            //DJILog.d(TAG, "yuvFrame size is too small " + yuvFrame.length);
//            return;
//        }
//        int length = width * height;
//
//        byte[] u = new byte[width * height / 4];
//        byte[] v = new byte[width * height / 4];
//        for (int i = 0; i < u.length; i++) {
//            v[i] = yuvFrame[length + 2 * i];
//            u[i] = yuvFrame[length + 2 * i + 1];
//        }
//        for (int i = 0; i < u.length; i++) {
//            yuvFrame[length + 2 * i] = u[i];
//            yuvFrame[length + 2 * i + 1] = v[i];
//        }
//
//        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
//            screenShot(yuvFrame, getApplicationContext().getExternalFilesDir("DJI").getPath() + "/DJI_ScreenShot", width, height);
//        } else {
//            screenShot(yuvFrame, Environment.getExternalStorageDirectory() + "/DJI_ScreenShot", width, height);
//        }
//    }
//
//    private void newSaveYuvDataToJPEG420P(byte[] yuvFrame, int width, int height) {
//        Log.i("MainActivity", "oldSaveYuvDATA_420p");
//
//        if (yuvFrame.length < width * height) {
//            return;
//        }
//        int length = width * height;
//
//        byte[] u = new byte[width * height / 4];
//        byte[] v = new byte[width * height / 4];
//
//        for (int i = 0; i < u.length; i ++) {
//            u[i] = yuvFrame[length + i];
//            v[i] = yuvFrame[length + u.length + i];
//        }
//        for (int i = 0; i < u.length; i++) {
//            yuvFrame[length + 2 * i] = v[i];
//            yuvFrame[length + 2 * i + 1] = u[i];
//        }
//
//        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
//            screenShot(yuvFrame, getApplicationContext().getExternalFilesDir("DJI").getPath() + "/DJI_ScreenShot", width, height);
//        } else {
//            screenShot(yuvFrame, Environment.getExternalStorageDirectory() + "/DJI_ScreenShot", width, height);
//        }
//    }
//
//    /**
//     * Save the buffered data into a JPG image file
//     */
//    private void screenShot(byte[] buf, String shotDir, int width, int height) {
//        File dir = new File(shotDir);
//        if (!dir.exists() || !dir.isDirectory()) {
//            dir.mkdirs();
//        }
//        YuvImage yuvImage = new YuvImage(buf,
//                ImageFormat.NV21,
//                width,
//                height,
//                null);
//        OutputStream outputFile;
//        final String path = dir + "/ScreenShot_" + System.currentTimeMillis() + ".jpg";
//        try {
//            outputFile = new FileOutputStream(new File(path));
//        } catch (FileNotFoundException e) {
//            Log.e(TAG, "test screenShot: new bitmap output file error: " + e);
//            return;
//        }
//        if (outputFile != null) {
//            yuvImage.compressToJpeg(new Rect(0,
//                    0,
//                    width,
//                    height), 100, outputFile);
//        }
//        try {
//            outputFile.close();
//        } catch (IOException e) {
//            Log.e(TAG, "test screenShot: compress yuv image error: " + e);
//            e.printStackTrace();
//        }
//    }
}

