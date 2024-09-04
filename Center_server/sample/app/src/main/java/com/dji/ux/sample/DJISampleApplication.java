package com.dji.ux.sample;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

public class DJISampleApplication extends Application {

    private static final String TAG = DJISampleApplication.class.getName();
    private static BaseProduct product;

    @Override
    public void onCreate() {
        super.onCreate();
        DJISDKManager.getInstance().registerApp(this, new DJISDKManager.SDKManagerCallback() {
            @Override
            public void onRegister(DJIError error) {
                if (error == DJISDKError.REGISTRATION_SUCCESS) {
                    Log.d(TAG, "DJI SDK registration successful");
                    DJISDKManager.getInstance().startConnectionToProduct();
                } else {
                    Log.d(TAG, "DJI SDK registration failed: " + error.getDescription());
                }
            }

            @Override
            public void onProductDisconnect() {
                Log.d(TAG, "Product disconnected");
                product = null;
            }

            @Override
            public void onProductConnect(BaseProduct newProduct) {
                Log.d(TAG, "Product connected: " + newProduct);
                product = newProduct;
            }

            @Override
            public void onProductChanged(BaseProduct newProduct) {
                product = newProduct;
            }

            @Override
            public void onComponentChange(BaseProduct.ComponentKey key, BaseComponent oldComponent, BaseComponent newComponent) {
                if (newComponent != null) {
                    Log.d(TAG, key.name() + " changed");
                }
            }

            @Override
            public void onInitProcess(DJISDKInitEvent event, int totalProcess) {
                // Handle SDK initialization process
            }

            @Override
            public void onDatabaseDownloadProgress(long current, long total) {
                // Handle database download progress
            }
        });
    }

    public static boolean isAircraftConnected() {
        return getProductInstance() != null && getProductInstance() instanceof Aircraft;
    }

    public static synchronized Aircraft getAircraftInstance() {
        if (!isAircraftConnected()) {
            return null;
        }
        return (Aircraft) getProductInstance();
    }


    public static synchronized BaseProduct getProductInstance() {
        product = DJISDKManager.getInstance().getProduct();
        return product;
    }
}
