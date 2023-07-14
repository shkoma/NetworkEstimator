package com.example.foregroundservice;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// https://developer.android.com/guide/components/services?hl=ko#java
public class MainActivity extends AppCompatActivity {
    public static final String TAG = MainActivity.class.getSimpleName();

    private final AtomicBoolean running = new AtomicBoolean(true);

    Button btnStartService, btnStopService, btnTerminateService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStartService = findViewById(R.id.buttonStartService);
        btnStopService = findViewById(R.id.buttonStopService);
        btnTerminateService = findViewById(R.id.buttonTerminateService);

        btnStartService.setOnClickListener(view -> startService());
        btnStopService.setOnClickListener(view -> stopService());
        btnTerminateService.setOnClickListener(view -> terminateService());

        Intent serviceIntent = new Intent(this, ForegroundService.class);
        serviceIntent.putExtra("inputExtra", "Foregroud Service Example in Android");
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Finish MainActivity");
    }


    private void terminateService() {
        Intent serviceIntent = new Intent(ForegroundService.ACTION_TERMINATE_SERVICE);
        getApplicationContext().sendBroadcast(serviceIntent);

        running.set(false);
        finish();
    }

    private void startService() {
        if (!isServiceRunning()) {
            running.set(true);
            Intent serviceIntent = new Intent(ForegroundService.ACTION_SERVICE_TRI_TO_START);
            getApplicationContext().sendBroadcast(serviceIntent);
        }
    }

    private void stopService() {
        if (isServiceRunning()) {
            running.set(false);
            Intent serviceIntent = new Intent(ForegroundService.ACTION_SERVICE_TRI_TO_STOP);
            getApplicationContext().sendBroadcast(serviceIntent);
        }
    }

    public boolean isServiceRunning() {
        return running.get();
    }
}