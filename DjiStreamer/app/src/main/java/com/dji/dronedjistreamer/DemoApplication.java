package com.dji.dronedjistreamer;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;

public class DemoApplication extends Application {

    public static final String FLAG_AIRCRAFT_CONNECTED = "aircraft_connected";
    public static final String FLAG_AIRCRAFT_DISCONNECTED = "aircraft_disconnected";
    public static final String FLAG_PRODUCT_CHANGED = "product_changed";
    public static final String FLAG_COMPONENT_CONNECTIVITY_CHANGED = "component_connectivity_changed";

    private static BaseProduct mProduct;
    private Handler mHandler;
    private DJISDKManager.SDKManagerCallback mDJISDKManagerCallback;

    private static Application instance;
    public void setContext(Application application) {
        instance = application;
    }

    public static Application getInstance() {
        return DemoApplication.instance;
    }

    @Override
    public Context getApplicationContext() {
        return instance;
    }

    public DemoApplication() {

    }

    /**
     * This function is used to get the instance of DJIBaseProduct.
     * If no product is connected, it returns null.
     */
    public static synchronized BaseProduct getProductInstance() {
        if (null == mProduct) {
            mProduct = DJISDKManager.getInstance().getProduct();
        }
        return mProduct;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("DJI_sdk_demoapplication", "onCreate");
        mHandler = new Handler(Looper.getMainLooper());

        /**
         * When starting SDK services, an instance of interface DJISDKManager.DJISDKManagerCallback will be used to listen to
         * the SDK Registration result and the product changing.
         */
        mDJISDKManagerCallback = new DJISDKManager.SDKManagerCallback() {

            //Listens to the SDK registration result
            @Override
            public void onRegister(DJIError error) {
                Log.i("DJI_sdk_demoapplication", "onRegister. " + error.toString());
                if(error == DJISDKError.REGISTRATION_SUCCESS) {
                    DJISDKManager.getInstance().startConnectionToProduct();
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Register Success", Toast.LENGTH_LONG).show();
                        }
                    });
                    loginAccount();

                } else {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {

                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Register Failed, check network is available", Toast.LENGTH_LONG).show();
                        }
                    });

                }
                Log.e("TAG", error.toString());
            }

            @Override
            public void onProductDisconnect() {
                Log.i("DJI_sdk_demoapplication", "onProductDisconnect.");
                Log.d("TAG", "onProductDisconnect");
                Toast.makeText(getApplicationContext(), "onProductDisconnect", Toast.LENGTH_LONG).show();
                notifyStatusChange(FLAG_AIRCRAFT_DISCONNECTED);
            }
            @Override
            public void onProductConnect(BaseProduct baseProduct) {
                Log.i("DJI_sdk_demoapplication", "onProductConnect: " + baseProduct.getModel().toString());
                Log.d("TAG", String.format("onProductConnect newProduct:%s", baseProduct));
                Toast.makeText(getApplicationContext(), "onProductConnect", Toast.LENGTH_LONG).show();
                notifyStatusChange(FLAG_AIRCRAFT_CONNECTED);
            }

            @Override
            public void onProductChanged(BaseProduct baseProduct) {
                Log.i("DJI_sdk_demoapplication", "onProductChanged " + baseProduct.getModel().toString());
                Log.d("TAG", String.format("onProductChanged newProduct:%s", baseProduct));
                Toast.makeText(getApplicationContext(), "onProductChanged", Toast.LENGTH_LONG).show();
                notifyStatusChange(FLAG_PRODUCT_CHANGED);
            }
            @Override
            public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent,
                                          BaseComponent newComponent) {
                Log.i("DJI_sdk_demoapplication", "onComponentChange.");
                if (newComponent != null) {
                    newComponent.setComponentListener(new BaseComponent.ComponentListener() {

                        @Override
                        public void onConnectivityChange(boolean isConnected) {
                            Log.d("TAG", "onComponentConnectivityChanged: " + isConnected);
                            notifyStatusChange(FLAG_COMPONENT_CONNECTIVITY_CHANGED);
                        }
                    });
                }

                Log.d("TAG",
                        String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                componentKey,
                                oldComponent,
                                newComponent));

            }
            @Override
            public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {
                Log.i("DJI_sdk_demoapplication", "onInitProcess.");
            }

            @Override
            public void onDatabaseDownloadProgress(long l, long l1) {
                Log.i("DJI_sdk_demoapplication", "onDatabaseDownloadProgress.");
            }

        };

        //Check the permissions before registering the application for android system 6.0 above.
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permissionCheck2 = ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.READ_PHONE_STATE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || (permissionCheck == 0 && permissionCheck2 == 0)) {

            //This is used to start SDK services and initiate SDK.
            DJISDKManager.getInstance().registerApp(getApplicationContext(), mDJISDKManagerCallback);
        } else {
            Toast.makeText(getApplicationContext(), "Please check if the permission is granted.", Toast.LENGTH_LONG).show();
        }
    }

    private void loginAccount(){

        UserAccountManager.getInstance().logIntoDJIUserAccount(this,
                new CommonCallbacks.CompletionCallbackWith<UserAccountState>() {
                    @Override
                    public void onSuccess(final UserAccountState userAccountState) {
                        Log.e("TAG", "Login Success");
                    }
                    @Override
                    public void onFailure(DJIError error) {
                        Log.e("TAG", "Login Error:" + error.getDescription());
                    }
                });
    }

    private void notifyStatusChange(String flag) {
        Runnable runnable = updateRunnable(flag);
        mHandler.removeCallbacks(runnable);
        mHandler.postDelayed(runnable, 500);
    }

    private Runnable updateRunnable(String flag) {
        return new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(flag);
                getApplicationContext().sendBroadcast(intent);
            }
        };
    }

//    private Runnable updateRunnable = new Runnable() {
//
//        @Override
//        public void run() {
//            Intent intent = new Intent(flag);
//            getApplicationContext().sendBroadcast(intent);
//        }
//    };

    public static boolean isAircraftConnected() {
        return getProductInstance() != null && getProductInstance() instanceof Aircraft;
    }

    public static synchronized Aircraft getAircraftInstance() {
        if (!isAircraftConnected()) {
            return null;
        }
        return (Aircraft) getProductInstance();
    }

}
