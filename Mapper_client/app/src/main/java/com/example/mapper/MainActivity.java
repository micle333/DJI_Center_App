package com.example.mapper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, View.OnClickListener {

    private GoogleMap mMap;
    public static String TAG = "MainActivity";

    private FrameLayout fragmentContainer;

    private GoogleMap googleMap;
    private SupportMapFragment mapFragment;
    private Handler handler;
    private View fragmentMapContainer;

    private ImageButton clearButton;

    private TextView latitudeTextView;
    private TextView longitudeTextView;
    public static final int SERVER_PORT = 3003;


    // Параметры преобразования из WGS-84 в СК-42
    private static final double aWGS84 = 6378137.0; // Большая полуось WGS-84
    private static final double fWGS84 = 1 / 298.257223563; // Сжатие WGS-84

    private static final double aSK42 = 6378245.0; // Большая полуось СК-42
    private static final double fSK42 = 1 / 298.3; // Сжатие СК-42

    // Смещения между системами координат
    private static final double dx = 23.92;
    private static final double dy = -141.27;
    private static final double dz = -80.9;

    // Параметры поворота (в радианах)
    private static final double wx = 0;
    private static final double wy = 0;
    private static final double wz = 0.00000090;

    // Масштабное различие
    private static final double ms = -0.00000112;



    // Get this IP from the Device WiFi Settings
    // Make sure you have the devices in same WiFi if testing locally
    // Or Make sure the port specified is open for connections.
    public String SERVER_IP = "192.168.109.206";
    private ClientThread clientThread;
    private Thread thread;
    private static final int REQUEST_CODE_PERMISSIONS = 1;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
    };

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Устанавливаем флаг для предотвращения засыпания
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
        // Устанавливаем флаги для fullscreen режима
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        fragmentContainer = findViewById(R.id.fragmentContainer);
//        fragmentContainer.setVisibility(View.VISIBLE);
        showMapFragment();

        SERVER_IP = getRouterIpAddress(this);
        handler = new Handler();

        latitudeTextView = findViewById(R.id.latitudeTextView);
        longitudeTextView = findViewById(R.id.longitudeTextView);

        clientThread = new ClientThread();
        thread = new Thread(clientThread);
        thread.start();
        clearButton = findViewById(R.id.clearButton);
        clearButton.setOnClickListener(this);


//        showToast("Connecting to Server...");
//        showToast(getIpAddress());
//        showToast(getRouterIpAddress(this));
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.clearButton) {
            googleMap.clear();
        }
    }
    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

//    public void showMessage(final String message, final int color) {
//        handler.post(() -> msgList.addView(textView(message, color)));
//    }

//    private void removeAllViews(){
//        handler.post(() -> msgList.removeAllViews());
//    }


    class ClientThread implements Runnable {

        private Socket socket;
        private BufferedReader input;

        @Override
        public void run() {
            showToast("Client thread started...");

            try {

                InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
                socket = new Socket(serverAddr, SERVER_PORT);
                while (!Thread.currentThread().isInterrupted()) {
                    this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String message = input.readLine();
                    if (null == message || "Disconnect".contentEquals(message)) {
                        boolean interrupted = Thread.interrupted();
                        message = "Server Disconnected: " + interrupted;
                        addMarkerToMap(message);
                        break;
                    }
                    String finalMessage = message;
                    runOnUiThread(() -> addMarkerToMap(finalMessage));
                    showToast(message);
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
                showToast("unknown host...");
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }
    private String getRouterIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        int ipAddress = dhcpInfo.gateway;
        return String.format("%d.%d.%d.%d",
                (ipAddress & 0xff),
                (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff),
                (ipAddress >> 24 & 0xff));
    }

    // Преобразование координат из WGS-84 в СК-42
    // Преобразование из геодезических координат в декартовы
    private static double[] geoToCartesian(double latitude, double longitude, double height, double a, double f) {
        double e2 = 2 * f - f * f;
        double N = a / Math.sqrt(1 - e2 * Math.sin(latitude) * Math.sin(latitude));
        double x = (N + height) * Math.cos(latitude) * Math.cos(longitude);
        double y = (N + height) * Math.cos(latitude) * Math.sin(longitude);
        double z = (N * (1 - e2) + height) * Math.sin(latitude);
        return new double[]{x, y, z};
    }

    // Преобразование из декартовых координат в геодезические
    private static double[] cartesianToGeo(double x, double y, double z, double a, double f) {
        double e2 = 2 * f - f * f;
        double longitude = Math.atan2(y, x);
        double p = Math.sqrt(x * x + y * y);
        double theta = Math.atan2(z * a, p * (1 - f));
        double latitude = Math.atan2(z + e2 * (1 - f) * Math.sin(theta) * Math.sin(theta) * Math.sin(theta),
                p - e2 * a * Math.cos(theta) * Math.cos(theta) * Math.cos(theta));
        double N = a / Math.sqrt(1 - e2 * Math.sin(latitude) * Math.sin(latitude));
        double height = p / Math.cos(latitude) - N;
        return new double[]{latitude, longitude, height};
    }

    // Функция для преобразования из WGS-84 в СК-42
    public static double[] wgs84ToSk42(double latitudeWGS, double longitudeWGS) {
        double heightWGS = 0; // Задаем высоту равной нулю
        double[] xyzWGS = geoToCartesian(Math.toRadians(latitudeWGS), Math.toRadians(longitudeWGS), heightWGS, aWGS84, fWGS84);

        // Преобразование Helmert
        double xSK42 = dx + (1 + ms) * (xyzWGS[0] + wz * xyzWGS[1] - wy * xyzWGS[2]);
        double ySK42 = dy + (1 + ms) * (-wz * xyzWGS[0] + xyzWGS[1] + wx * xyzWGS[2]);
        double zSK42 = dz + (1 + ms) * (wy * xyzWGS[0] - wx * xyzWGS[1] + xyzWGS[2]);

        double[] geoSK42 = cartesianToGeo(xSK42, ySK42, zSK42, aSK42, fSK42);
        return new double[]{Math.toDegrees(geoSK42[0]), Math.toDegrees(geoSK42[1]), geoSK42[2]};
    }


    //    private String getIpAddress() {
//        try {
//            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
//            for (NetworkInterface intf : interfaces) {
//                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
//                for (InetAddress addr : addrs) {
//                    Log.d("IP OF ALL:", addr.getHostAddress());
//                    if (!addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
//                        return addr.getHostAddress();
//                    }
//                }
//            }
//        } catch (SocketException e) {
//            e.printStackTrace();
//        }
//        return "IP Address not found";
//    }
    private void showMapFragment() {
        Log.i(TAG, "Show map");
        mapFragment = new SupportMapFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, mapFragment)
                .commit();
        fragmentContainer.setVisibility(View.VISIBLE); // Сделать контейнер видимым
        mapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap map) {
                googleMap = map;
                googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                // Можно добавить дополнительные действия при готовности карты
            }
        });
    }
    // Метод для изменения размера значка
    private BitmapDescriptor resizeAndCenterMarkerIcon(int resourceId, int width, int height) {
        Bitmap imageBitmap = BitmapFactory.decodeResource(getResources(), resourceId);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false);
        return BitmapDescriptorFactory.fromBitmap(resizedBitmap);
    }


    private void addMarkerToMap(String message) {
        if (googleMap != null) {
            Log.i(TAG, "Try add marker");

            // Разделение координат на широту и долготу
            String[] coordinatesParts = message.split(",");
            if (coordinatesParts.length == 5) {
                double mylatitude = Double.parseDouble(coordinatesParts[0].trim());
                double mylongitude = Double.parseDouble(coordinatesParts[1].trim());

                double latitude = Double.parseDouble(coordinatesParts[2].trim());
                double longitude = Double.parseDouble(coordinatesParts[3].trim());

                float droneYaw = Float.parseFloat((coordinatesParts[4].trim()));

                LatLng myposition = new LatLng(mylatitude, mylongitude);
                LatLng position = new LatLng(latitude, longitude);

                BitmapDescriptor customDroneIcon = resizeAndCenterMarkerIcon(R.drawable.drone_marker_neon, 32, 80);
                BitmapDescriptor customMarkerIcon = resizeAndCenterMarkerIcon(R.drawable.map_marker_neon, 80, 80);

                MarkerOptions DroneMarkerOptions = new MarkerOptions()
                        .position(myposition)
                        .title("Новый маркер")
                        .icon(customDroneIcon)
                        .anchor(0.5f, 0.5f)
                        .rotation((float) droneYaw);

                MarkerOptions markerOptions = new MarkerOptions()
                        .position(position)
                        .title(String.valueOf(latitude) +"; "+  String.valueOf(longitude))
                        .icon(customMarkerIcon)
                        .anchor(0.5f, 0.5f);

                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 5));

                // Построение линии между маркерами

                int lineColor = Color.parseColor("#00ec00");

                PolylineOptions polylineOptions = new PolylineOptions()
                        .add(myposition)
                        .add(position)
                        .color(lineColor) // Цвет линии
                        .width(1); // Ширина линии

                Polyline polyline = googleMap.addPolyline(polylineOptions);
                // Обновление текстовых полей


                double[] sk42 = wgs84ToSk42(latitude, longitude);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        latitudeTextView.setText("Lat: " + sk42[0] );
                        longitudeTextView.setText("Lon: " + sk42[1]);
                    }
                });


                googleMap.addMarker(markerOptions);
                googleMap.addMarker(DroneMarkerOptions);
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15)); // Установка масштаба карты
            }
        }
    }



    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private boolean hasPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissions granted, proceed with your logic
                // startBluetoothOperation();
            } else {
                // Permissions denied, handle this case
                // You may want to inform the user or disable functionality that depends on these permissions
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        LatLng point = new LatLng(55.263388, 36.930084);
        mMap.addMarker(new MarkerOptions().position(point).title("Dasha"));
    }
}
