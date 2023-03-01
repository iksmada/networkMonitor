package com.radamski.networkmonitor.receiver;

import static com.radamski.networkmonitor.ActivityDiscovery.UNIQUE_WORK_NAME;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.common.util.concurrent.ListenableFuture;
import com.radamski.networkmonitor.service.NetworkSniffWorker;

import java.util.List;

public class NetworkSniffReceiver extends BroadcastReceiver {
    private final String TAG = "NetworkSniffReceiver";

    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive called");
        WorkManager workManager = WorkManager.getInstance(context);
        ListenableFuture<List<WorkInfo>> currentRunning = workManager.getWorkInfosForUniqueWork(UNIQUE_WORK_NAME);
        if(!currentRunning.isDone()) {
            Log.i(TAG, "enqueue new NetworkSniffWorker");
            OneTimeWorkRequest onceRequest = new OneTimeWorkRequest.Builder(NetworkSniffWorker.class).build();
            workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, onceRequest);
        } else {
            Log.i(TAG, "Skipping NetworkSniffWorker because it is already running");
        }
    }
}
