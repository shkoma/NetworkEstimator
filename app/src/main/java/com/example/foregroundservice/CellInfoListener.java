package com.example.foregroundservice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.CellInfo;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.collect.Sets;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// https://android.googlesource.com/platform/packages/apps/Settings/+/master/src/com/android/settings/network/SubscriptionsPreferenceController.java
// https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/master/src/com/android/settings/network/telephony/SignalStrengthListener.java
// 참조
public class CellInfoListener {
    private static final String TAG = CellInfoListener.class.getSimpleName();
    private TelephonyManager mBaseTelephonyManager;
    private SubscriptionManager mSubscriptionManager;
    private Callback mCallback;
    private Context mContext;

    private int mPhoneId;

    Map<Integer, CellInfoTelephonyCallback> mTelephonyCallbacks;

    public interface Callback {
        void onCellInfoChanged(@NonNull List<CellInfo> cellInfo);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public class CellInfoTelephonyCallback extends TelephonyCallback
            implements TelephonyCallback.CellInfoListener {
        @Override
        public void onCellInfoChanged(@NonNull List<CellInfo> cellInfo) {
            mCallback.onCellInfoChanged(cellInfo);
        }
    }

    public CellInfoListener(Context context, int phoneId, Callback callback) {
        mBaseTelephonyManager = context.getSystemService(TelephonyManager.class);
        mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
        mPhoneId = phoneId;
        mCallback = callback;
        mContext = context;
        mTelephonyCallbacks = new TreeMap<>();
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void resume() {
        for (int subId : mTelephonyCallbacks.keySet()) {
            startListening(subId);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void pause() {
        for (int subId : mTelephonyCallbacks.keySet()) {
            stopListening(subId);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void stopListening(int subId) {
        TelephonyManager mgr = mBaseTelephonyManager.createForSubscriptionId(subId);
        mgr.unregisterTelephonyCallback(mTelephonyCallbacks.get(subId));
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void startListening(int subId) {
        TelephonyManager mgr = mBaseTelephonyManager.createForSubscriptionId(subId);
        mgr.registerTelephonyCallback(mContext.getMainExecutor(), mTelephonyCallbacks.get(subId));
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void updateSubscriptionIds() {
        Log.d(TAG, "updateSubscriptionIds mPhoneId: " + mPhoneId);
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No permission with READ_PHONE_STATE");
            return;
        }

        SubscriptionInfo info = mSubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(mPhoneId);
        if (info == null) {
            Log.e(TAG, "PhoneId: " + mPhoneId + " is null");
            return;
        }
        Set<Integer> ids = new ArraySet<>();
        ids.add(info.getSubscriptionId());

        Set<Integer> currentIds = new ArraySet<>(mTelephonyCallbacks.keySet());
        for (int idToRemove : Sets.difference(currentIds, ids)) {
            stopListening(idToRemove);
            mTelephonyCallbacks.remove(idToRemove);
        }

        for (int idToAdd : Sets.difference(ids, currentIds)) {
            CellInfoTelephonyCallback telephonyCallback =
                    new CellInfoTelephonyCallback();
            mTelephonyCallbacks.put(idToAdd, telephonyCallback);
            startListening(idToAdd);
        }
    }
}
