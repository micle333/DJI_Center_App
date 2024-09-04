package com.dji.ux.sample;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.dji.frame.util.V_JsonUtil;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.internal.cache.component.FlightController;
import dji.log.DJILog;
import dji.log.GlobalConfig;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

/** Main activity that displays three choices to user */
public class MainActivity extends Activity implements PopupMenu.OnMenuItemClickListener {
    private static final String TAG = "MainActivity";
    private static final String LAST_USED_BRIDGE_IP = "bridgeip";
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static boolean isAppStarted = false;
    private BaseProduct mProduct;
    private Button btnOpen;

    private DJISDKManager.SDKManagerCallback registrationCallback = new DJISDKManager.SDKManagerCallback() {

        @Override
        public void onRegister(DJIError error) {
            isRegistrationInProgress.set(true);
            if (error == DJISDKError.REGISTRATION_SUCCESS) {
                DJISDKManager.getInstance().startConnectionToProduct();

//                Toast.makeText(getApplicationContext(), "SDK registration succeeded!", Toast.LENGTH_LONG).show();
            } else {

//                Toast.makeText(getApplicationContext(),
//                               "SDK: " + error.getDescription(),
//                               Toast.LENGTH_LONG).show();
            }
        }
        @Override
        public void onProductDisconnect() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btnOpen.setEnabled(false);
                    TextView Status = (TextView) findViewById(R.id.text_connection_status);
                    TextView MonelName = (TextView) findViewById(R.id.text_model_available);
                    TextView version = (TextView) findViewById(R.id.text_product_info);
                    Status.setText("Disconnected");
//                    MonelName.setText("N/A");
//                    version.setText("N/A");
                }
            });
//            Toast.makeText(getApplicationContext(),
//                           "product disconnect!",
//                           Toast.LENGTH_LONG).show();

        }
        @Override
        public void onProductConnect(BaseProduct product) {

            Toast.makeText(getApplicationContext(),
                           "product connect!",
                           Toast.LENGTH_LONG).show();
        }

        @Override
        public void onProductChanged(BaseProduct product) {

        }

        @Override
        public void onComponentChange(BaseProduct.ComponentKey key,
                                      BaseComponent oldComponent,
                                      BaseComponent newComponent) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    TextView Status = (TextView) findViewById(R.id.text_connection_status);

                    TextView MonelName = (TextView) findViewById(R.id.text_model_available);
                    TextView version = (TextView) findViewById(R.id.text_product_info);
                    BaseProduct product = DJISDKManager.getInstance().getProduct();

                    if (product != null && product.getModel() != null) {
                        btnOpen.setEnabled(true);
                        String productName = product.getModel() != null ? product.getModel().getDisplayName() : "Unknown Model";
                        Status.setText("Connected");
                        MonelName.setText(productName);
                        String ProductVersion = product.getFirmwarePackageVersion() != null ? product.getFirmwarePackageVersion() : "Unknown Version";
                        version.setText(ProductVersion);
                    } else {
                        Status.setText("Disconnected");
                        btnOpen.setEnabled(false);
                        MonelName.setText("N/A");
                        version.setText("Product Info");
                    }
//                    Toast.makeText(getApplicationContext(),
//                            key.toString() + " changed",
//                            Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override
        public void onInitProcess(DJISDKInitEvent event, int totalProcess) {
//            Toast.makeText(getApplicationContext(),
//                    "onInitProcess," + event + "totalProcess," + totalProcess,
//                    Toast.LENGTH_LONG).show();
        }

        @Override
        public void onDatabaseDownloadProgress(long current, long total) {
//            Toast.makeText(getApplicationContext(),
//                    "onDatabaseDownloadProgress" + (int) (100 * current / total),
//                    Toast.LENGTH_LONG).show();
        }
    };

    public static boolean isStarted() {
        return isAppStarted;
    }
    private static final String[] REQUIRED_PERMISSION_LIST = new String[] {
        Manifest.permission.VIBRATE, // Gimbal rotation
        Manifest.permission.INTERNET, // API requests
        Manifest.permission.ACCESS_WIFI_STATE, // WIFI connected products
        Manifest.permission.ACCESS_COARSE_LOCATION, // Maps
        Manifest.permission.ACCESS_NETWORK_STATE, // WIFI connected products
        Manifest.permission.ACCESS_FINE_LOCATION, // Maps
        Manifest.permission.CHANGE_WIFI_STATE, // Changing between WIFI and USB connection
        Manifest.permission.WRITE_EXTERNAL_STORAGE, // Log files
        Manifest.permission.BLUETOOTH, // Bluetooth connected products
        Manifest.permission.BLUETOOTH_ADMIN, // Bluetooth connected products
        Manifest.permission.READ_EXTERNAL_STORAGE, // Log files
        Manifest.permission.RECORD_AUDIO,// Speaker accessory
        Manifest.permission.ACCESS_FINE_LOCATION,
//        Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE
    };
    private static final int REQUEST_PERMISSION_CODE = 12345;
    private List<String> missingPermission = new ArrayList<>();
    private EditText bridgeModeEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        isAppStarted = true;
        btnOpen = findViewById(R.id.btn_open);
        btnOpen.setEnabled(false);

        hideSystemUI();

        btnOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Class nextActivityClass = CompleteWidgetActivity.class;
                Intent intent = new Intent(MainActivity.this, nextActivityClass);
                startActivity(intent);
            }
        });

        checkAndRequestPermissions();
    }
//    public void refreshSDKRelativeUI() {
//        btnOpen.setEnabled(true);
//        mProduct = DJISampleApplication.getProductInstance();
//        Log.d(TAG, "mProduct: " + (mProduct == null ? "null" : "unnull"));
//        if (null != mProduct ) {
//            if (mProduct.isConnected()) {
//                btnOpen.setEnabled(true);
//                String str = mProduct instanceof Aircraft ? "DJIAircraft" : "DJIHandHeld";
//                textConnectionStatus.setText("Status: " + str + " connected");
//
//                if (mProduct instanceof Aircraft) {
//                    addAppActivationListenerIfNeeded();
//                }
//
//                if (null != mProduct.getModel()) {
//                    textProductInfo.setText("" + mProduct.getModel().getDisplayName());
//                } else {
//                    textProductInfo.setText(R.string.product_information);
//                }
//            } else if (mProduct instanceof Aircraft){
//                Aircraft aircraft = (Aircraft) mProduct;
//                if (aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
//                    textConnectionStatus.setText(R.string.connection_only_rc);
//                    textProductInfo.setText(R.string.product_information);
//                    btnOpen.setEnabled(false);
//                    textModelAvailable.setText("Firmware version:N/A");
//                }
//            }
//        } else {
//            btnOpen.setEnabled(false);
//            textProductInfo.setText(R.string.product_information);
//            textConnectionStatus.setText(R.string.connection_loose);
//            textModelAvailable.setText("Firmware version:N/A");
//        }
//    }

    private void hideSystemUI() {
        // Включить immersive mode для полной полноэкранной работы
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }


//    @Override
//    public void onClick(View view) {
//        Class nextActivityClass;
//
//        int id = view.getId();
//        if (id == R.id.complete_ui_widgets) {
//            nextActivityClass = CompleteWidgetActivity.class;
//        } else {
//            //nextActivityClass = MapWidgetActivity.class;
//            PopupMenu popup = new PopupMenu(this, view);
//            popup.setOnMenuItemClickListener(this);
//            Menu popupMenu = popup.getMenu();
//            MenuInflater inflater = popup.getMenuInflater();
//            inflater.inflate(R.menu.map_select_menu, popupMenu);
//            popupMenu.findItem(R.id.here_map).setEnabled(isHereMapsSupported());
//            popupMenu.findItem(R.id.google_map).setEnabled(isGoogleMapsSupported(this));
//            popup.show();
//            return;
//        }
//
//        Intent intent = new Intent(this, nextActivityClass);
//        startActivity(intent);
//    }

    @Override
    protected void onDestroy() {
        DJISDKManager.getInstance().destroy();
        isAppStarted = false;
        super.onDestroy();
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            ActivityCompat.requestPermissions(this,
                                              missingPermission.toArray(new String[missingPermission.size()]),
                                              REQUEST_PERMISSION_CODE);
        }
    }

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            Toast.makeText(getApplicationContext(), "Missing permissions! Will not register SDK to connect to aircraft." + V_JsonUtil.toJson(missingPermission), Toast.LENGTH_LONG).show();
        }
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    DJISDKManager.getInstance().registerApp(MainActivity.this, registrationCallback);
                }
            });
        }
    }



    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        Intent intent = new Intent(this, MapWidgetActivity.class);
        int mapBrand = 1;
        switch (menuItem.getItemId()) {
            case R.id.here_map:
                mapBrand = 0;
                break;
            case R.id.google_map:
                mapBrand = 1;
                break;
            case R.id.amap:
                mapBrand = 2;
                break;
            case R.id.mapbox:
                mapBrand = 3;
                break;
        }
        intent.putExtra(MapWidgetActivity.MAP_PROVIDER, mapBrand);
        startActivity(intent);
        return false;
    }

    public static boolean isHereMapsSupported() {
        String abi;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            abi = Build.CPU_ABI;
        } else {
            abi = Build.SUPPORTED_ABIS[0];
        }
        DJILog.d(TAG, "abi=" + abi);

        //The possible values are armeabi, armeabi-v7a, arm64-v8a, x86, x86_64, mips, mips64.
        return abi.contains("arm");
    }

    public static boolean isGoogleMapsSupported(Context context) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
    }

    private void handleBridgeIPTextChange() {
        // the user is done typing.
        final String bridgeIP = bridgeModeEditText.getText().toString();

        if (!TextUtils.isEmpty(bridgeIP)) {
            DJISDKManager.getInstance().enableBridgeModeWithBridgeAppIP(bridgeIP);
            Toast.makeText(getApplicationContext(),"BridgeMode ON!\nIP: " + bridgeIP,Toast.LENGTH_SHORT).show();
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(LAST_USED_BRIDGE_IP,bridgeIP).apply();
        }
    }
}


//double pitch = gimbalState.getAttitudeInDegrees().getPitch();
//double yaw = gimbalState.getAttitudeInDegrees().getYaw();
//
//double flightHeight = flightControllerState.getAircraftLocation().getAltitude();
//double latitude = flightControllerState.getAircraftLocation().getLatitude();
//double longitudeText = flightControllerState.getAircraftLocation().getLongitude();