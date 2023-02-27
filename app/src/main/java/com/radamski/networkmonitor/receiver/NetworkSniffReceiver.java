package com.radamski.networkmonitor.receiver;

import static com.radamski.networkmonitor.ActivityDiscovery.UNIQUE_WORK_NAME;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.radamski.networkmonitor.service.NetworkSniffWorker;

import java.util.List;

public class NetworkSniffReceiver extends BroadcastReceiver {
    private final String TAG = "NetworkSniffReceiver";

    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive called");
        WorkManager workManager = WorkManager.getInstance(context);
        LiveData<List<WorkInfo>> currentRunning = workManager.getWorkInfosForUniqueWorkLiveData(UNIQUE_WORK_NAME);
        List<WorkInfo> listWork = currentRunning.getValue();
        if(listWork == null || listWork.stream().noneMatch(work -> work.getState() == WorkInfo.State.RUNNING)) {
            Log.i(TAG, "enqueue new NetworkSniffWorker");
            OneTimeWorkRequest onceRequest = new OneTimeWorkRequest.Builder(NetworkSniffWorker.class).build();
            workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, onceRequest);
        } else {
            Log.i(TAG, "Skipping NetworkSniffWorker because it is already running");
        }
    }
}
