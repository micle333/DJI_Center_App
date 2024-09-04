package com.dji.ux.sample;

import com.dji.mapkit.core.maps.DJIMap;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.nfc.Tag;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.gimbal.GimbalState;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.ux.widget.FPVOverlayWidget;
import dji.ux.widget.FPVWidget;
import dji.ux.widget.MapWidget;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dji.keysdk.CameraKey;
import dji.keysdk.KeyManager;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.ux.panel.CameraSettingAdvancedPanel;
import dji.ux.panel.CameraSettingExposurePanel;
import dji.ux.utils.DJIProductUtil;
import dji.ux.widget.ThermalPaletteWidget;
import dji.ux.widget.config.CameraConfigApertureWidget;
import dji.ux.widget.config.CameraConfigEVWidget;
import dji.ux.widget.config.CameraConfigISOAndEIWidget;
import dji.ux.widget.config.CameraConfigSSDWidget;
import dji.ux.widget.config.CameraConfigShutterWidget;
import dji.ux.widget.config.CameraConfigStorageWidget;
import dji.ux.widget.config.CameraConfigWBWidget;
import dji.ux.widget.controls.CameraControlsWidget;
import dji.ux.widget.controls.LensControlWidget;

/**
 * Activity that shows all the UI elements together
 */
public class CompleteWidgetActivity extends Activity  {

    private MapWidget mapWidget;
    private ViewGroup parentView;
    private FPVWidget fpvWidget;
    private FPVWidget secondaryFPVWidget;
    private FPVOverlayWidget fpvOverlayWidget;
    private RelativeLayout primaryVideoView;
    private FrameLayout secondaryVideoView;
    private boolean isMapMini = true;

    private CameraSettingExposurePanel cameraSettingExposurePanel;
    private CameraSettingAdvancedPanel cameraSettingAdvancedPanel;
    private CameraConfigISOAndEIWidget cameraConfigISOAndEIWidget;
    private CameraConfigShutterWidget cameraConfigShutterWidget;
    private CameraConfigApertureWidget cameraConfigApertureWidget;
    private CameraConfigEVWidget cameraConfigEVWidget;
    private CameraConfigWBWidget cameraConfigWBWidget;
    private CameraConfigStorageWidget cameraConfigStorageWidget;
    private CameraConfigSSDWidget cameraConfigSSDWidget;
    private CameraControlsWidget controlsWidget;
    private LensControlWidget lensControlWidget;
    private ThermalPaletteWidget thermalPaletteWidget;


    private int height;
    private int width;
    private int margin;
    private int deviceWidth;
    private int deviceHeight;

    private TextView gimbalPitchTextView;
    private TextView gimbalYawTextView;
    private TextView flightHeightTextView;
    private TextView flightLatitudeTextView;
    private TextView flightLongitudeTextView;

    private TextView droneCoordinatesTextView;
    private TextView calculatedPointTextView;
    private ImageButton calculatePointButton;

    private double currentLatitude;
    private double currentLongitude;
    private float currentAltitude;
    private float gimbalPitch;
    private float droneYaw;
    public static final String TAG = "MainActivity";

    //////////  Socket Server ///////////
    public static final int SERVER_PORT = 3003;
    private static final String SERVER_IP = "192.168.109.67";
    private ServerSocket serverSocket;
    private Socket tempClientSocket;
    private Thread serverThread;

    private LinearLayout msgList;
    private Handler handler;
    private EditText edMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_default_widgets);

        // Устанавливаем флаг для предотвращения засыпания
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        gimbalPitchTextView = findViewById(R.id.gimbal_pitch);
        gimbalYawTextView = findViewById(R.id.gimbal_yaw);
        flightHeightTextView = findViewById(R.id.flight_height);
        flightLatitudeTextView = findViewById(R.id.flight_latitude);
        flightLongitudeTextView = findViewById(R.id.flight_longitude);

//        calculatedPointTextView = findViewById(R.id.calculated_point);
        calculatePointButton = findViewById(R.id.calculate_point_button);

        DJISDKManager.getInstance().registerApp(this, new DJISDKManager.SDKManagerCallback() {
            @Override
            public void onRegister(DJIError error) {
                if (error == DJISDKError.REGISTRATION_SUCCESS) {
                    DJISDKManager.getInstance().startConnectionToProduct();
                    initGimbalData();
                    initFlightController();
                } else {
//                    showToast("Registration failed: " + error.getDescription());
                }
            }

            @Override
            public void onProductDisconnect() {
//                showToast("Product disconnected");
            }

            @Override
            public void onProductConnect(BaseProduct baseProduct) {
//                showToast("Product connected");
                initGimbalData();
                initFlightController();
            }

            @Override
            public void onProductChanged(BaseProduct baseProduct) {
//                showToast("Product changed");
            }

            @Override
            public void onComponentChange(BaseProduct.ComponentKey componentKey, BaseComponent oldComponent, BaseComponent newComponent) {
                if (newComponent != null) {
                    newComponent.setComponentListener(isConnected -> {
//                        showToast(componentKey.name() + " is " + (isConnected ? "connected" : "disconnected"));
                        if (componentKey == BaseProduct.ComponentKey.GIMBAL) {
                            initGimbalData();
                            initFlightController();
                        }
                    });
                }
            }

            @Override
            public void onInitProcess(DJISDKInitEvent event, int totalProcess) {
            }

            @Override
            public void onDatabaseDownloadProgress(long current, long total) {
            }
        });

        height = DensityUtil.dip2px(this, 100);
        width = DensityUtil.dip2px(this, 150);
        margin = DensityUtil.dip2px(this, 12);

        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        final Display display = windowManager.getDefaultDisplay();
        Point outPoint = new Point();
        display.getRealSize(outPoint);
        deviceHeight = outPoint.y;
        deviceWidth = outPoint.x;

        mapWidget = (MapWidget) findViewById(R.id.map_widget);
        mapWidget.initAMap(map -> map.setOnMapClickListener((DJIMap.OnMapClickListener) latLng -> onViewClick(mapWidget)));
        mapWidget.onCreate(savedInstanceState);

        initCameraView();
        parentView = (ViewGroup) findViewById(R.id.root_view);

        fpvWidget = findViewById(R.id.fpv_widget);
        fpvWidget.setOnClickListener(view -> onViewClick(fpvWidget));
        fpvOverlayWidget = findViewById(R.id.fpv_overlay_widget);
        primaryVideoView = findViewById(R.id.fpv_container);
        secondaryVideoView = findViewById(R.id.secondary_video_view);
        secondaryFPVWidget = findViewById(R.id.secondary_fpv_widget);
        secondaryFPVWidget.setOnClickListener(view -> swapVideoSource());

        fpvWidget.setCameraIndexListener((cameraIndex, lensIndex) -> cameraWidgetKeyIndexUpdated(fpvWidget.getCameraKeyIndex(), fpvWidget.getLensKeyIndex()));
        updateSecondaryVideoVisibility();

        initGimbalData();
        initFlightController();

        handler = new Handler();
        this.serverThread = new Thread(new ServerThread());
        this.serverThread.start();
//        showToast("serverThread started...");

        calculatePointButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calculatePointOnGround();
            }
        });

    }

    private void sendMessage(final String message) {

        try {
//            showToast("try to send message...");
            if (null != tempClientSocket) {
                new Thread(() -> {
                    PrintWriter out = null;
                    try {
//                        showToast("try to send...");
                        out = new PrintWriter(new BufferedWriter(
                                new OutputStreamWriter(tempClientSocket.getOutputStream())),
                                true);
                    } catch (IOException e) {
//                        showToast("dont send...");
                        e.printStackTrace();
                    }
                    out.println(message);
                }).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class ServerThread implements Runnable {

        public void run() {
            Socket socket;
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG,"Error Starting Server");
            }
            if (null != serverSocket) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        socket = serverSocket.accept();
                        CommunicationThread commThread = new CommunicationThread(socket);
                        new Thread(commThread).start();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.d(TAG,"Error Communicating to Client :");
                    }
                }
            }
        }
    }

    class CommunicationThread implements Runnable {

        private final Socket clientSocket;

        private BufferedReader input;

        public CommunicationThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
            tempClientSocket = clientSocket;
            try {
                this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
                showToast("Error Connecting to Client.");
                return;
            }
            showToast("Mapper Connected.");
        }

        public void run() {

            while (!Thread.currentThread().isInterrupted()) try {
                String read = input.readLine();
                if (null == read || "Disconnect".contentEquals(read)) {
                    boolean interrupted = Thread.interrupted();
                    read = "Client Disconnected: " + interrupted;
                    Log.d(TAG,"Client : " + read);
                    break;
                }
                Log.d(TAG,"Client : " + read);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }



    public static double getRealPitch(double pitch) {
        if (pitch < -30 || pitch > 0) {
            return -1;
        } else {
            System.out.println("Koeff: " + (2.0 / 75.0 * pitch - 0.2));
            return (1.0 / 150.0 * pitch - 0.8);
        }
    }
    private void calculatePointOnGround() {
        if (gimbalPitch == 0 || currentAltitude == 0 || currentLatitude == 0 || currentLongitude == 0) {
            currentAltitude = 100;
            currentLatitude = 55.810756;
            currentLongitude = 37.500652;
//            Toast.makeText(this, "Invalid data --> send", Toast.LENGTH_SHORT).show();
//            Toast.makeText(this, "Data is not available yet", Toast.LENGTH_SHORT).show();
//            return;
        }

        double R = 6378137; // Radius of Earth in meters

        // Calculate the distance on the ground
        double distance = currentAltitude / Math.tan(Math.toRadians(gimbalPitch)) * getRealPitch(gimbalPitch);

        double deltaLat = (distance * Math.cos(Math.toRadians(droneYaw))) / R;
        double deltaLon = (distance * Math.sin(Math.toRadians(droneYaw))) / (R * Math.cos(Math.toRadians(currentLatitude)));

        // Calculate the target latitude and longitude
        double targetLatitude = currentLatitude + Math.toDegrees(deltaLat);
        double targetLongitude = currentLongitude + Math.toDegrees(deltaLon);

        String message = currentLatitude + "," + currentLongitude + "," + targetLatitude + "," + targetLongitude + "," + droneYaw;
        sendMessage(message);
        Log.d(TAG, String.valueOf(droneYaw));

//        String calculatedPointText = "Target Lat: " + targetLatitude + ", Target Lon: " + targetLongitude;
//        calculatedPointTextView.setText(calculatedPointText);

//        Toast.makeText(this, calculatedPointText, Toast.LENGTH_SHORT).show();
    }





    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }
    private void initGimbalData() {
        if (ModuleVerificationUtil.isGimbalModuleAvailable()) {
            DJISampleApplication.getProductInstance().getGimbal().setStateCallback(new GimbalState.Callback() {
                @Override
                public void onUpdate(@NonNull GimbalState gimbalState) {
                    runOnUiThread(() -> {
                        String pitchText = "Pitch: " + gimbalState.getAttitudeInDegrees().getPitch() + "°";
                        String yawText = "Yaw: " + gimbalState.getAttitudeInDegrees().getYaw() + "°";

                        gimbalPitch = gimbalState.getAttitudeInDegrees().getPitch();
                        droneYaw = gimbalState.getAttitudeInDegrees().getYaw();

                        gimbalPitchTextView.setText(pitchText);
                        gimbalYawTextView.setText(yawText);
                    });
                }
            });
        }
    }
    private void initFlightController() {
        BaseProduct product = DJISampleApplication.getProductInstance();
        if (product != null && product instanceof Aircraft) {
            FlightController flightController = ((Aircraft) product).getFlightController();
            if (flightController != null) {
                flightController.setStateCallback(new FlightControllerState.Callback() {
                    @Override
                    public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                        runOnUiThread(() -> {
                            String flightHeightText = "Height: " + flightControllerState.getAircraftLocation().getAltitude() + " m";
                            String latitudeText = "Latitude: " + flightControllerState.getAircraftLocation().getLatitude();
                            String longitudeText = "Longitude: " + flightControllerState.getAircraftLocation().getLongitude();

                            currentLatitude = flightControllerState.getAircraftLocation().getLatitude();
                            currentLongitude = flightControllerState.getAircraftLocation().getLongitude();
                            currentAltitude = flightControllerState.getAircraftLocation().getAltitude();
//                            droneYaw = flightControllerState.getAircraftHeadDirection();

                            flightHeightTextView.setText(flightHeightText);
                            flightLatitudeTextView.setText(latitudeText);
                            flightLongitudeTextView.setText(longitudeText);
                        });
                    }
                });
            } else {
                Toast.makeText(this, "Flight Controller not available", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Product not available or not an Aircraft", Toast.LENGTH_SHORT).show();
        }
    }


    private void initCameraView() {
        cameraSettingExposurePanel = findViewById(R.id.camera_setting_exposure_panel);
        cameraSettingAdvancedPanel = findViewById(R.id.camera_setting_advanced_panel);
        cameraConfigISOAndEIWidget = findViewById(R.id.camera_config_iso_and_ei_widget);
        cameraConfigShutterWidget = findViewById(R.id.camera_config_shutter_widget);
        cameraConfigApertureWidget = findViewById(R.id.camera_config_aperture_widget);
        cameraConfigEVWidget = findViewById(R.id.camera_config_ev_widget);
        cameraConfigWBWidget = findViewById(R.id.camera_config_wb_widget);
        cameraConfigStorageWidget = findViewById(R.id.camera_config_storage_widget);
        cameraConfigSSDWidget = findViewById(R.id.camera_config_ssd_widget);
        lensControlWidget = findViewById(R.id.camera_lens_control);
        controlsWidget = findViewById(R.id.CameraCapturePanel);
        thermalPaletteWidget = findViewById(R.id.thermal_pallette_widget);
    }

    private void onViewClick(View view) {
        if (view == fpvWidget && !isMapMini) {
            resizeFPVWidget(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT, 0, 0);
            reorderCameraCapturePanel();
            ResizeAnimation mapViewAnimation = new ResizeAnimation(mapWidget, deviceWidth, deviceHeight, width, height, margin);
            mapWidget.startAnimation(mapViewAnimation);
            isMapMini = true;
        } else if (view == mapWidget && isMapMini) {
            hidePanels();
            resizeFPVWidget(width, height, margin, 12);
            reorderCameraCapturePanel();
            ResizeAnimation mapViewAnimation = new ResizeAnimation(mapWidget, width, height, deviceWidth, deviceHeight, 0);
            mapWidget.startAnimation(mapViewAnimation);
            isMapMini = false;
        }
    }

    private void resizeFPVWidget(int width, int height, int margin, int fpvInsertPosition) {
        RelativeLayout.LayoutParams fpvParams = (RelativeLayout.LayoutParams) primaryVideoView.getLayoutParams();
        fpvParams.height = height;
        fpvParams.width = width;
        fpvParams.rightMargin = margin;
        fpvParams.bottomMargin = margin;
        if (isMapMini) {
            fpvParams.addRule(RelativeLayout.CENTER_IN_PARENT, 0);
            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        } else {
            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
            fpvParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
            fpvParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        }
        primaryVideoView.setLayoutParams(fpvParams);

        parentView.removeView(primaryVideoView);
        parentView.addView(primaryVideoView, fpvInsertPosition);
    }

    private void reorderCameraCapturePanel() {
        View cameraCapturePanel = findViewById(R.id.CameraCapturePanel);
        parentView.removeView(cameraCapturePanel);
        parentView.addView(cameraCapturePanel, isMapMini ? 9 : 13);
    }

    private void swapVideoSource() {
        if (secondaryFPVWidget.getVideoSource() == FPVWidget.VideoSource.SECONDARY) {
            fpvWidget.setVideoSource(FPVWidget.VideoSource.SECONDARY);
            secondaryFPVWidget.setVideoSource(FPVWidget.VideoSource.PRIMARY);
        } else {
            fpvWidget.setVideoSource(FPVWidget.VideoSource.PRIMARY);
            secondaryFPVWidget.setVideoSource(FPVWidget.VideoSource.SECONDARY);
        }
    }

    private void cameraWidgetKeyIndexUpdated(int keyIndex, int subKeyIndex) {
        controlsWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraSettingExposurePanel.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraSettingAdvancedPanel.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraConfigISOAndEIWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraConfigShutterWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraConfigApertureWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraConfigEVWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraConfigWBWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraConfigStorageWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        cameraConfigSSDWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        controlsWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        lensControlWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
        thermalPaletteWidget.updateKeyOnIndex(keyIndex, subKeyIndex);

        fpvOverlayWidget.updateKeyOnIndex(keyIndex, subKeyIndex);
    }

    private void updateSecondaryVideoVisibility() {
        if (secondaryFPVWidget.getVideoSource() == null || !DJIProductUtil.isSupportMultiCamera()) {
            secondaryVideoView.setVisibility(View.GONE);
        } else {
            secondaryVideoView.setVisibility(View.VISIBLE);
        }
    }

    private void hidePanels() {
        //These panels appear based on keys from the drone itself.
        if (KeyManager.getInstance() != null) {
            KeyManager.getInstance().setValue(CameraKey.create(CameraKey.HISTOGRAM_ENABLED, fpvWidget.getCameraKeyIndex()), false, null);
            KeyManager.getInstance().setValue(CameraKey.create(CameraKey.COLOR_WAVEFORM_ENABLED, fpvWidget.getCameraKeyIndex()), false, null);
        }

        //These panels have buttons that toggle them, so call the methods to make sure the button state is correct.
        controlsWidget.setAdvancedPanelVisibility(false);
        controlsWidget.setExposurePanelVisibility(false);

        //These panels don't have a button state, so we can just hide them.
        findViewById(R.id.pre_flight_check_list).setVisibility(View.GONE);
        findViewById(R.id.rtk_panel).setVisibility(View.GONE);
        //findViewById(R.id.simulator_panel).setVisibility(View.GONE);
        findViewById(R.id.spotlight_panel).setVisibility(View.GONE);
        findViewById(R.id.speaker_panel).setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Hide both the navigation bar and the status bar.
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        mapWidget.onResume();
    }

    @Override
    protected void onPause() {
        mapWidget.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mapWidget.onDestroy();

        if (null != serverThread) {
            sendMessage("Disconnect");
            serverThread.interrupt();
            serverThread = null;
        }

        BaseProduct product = DJISampleApplication.getProductInstance();
        if (product != null && product.getGimbal() != null) {
            product.getGimbal().setStateCallback(null);
        }

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapWidget.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapWidget.onLowMemory();
    }

    private class ResizeAnimation extends Animation {

        private View mView;
        private int mToHeight;
        private int mFromHeight;

        private int mToWidth;
        private int mFromWidth;
        private int mMargin;

        private ResizeAnimation(View v, int fromWidth, int fromHeight, int toWidth, int toHeight, int margin) {
            mToHeight = toHeight;
            mToWidth = toWidth;
            mFromHeight = fromHeight;
            mFromWidth = fromWidth;
            mView = v;
            mMargin = margin;
            setDuration(300);
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            float height = (mToHeight - mFromHeight) * interpolatedTime + mFromHeight;
            float width = (mToWidth - mFromWidth) * interpolatedTime + mFromWidth;
            RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) mView.getLayoutParams();
            p.height = (int) height;
            p.width = (int) width;
            p.rightMargin = mMargin;
            p.bottomMargin = mMargin;
            mView.requestLayout();
        }
    }
}
