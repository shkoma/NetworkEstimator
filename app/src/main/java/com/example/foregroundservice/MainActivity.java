package com.example.foregroundservice;

import androidx.annotation.NonNull;
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

    private final AtomicBoolean running = new AtomicBoolean(false);

    Button btnStartService, btnStopService, btnTerminateService;

    int PERMISSION_ALL = 1;
    String[] PERMISSIONS = {
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    };

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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Do not have the permission of ACCESS_FINE_LOCATION");
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (Build.VERSION.SDK_INT >= 23) {

            // requestPermission의 배열의 index가 아래 grantResults index와 매칭
            // 퍼미션이 승인되면
            if(grantResults.length > 0  && grantResults[0]== PackageManager.PERMISSION_GRANTED){
                Log.d(TAG,"Permission: "+permissions[0]+ "was "+grantResults[0]);

                // TODO : 퍼미션이 승인되는 경우에 대한 코드

            }
            // 퍼미션이 승인 거부되면
            else {
                Log.d(TAG,"Permission denied");

                // TODO : 퍼미션이 거부되는 경우에 대한 코드
            }
        }
    }
}