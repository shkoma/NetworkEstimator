package com.example.foregroundservice;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

public class ControlSubThread implements Runnable {
    public static final String TAG = ControlSubThread.class.getSimpleName();
    private Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private int interval = 100;
    private int mCount = 0;

    private Handler mHandler;

    public ControlSubThread(Handler handler, int sleepInterval) {
        this.interval = sleepInterval;
        this.mHandler = handler;
    }

    public void start() {
        worker = new Thread(this);
        worker.start();
        Log.d(TAG, "New Thread starts: " + worker.getId());
    }

    public void interrupt() {
        Log.d(TAG, "Thread is interrupted: " + worker.getId());
        running.set(false);
        worker.interrupt();
    }

    boolean isRunning() {
        return running.get();
    }

    boolean isStopped() {
        return stopped.get();
    }


    @Override
    public void run() {
        running.set(true);
        stopped.set(false);
        while (running.get()) {
            try {
                Thread.sleep(interval);
                mCount++;
                Message msg = Message.obtain(mHandler, ForegroundService.MESSAGE_COUNT_NOTIFICATION);
                msg.arg1 = mCount;
                mHandler.sendMessage(msg);
                Log.d(TAG, "Count: " + mCount);
            } catch (InterruptedException e){
                Thread.currentThread().interrupt();
                System.out.println(
                        "Thread was interrupted, Failed to complete operation");
            }
            // do something
        }
        mCount = 0;
        stopped.set(true);
        Log.d(TAG, "Worker is done: " + worker.getId());
    }
}
