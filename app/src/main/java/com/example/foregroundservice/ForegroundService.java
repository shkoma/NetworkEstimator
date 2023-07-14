package com.example.foregroundservice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

// https://github.com/kazimdsaidul/Foreground-Service-Android-Example/blob/master/app/src/main/java/com/kazi/foregroundserviceandroidexample/ForegroundService.java

public class ForegroundService extends Service {
    public static final String TAG = ForegroundService.class.getSimpleName();
    public static final String CHANNEL_ID = ForegroundService.class.getSimpleName() + "_Channel";

    public static final String ACTION_SERVICE_TRI_TO_START = "android.intent.action.SERVICE_TRI_TO_START";
    public static final String ACTION_SERVICE_TRI_TO_STOP = "android.intent.action.SERVICE_TRI_TO_STOP";
    public static final String ACTION_TERMINATE_SERVICE = "android.intent.action.TERMINATE_SERVICE";

    public static final int MESSAGE_COUNT_NOTIFICATION = 0;

    private static final int DELAYED_TIME = 1000;
    private static final int NOTIFICATION_ID = 10;

    private NotificationManager mNotificationManager;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Receive: " + intent.getAction());
            if (ACTION_SERVICE_TRI_TO_START.equals(intent.getAction())) {
                startThread();
            }
            else if (ACTION_SERVICE_TRI_TO_STOP.equals(intent.getAction())) {
                stopThread();
            } else if (ACTION_TERMINATE_SERVICE.equals(intent.getAction())) {
                stopThread();
                stopForeground(true);
//                stopSelf();
            }
        }
    };

    Handler mHandler;
    HandlerThread mHandlerThread;
    ControlSubThread mThread;

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = getSystemService(NotificationManager.class);
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
                }
            }
        };
        mThread = new ControlSubThread(mHandler, DELAYED_TIME);

        IntentFilter filter = new IntentFilter(ACTION_SERVICE_TRI_TO_STOP);
        filter.addAction(ACTION_SERVICE_TRI_TO_START);
        filter.addAction(ACTION_TERMINATE_SERVICE);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");

        startThread();

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
        mHandler.removeMessages(MESSAGE_COUNT_NOTIFICATION);
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

    private void stopThread() {
        if (mThread != null && mThread.isRunning()) {
            mThread.interrupt();
        }
        mThread = null;
    }

    private void startThread() {
        if (mThread == null) {
            mThread = new ControlSubThread(mHandler, DELAYED_TIME);
        }

        if (mThread.isStopped()) {
            mThread.start();
        }
    }
}
