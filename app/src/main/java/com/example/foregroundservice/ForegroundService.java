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
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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

    private TelephonyManager mTelephonyManager;
    private NotificationManager mNotificationManager;
    private WifiManager mWifiManager;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.S)
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Receive: " + intent.getAction());
            if (ACTION_SERVICE_TRI_TO_START.equals(intent.getAction())) {
                startThread();
                startWifiScan();
                mHandler.sendEmptyMessageDelayed(MESSAGE_GET_INFO, DELAYED_TIME * 2);
            } else if (ACTION_SERVICE_TRI_TO_STOP.equals(intent.getAction())) {
                stopThread();
                readDataFromFile();
            } else if (ACTION_TERMINATE_SERVICE.equals(intent.getAction())) {
                stopThread();
                stopForeground(true);
            } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
            }
        }
    };

    Handler mHandler;
    HandlerThread mHandlerThread;
    ControlSubThread mThread;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private SubscriptionManager mSubsctiptionManager;

    class TimeseriesData {
        public static final String CID = "cid";
        public static final String BANDWIDTH = "bandwidth";
        public static final String TAC = "tac";
        public static final String MCC = "mcc";
        public static final String MNC = "mnc";

        public static final String CQI = "cqi";
        public static final String RSSI = "rssi";
        public static final String RSRP = "rsrp";
        public static final String RSRQ = "rsrq";
        public static final String RSSNR = "rssnr";

        public static final String LONGIUDE = "longitude";
        public static final String LATITUDE = "latitude";
        public static final String BSSID = "bssid";

        public long timestamp;
        public int cid;
        public int bandwidth;
        //     private final int mEarfcn;
        //    // cell bandwidth, in kHz
        public int tac;
        public String mcc;
        public String mnc;

        public int cqi;
        public int rssi;
        public int rsrp;
        public int rsrq;
        public int rssnr;

        public double longitude;
        public double latitude;

        public String bssid;
        public String localIp;

        @Override
        public String toString() {
            String str = cid + "," + bandwidth + "," + tac + "," + mcc + "," + mnc + "," +
                    cqi + "," + rssi + "," + rsrp + "," + rsrq + "," + rssnr;
            return str;
        }
    }

    public static String getCurrentTime() {
        long now = System.currentTimeMillis();
        Date date = new Date(now);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String nowTime = sdf.format(date);
        return nowTime;
    }

    List<SignalStrengthListener> mSignalStrengthListener = new ArrayList<>();
    List<CellInfoListener> mCellInfoListener = new ArrayList<>();

    private boolean mListen;
    private boolean[] mDone;
    private TimeseriesData[] mData;
    private List<String> mDataList;

    private int mCount;

    private int MAX_SIZE = 10;

    class CellInfoCallback implements CellInfoListener.Callback {
        int mPhoneId;
        CellInfoCallback(int phoneId) {
            mPhoneId = phoneId;
        }
        @RequiresApi(api = Build.VERSION_CODES.Q)
        @Override
        public void onCellInfoChanged(@NonNull List<CellInfo> cellInfo) {
            if (mDone[mPhoneId] == false) {
                mDone[mPhoneId] = true;
            } else {
                return;
            }

            Log.d(TAG, "received Callback[" + mPhoneId + "]: " + cellInfo.toString());
            mData[mPhoneId] = new TimeseriesData();
            for (CellInfo info : cellInfo) {
                if (info.isRegistered()) {
                    if (info instanceof CellInfoNr) {
                        CellIdentityNr nr = (CellIdentityNr) ((CellInfoNr) info).getCellIdentity();
                        CellSignalStrengthNr signal = (CellSignalStrengthNr) ((CellInfoNr) info).getCellSignalStrength();
                        signal.getAsuLevel();
                    } else if (info instanceof CellInfoLte) {
                        CellIdentityLte lte = ((CellInfoLte) info).getCellIdentity();
                        CellSignalStrengthLte signal = ((CellInfoLte) info).getCellSignalStrength();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                mData[mPhoneId].timestamp = info.getTimestampMillis();
                            }
                            mData[mPhoneId].cid = lte.getCi();
                            mData[mPhoneId].bandwidth = lte.getBandwidth();
                            mData[mPhoneId].tac = lte.getTac();
                            mData[mPhoneId].mcc = lte.getMccString();
                            mData[mPhoneId].mnc = lte.getMncString();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                mData[mPhoneId].cqi = signal.getCqi();
                                mData[mPhoneId].rssi = signal.getRssi();
                                mData[mPhoneId].rsrp = signal.getRsrp();
                                mData[mPhoneId].rsrq = signal.getRsrq();
                                mData[mPhoneId].rssnr = signal.getRssnr();
                            }
                        }
                    }
                }
            }

            if (mPhoneId == 0) {
                getCurrentWifiInfo(mData[mPhoneId]);
                getCurrentLocation(mData[mPhoneId]);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public void onCreate() {
        super.onCreate();
        mTelephonyManager = getSystemService(TelephonyManager.class);
        mNotificationManager = getSystemService(NotificationManager.class);
        mSubsctiptionManager = getSystemService(SubscriptionManager.class);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mHandlerThread = new HandlerThread("ForegroundService Handler");
        mHandlerThread.start();

        mListen = true;
        mDone = new boolean[mTelephonyManager.getActiveModemCount()];
        mData = new TimeseriesData[mTelephonyManager.getActiveModemCount()];
        mDataList = new ArrayList<>();
        mCount = 0;

        for (int idx = 0; idx < mTelephonyManager.getActiveModemCount(); idx++) {
            mCellInfoListener.add(new CellInfoListener(this, idx, new CellInfoCallback(idx)));
            mDone[idx] = false;
        }

        for (CellInfoListener listener : mCellInfoListener) {
            listener.updateSubscriptionIds();
        }

        mHandler = new Handler(mHandlerThread.getLooper()) {
            @RequiresApi(api = Build.VERSION_CODES.S)
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_COUNT_NOTIFICATION:
                        NotificationCompat.Builder notificationBuilder = getNotificationBuilder(msg.arg1);
                        startForeground(NOTIFICATION_ID, notificationBuilder.build());
                        break;
                    case MESSAGE_GET_INFO:
                        if (mListen) {
                            for (CellInfoListener listener : mCellInfoListener) {
                                listener.pause();
                                //mDone[listener.getPhoneId()] = false;
                            }
                            mListen = false;
                        } else {
                            for (CellInfoListener listener : mCellInfoListener) {
                                listener.resume();
                            }
                            mListen = true;
                        }

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

    @RequiresApi(api = Build.VERSION_CODES.S)
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

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void stopThread() {
        for (CellInfoListener listener : mCellInfoListener) {
            listener.pause();
        }

        for (SignalStrengthListener listener : mSignalStrengthListener) {
            listener.pause();
        }

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

    private void arrangeData() {
        for (boolean check : mDone) {
            if (!check) {
                Log.e(TAG, "not received a data yet");
                return;
            }
        }

        String str = getCurrentTime() + ",";
        str += mData[0].longitude + "," + mData[0].latitude + "," + mData[0].bssid + " ";
        str += mData[0].toString();
        mDone[0] = false;

        if (mData.length > 1) {
            str += " " + mData[1].toString();
            mDone[1] = false;
        }
        mDataList.add(str);

        if (mCount++ > MAX_SIZE) {
            mCount = 0;
            wrtieDataToFile();
            mDataList.clear();
        }
    }

    private void getCurrentLocation(TimeseriesData data) {
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
                            data.longitude = location.getLongitude();
                            data.latitude = location.getLatitude();
                            Log.d(TAG, data.toString());
                            arrangeData();
                            //wrtieDataToFile(data);
                        }
                    });
        }
    }

    private void getCurrentWifiInfo(TimeseriesData data) {
        Log.d(TAG, "getCurrentWifiInfo");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        String bssid = wifiInfo.getBSSID();
        Log.d(TAG, "Wifi bssid: " + bssid);
        data.bssid = bssid;
        data.localIp = getLocalIpAddress();
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

    public void wrtieDataToFile() {
        String externalDir = Environment.getDataDirectory().getAbsolutePath() + "/data/com.example.foregroundservice";
        String fileName = "Timeseries-data.txt";
        File directory = new File(externalDir);
        if (!directory.exists()) {
            Log.d(TAG, "Create directory: " + directory);
           if(!directory.mkdirs()) {
               Log.e(TAG, "failed to make dirs: " + directory);
           }
        }
        BufferedWriter bufferedWriter = null;
        try {
            File file = new File(externalDir + "/" + fileName);
            Log.d(TAG, "file: " + file);
            FileWriter fileWriter = new FileWriter(file, true);
            bufferedWriter = new BufferedWriter(fileWriter);
            for (String data: mDataList) {
                bufferedWriter.append(data);
                bufferedWriter.newLine();
            }
            bufferedWriter.close();
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e);
            try {
                if (bufferedWriter != null) bufferedWriter.close();
            } catch (IOException ex) {
                Log.e(TAG, "Exception: " + ex);
            }
        }
    }

    public void readDataFromFile() {
        String line = null;
        String externalDir = Environment.getDataDirectory().getAbsolutePath() + "/data/com.example.foregroundservice";
        String filename = "Timeseries-data.txt";
        BufferedReader buf = null;
        try {
            Log.d(TAG, "readData from file: " + externalDir + "/" + filename);

            buf = new BufferedReader(new FileReader(externalDir + "/" + filename));
            while ((line = buf.readLine()) != null) {
                Log.d(TAG, line);
            }
            buf.close();
        } catch (Exception e) {
            try {
                if (buf != null) buf.close();
            } catch (IOException ex) {
                Log.e(TAG, "Exception: " + ex);
            }
            Log.e(TAG, "Exception: " + e);
        }
    }
}
