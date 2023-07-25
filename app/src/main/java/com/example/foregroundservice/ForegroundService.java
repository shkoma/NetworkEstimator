package com.example.foregroundservice;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.TelephonyManager;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

// https://github.com/kazimdsaidul/Foreground-Service-Android-Example/blob/master/app/src/main/java/com/kazi/foregroundserviceandroidexample/ForegroundService.java

public class ForegroundService extends Service {
    public static final String TAG = ForegroundService.class.getSimpleName();
    public static final String CHANNEL_ID = ForegroundService.class.getSimpleName() + "_Channel";

    public static final String ACTION_SERVICE_TRI_TO_START = "android.intent.action.SERVICE_TRI_TO_START";
    public static final String ACTION_SERVICE_TRI_TO_STOP = "android.intent.action.SERVICE_TRI_TO_STOP";
    public static final String ACTION_TERMINATE_SERVICE = "android.intent.action.TERMINATE_SERVICE";

    public static final int MESSAGE_BEGIN = 0;
    public static final int MESSAGE_COUNT_NOTIFICATION = 0;
    public static final int MESSAGE_GET_INFO = 1;
    public static final int MESSAGE_END = 1;

    private static final int DELAYED_TIME = 1000;
    private static final int NOTIFICATION_ID = 10;

    private NotificationManager mNotificationManager;
    private WifiManager mWifiManager;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Receive: " + intent.getAction());
            if (ACTION_SERVICE_TRI_TO_START.equals(intent.getAction())) {
                startThread();
                startWifiScan();
                mHandler.sendEmptyMessageDelayed(MESSAGE_GET_INFO, DELAYED_TIME * 2);
            } else if (ACTION_SERVICE_TRI_TO_STOP.equals(intent.getAction())) {
                stopThread();
            } else if (ACTION_TERMINATE_SERVICE.equals(intent.getAction())) {
                stopThread();
                stopForeground(true);
//                stopSelf();
            } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
            }
        }
    };

    Handler mHandler;
    HandlerThread mHandlerThread;
    ControlSubThread mThread;

    private FusedLocationProviderClient fusedLocationProviderClient;

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = getSystemService(NotificationManager.class);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mHandlerThread = new HandlerThread("ForegroundService Handler");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_COUNT_NOTIFICATION:
                        NotificationCompat.Builder notificationBuilder = getNotificationBuilder(msg.arg1);
                        startForeground(NOTIFICATION_ID, notificationBuilder.build());
                        break;
                    case MESSAGE_GET_INFO:
                        getCellInformation();
                        getCurrentLocation();
                        getCurrentWifiInfo();
                        sendEmptyMessageDelayed(MESSAGE_GET_INFO, DELAYED_TIME * 2);
                        break;
                }
            }
        };
        mThread = new ControlSubThread(mHandler, DELAYED_TIME);

        IntentFilter filter = new IntentFilter(ACTION_SERVICE_TRI_TO_STOP);
        filter.addAction(ACTION_SERVICE_TRI_TO_START);
        filter.addAction(ACTION_TERMINATE_SERVICE);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(mReceiver, filter);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        createNotificationChannel();
        NotificationCompat.Builder notificationBuilder = getNotificationBuilder(0);
        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        return super.onStartCommand(intent, flags, startId);
    }

    private NotificationCompat.Builder getNotificationBuilder(int count) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service Test")
                .setContentText("Run service: " + count)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE)
                .setVibrate(null)
                .setContentIntent(pendingIntent);
        return notificationBuilder;
    }

    private void prepareToDestroy() {
        unregisterReceiver(mReceiver);
        removeAllMessage();
        mHandler.getLooper().quit();
        mHandlerThread.interrupt();
        mHandlerThread.quitSafely();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        prepareToDestroy();
        stopThread();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "Foreground Service Stopped", Toast.LENGTH_SHORT).show();
        prepareToDestroy();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        Log.i(TAG, "createNotificationChannel");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.enableVibration(true);
            serviceChannel.enableLights(true);
            serviceChannel.setDescription("Foreground service is running");

            mNotificationManager.createNotificationChannel(serviceChannel);
        }
    }

    private void removeAllMessage() {
        for (int i = MESSAGE_BEGIN; i <= MESSAGE_END; i++) {
            mHandler.removeMessages(i);
        }
    }

    private void stopThread() {
        if (mThread != null && mThread.isRunning()) {
            mThread.interrupt();
        }
        mThread = null;
        removeAllMessage();
    }

    private void startThread() {
        if (mThread == null) {
            mThread = new ControlSubThread(mHandler, DELAYED_TIME);
        }

        if (mThread.isStopped()) {
            mThread.start();
        }
    }

    private void startWifiScan() {
        Log.d(TAG, "startWifiScan");
        if (mWifiManager.isWifiEnabled() == false) {
            mWifiManager.setWifiEnabled(true);
        }

        if (mWifiManager.isWifiEnabled()) {
            mWifiManager.startScan();
        } else {
            Log.d(TAG, "wifi is off");
            Toast.makeText(this, "Please turn on the wifi", Toast.LENGTH_SHORT).show();
        }
    }
    private void getCellInformation() {
        Log.d(TAG, "getCellInformation");
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            List<CellInfo> cells = tm.getAllCellInfo();
            if (cells != null) {
                for (CellInfo info : cells) {
                    if (info.isRegistered() == false) continue;

                    Log.d(TAG, "current call is: " + info.toString());
                    if (info instanceof CellInfoLte) {
                        CellIdentityLte lte = ((CellInfoLte) info).getCellIdentity();
                        CellSignalStrengthLte signal = ((CellInfoLte) info).getCellSignalStrength();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                Log.d(TAG, "TimeStamp: " + info.getTimestampMillis());
                            }
                            Log.d(TAG, "LTE CellID: " + lte.getCi() + ", Bandwidth: " + lte.getBandwidth()
                                    + ", TAC: " + lte.getTac() + ", PLMN: " + lte.getMccString() + lte.getMncString());
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                Log.d(TAG, "CQI: " + signal.getCqi() + ", RSSI: " + signal.getRssi() + ", RSRP: " + signal.getRsrp()
                                        + ", RSRQ: " + signal.getRsrq() + ", RSSNR: " + signal.getRssnr());
                            }
                        }
                    }
                }
            }
        }
    }

    class LocationExecutor implements Executor {
        private Executor executor;

        public LocationExecutor() {
            this(Executors.newSingleThreadExecutor());
        }

        private LocationExecutor(Executor excutor) {
            this.executor = excutor;
        }

        @Override
        public void execute(Runnable runnable) {
            executor.execute(runnable);
        }
    }

    private void getCurrentLocation() {
        Log.d(TAG, "getCurrentLocation");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        }
        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        LocationExecutor executor = new LocationExecutor();
        if (fusedLocationProviderClient != null) {
            fusedLocationProviderClient
                    .getCurrentLocation(LocationRequest.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                    .addOnSuccessListener(executor, location -> {
                        if (location != null) {
                            Log.d(TAG, "long: " + location.getLongitude() + ", lati: " + location.getLatitude());
                        }
                    });
        }
    }

    private void getCurrentWifiInfo() {
        Log.d(TAG, "getCurrentWifiInfo");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
//        int ipAddress = wifiInfo.getNetworkId();
//        String macAddress = wifiInfo.getMacAddress();
//        Log.d(TAG, "Wifi ip address: " + android.text.format.Formatter.formatIpAddress(ipAddress));
//        Log.d(TAG, "Wifi mac address: " + macAddress);
        getLocalIpAddress();
        ScanResult scanResult;
        List<ScanResult> apList = mWifiManager.getScanResults();
        for (int i = 0; i < apList.size() && i < 5; i++) {
            scanResult = (ScanResult) apList.get(i);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                Log.d(TAG, "Wifi ap[" + i + "]: " + scanResult.getWifiSsid());
//                Log.d(TAG, "Wifi ap[" + i + "]: " + scanResult.getWifiStandard());
            }
        }
    }

    public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        String ip = Formatter.formatIpAddress(inetAddress.hashCode());
                        Log.i(TAG, "Local IP="+ ip);
                        return ip;
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(TAG, ex.toString());
        }
        return null;
    }
}
