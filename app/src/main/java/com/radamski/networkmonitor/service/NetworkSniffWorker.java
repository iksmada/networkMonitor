package com.radamski.networkmonitor.service;

import static com.radamski.networkmonitor.AbstractDiscoveryTask.CONNECTED_TRACKED_DEVICES;
import static com.radamski.networkmonitor.AbstractDiscoveryTask.COUNT_DOWN_DEVICES;
import static com.radamski.networkmonitor.AbstractDiscoveryTask.TRACKED_DEVICES;
import static com.radamski.networkmonitor.Utils.Prefs.DEFAULT_IP_END;
import static com.radamski.networkmonitor.Utils.Prefs.DEFAULT_IP_START;
import static com.radamski.networkmonitor.Utils.Prefs.DEFAULT_TRIGGER_COUNTDOWN;
import static com.radamski.networkmonitor.Utils.Prefs.KEY_IP_END;
import static com.radamski.networkmonitor.Utils.Prefs.KEY_IP_START;
import static com.radamski.networkmonitor.Utils.Prefs.KEY_TRIGGER_COUNTDOWN;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.radamski.networkmonitor.ActivityDeviceState;
import com.radamski.networkmonitor.BasicStateRunner;
import com.radamski.networkmonitor.DefaultDiscoveryCallable;
import com.radamski.networkmonitor.Network.HostBean;
import com.radamski.networkmonitor.Network.NetInfo;
import com.radamski.networkmonitor.Utils.TaskInterface;
import com.radamski.networkmonitor.Utils.TinyDB;
import com.radamski.networkmonitor.receiver.NetwortkStatusReceiver;
import com.radamski.networkmonitor.state.DeviceUpdate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class NetworkSniffWorker extends Worker implements TaskInterface {
    private static final String TAG = "NetworkSniffWorker";
    private final Context context;
    private final TinyDB tinydb;
    private List<HostBean> trackedHosts;
    public Set<HostBean> foundHosts = new HashSet<>();
    public Map<HostBean, Boolean> pastState = new HashMap<>();
    public Map<HostBean, Integer> countDown = new HashMap<>();
    private final SharedPreferences prefs;
    private DefaultDiscoveryCallable mDiscoveryTask;

    public NetworkSniffWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
        tinydb = new TinyDB(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "doWork called for: " + this.getId());
        // TODO Check what to do if it is not in the wifi, result failure?
        NetwortkStatusReceiver status = new NetwortkStatusReceiver(null);
        status.onReceive(context, new Intent());


        NetInfo net = new NetInfo(context);
        long network_ip = NetInfo.getUnsignedLongFromIp(net.ip);
        long network_start = NetInfo.getUnsignedLongFromIp(prefs.getString(KEY_IP_START, DEFAULT_IP_START));
        long network_end = NetInfo.getUnsignedLongFromIp(prefs.getString(KEY_IP_END, DEFAULT_IP_END));

        // TODO add a receiver if this change
        trackedHosts = tinydb.getListObject(TRACKED_DEVICES, HostBean.class);

        if(trackedHosts.isEmpty() || isStopped()) {
            return Result.failure();
        }

        // lets assume everybody is connected for a test
        // TODO save past state to the tinyDB
        ArrayList<HostBean> connectedHosts = tinydb.getListObject(CONNECTED_TRACKED_DEVICES, HostBean.class);
        trackedHosts.forEach(host -> pastState.put(host, connectedHosts.contains(host)));
        countDown.putAll(tinydb.getMapObject(COUNT_DOWN_DEVICES, HostBean.class));
        countDown.forEach((host, count) -> Log.i(TAG, String.format("Recovered %s with isConnected=false count=%d", host.hostname, count)));

        // start task
        Log.i(TAG, "Starting DefaultDiscoveryCallable");
        // TODO Search only the tracked devices
        mDiscoveryTask = new DefaultDiscoveryCallable(this, context, true);
        mDiscoveryTask.setNetwork(network_ip, network_start, network_end);
        Result result = mDiscoveryTask.call();
        foundHosts.clear();

        return result;
    }

    @Override
    public void onStopped() {
        Log.d(TAG, "onStopped called for: " + this.getId());
        if(mDiscoveryTask != null)
        {
            ExecutorService lPool = mDiscoveryTask.getExecutor();
            if(lPool != null) {
                lPool.shutdownNow();
            }
        }
    }

    private Result updateResult(Result result, Result lastResult) {
        if (result instanceof Result.Failure) {
            return result;
        }
        else {
            if(result instanceof Result.Retry) {
                if(lastResult instanceof Result.Failure) {
                    return lastResult;
                }
                else {
                    return result;
                }
            }
            else {
                return lastResult;
            }
        }
    }

    @Override
    public void onTaskCompleted(boolean wasCancelled) {
        Log.d(TAG, String.format("onTaskCompleted called with wasCancelled=%s and isStopped()=%s", wasCancelled, isStopped()));
        if(!wasCancelled && !isStopped()) {
            trackedHosts = tinydb.getListObject(TRACKED_DEVICES, HostBean.class);

            for(HostBean host: trackedHosts)
            {
                if(!foundHosts.contains(host))
                    Log.i(TAG, String.format("Tracked host not found: %s", host.hostname));

                checkHostState(host, foundHosts.contains(host), false);
            }
        }
    }

    private void checkHostState(HostBean host, Boolean isConnected, boolean premature) {
        Boolean wasConnected = pastState.get(host);
        if (wasConnected == null) {
            updateState(host, isConnected);
        } else {
            if (isConnected) {
                // this is never false positive, we can trust on this detection
                if (!wasConnected) {
                    sendToTasker(host, isConnected, premature);
                }
                if(countDown.containsKey(host)) {
                    countDown.remove(host);
                    Log.i(TAG, String.format("False positive of %s - isConnected=%s", host.hostname, isConnected));
                }
            } else {
                // assuming premature is always false
                // Disconnection can be a false positive, we can't trust on this detection
                countDown.computeIfAbsent(host , hostBean -> {
                    // start count down (number of detection) before sending that a device is disconnected
                    if (wasConnected) {
                        return  0;
                    }
                    else {
                        // wont add the host entry to the countDown map
                        return null;
                    }
                });
                countDown.computeIfPresent(host, (hostBean, count) -> {
                    count += 1;
                    Log.i(TAG, String.format("Monitoring %s with isConnected=%s count=%d", host.hostname, isConnected, count));
                    // TODO add this options to Prefs activity
                    int countToTrigger = prefs.getInt(KEY_TRIGGER_COUNTDOWN, DEFAULT_TRIGGER_COUNTDOWN);
                    // check if we need to trigger someone finally
                    if (countToTrigger == count) {
                        sendToTasker(host, isConnected, premature);
                        countDown.remove(host);
                    }
                    return count;
                });

            }
            tinydb.putMapObject(COUNT_DOWN_DEVICES, countDown);
        }
    }

    private void sendToTasker(HostBean host, Boolean isConnected, boolean premature) {
        updateState(host, isConnected);
        BasicStateRunner.Companion.requestQuery(context, ActivityDeviceState.class,
                new DeviceUpdate(host, isConnected, premature));
    }

    private void updateState(HostBean host, Boolean isConnected) {
        pastState.put(host, isConnected);
        //                                        filter for isConnected == true
        List<HostBean> connected = pastState.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toList());
        tinydb.putListObject(CONNECTED_TRACKED_DEVICES, connected);
    }

    @Override
    public void setTaskProgress(int i) {

    }

    @Override
    public void onProgressUpdate(HostBean host) {
        if(trackedHosts.contains(host))
        {
            Log.i(TAG, String.format("Found prematurely a tracked host: %s", host.hostname));
            checkHostState(host, true, true);
        }
        foundHosts.add(host);
        if(foundHosts.containsAll(trackedHosts)) {
            mDiscoveryTask.getExecutor().shutdownNow();
        }
    }
}
