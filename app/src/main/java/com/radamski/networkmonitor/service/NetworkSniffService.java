package com.radamski.networkmonitor.service;

import static com.radamski.networkmonitor.AbstractDiscoveryTask.CONNECTED_TRACKED_DEVICES;
import static com.radamski.networkmonitor.AbstractDiscoveryTask.TRACKED_DEVICES;
import static com.radamski.networkmonitor.Utils.Prefs.DEFAULT_IP_END;
import static com.radamski.networkmonitor.Utils.Prefs.DEFAULT_IP_START;
import static com.radamski.networkmonitor.Utils.Prefs.DEFAULT_TRIGGER_COUNTDOWN;
import static com.radamski.networkmonitor.Utils.Prefs.KEY_TRIGGER_COUNTDOWN;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.radamski.networkmonitor.ActivityDeviceState;
import com.radamski.networkmonitor.ActivityDiscovery;
import com.radamski.networkmonitor.BasicStateRunner;
import com.radamski.networkmonitor.DefaultDiscoveryTask;
import com.radamski.networkmonitor.Network.HostBean;
import com.radamski.networkmonitor.Network.NetInfo;
import com.radamski.networkmonitor.R;
import com.radamski.networkmonitor.Utils.TaskInterface;
import com.radamski.networkmonitor.Utils.TinyDB;
import com.radamski.networkmonitor.receiver.NetwortkStatusReceiver;
import com.radamski.networkmonitor.receiver.RestartServiceReceiver;
import com.radamski.networkmonitor.state.DeviceUpdate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NetworkSniffService extends Service implements TaskInterface {
    private final String TAG = "NetworkSniffService";
    public static boolean isServiceRunning;
    public static boolean breakLoop;
    private final String CHANNEL_ID = "NOTIFICATION_CHANNEL";
    private DefaultDiscoveryTask mDiscoveryTask;
    private long network_ip;
    private long network_start;
    private long network_end;
    private TinyDB tinydb;
    private List<HostBean> trackedHosts;
    public static List<HostBean> foundHosts = new ArrayList<>();
    public static Map<HostBean, Boolean> pastState = new HashMap<>();
    public static Map<HostBean, Integer> countDown = new HashMap<>();
    private SharedPreferences prefs;

    public NetworkSniffService() {
        Log.i(TAG, "constructor called");
        isServiceRunning = false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate called");
        createNotificationChannel();
        isServiceRunning = true;
        tinydb = new TinyDB(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO bind to the activity in order to avoid stopping the service
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");

        // TODO register it and receive network change for updates
        NetwortkStatusReceiver status = new NetwortkStatusReceiver(null);
        status.onReceive(this, new Intent());


        NetInfo net = new NetInfo(this);
        network_ip = NetInfo.getUnsignedLongFromIp(net.ip);
        network_start = NetInfo.getUnsignedLongFromIp(DEFAULT_IP_START);
        network_end = NetInfo.getUnsignedLongFromIp(DEFAULT_IP_END);

        // TODO add a receiver if this change
        trackedHosts = tinydb.getListObject(TRACKED_DEVICES, HostBean.class);

        if(trackedHosts.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // start task
        initTask();

        // setting notification to avoid exception
        Intent notificationIntent = new Intent(this, ActivityDiscovery.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Service is Running")
                .setContentText("Listening for Screen Off/On events")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentIntent(pendingIntent)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .build();

        startForeground(1, notification);
        return START_STICKY;
    }


    private void initTask() {
        // start again
        Log.i(TAG, "Starting DefaultDiscoveryTask");
        mDiscoveryTask = new DefaultDiscoveryTask(this, this, true);
        mDiscoveryTask.setNetwork(network_ip, network_start, network_end);
        mDiscoveryTask.execute();
        foundHosts.clear();
    }
    @Override
    public void onTaskCompleted(boolean wasCancelled) {
        if(!wasCancelled) {
            trackedHosts = tinydb.getListObject(TRACKED_DEVICES, HostBean.class);

            for(HostBean host: trackedHosts)
            {
                if(foundHosts.contains(host))
                    Log.i(TAG, String.format("Found a tracked host: %s", host.hostname));
                else
                    Log.i(TAG, String.format("Tracked host not found: %s", host.hostname));
                checkHostState(host, foundHosts.contains(host), false);
            }

            // sleep

            initTask();
        }
    }

    private void checkHostState(HostBean host, Boolean isConnected, boolean premature) {
        Boolean wasConnected = pastState.get(host);
        if (wasConnected == null) {
            pastState.put(host, isConnected);
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
                    int countToTrigger = Integer.parseInt(prefs.getString(KEY_TRIGGER_COUNTDOWN, DEFAULT_TRIGGER_COUNTDOWN));
                    // check if we need to trigger someone finally
                    if (countToTrigger == count) {
                        sendToTasker(host, isConnected, premature);
                        countDown.remove(host);
                    }
                    return count;
                });
            }
        }
    }

    private void sendToTasker(HostBean host, Boolean isConnected, boolean premature) {
        pastState.put(host, isConnected);
        //                                        filter for isConnected == true
        List<HostBean> connected = pastState.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toList());
        tinydb.putListObject(CONNECTED_TRACKED_DEVICES, connected);
        BasicStateRunner.Companion.requestQuery(NetworkSniffService.this, ActivityDeviceState.class,
                new DeviceUpdate(host, isConnected, premature));
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
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String appName = getString(R.string.app_name);
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    appName,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    //method from Service class
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.i(TAG, "onTaskRemoved called");
        // TODO restart the service?
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy called");
        if(mDiscoveryTask != null) {
            mDiscoveryTask.cancel(true);
        }
        isServiceRunning = false;
        stopForeground(STOP_FOREGROUND_REMOVE);

        trackedHosts = tinydb.getListObject(TRACKED_DEVICES, HostBean.class);

        if(!trackedHosts.isEmpty() && !breakLoop) {
            // call RestartServiceReceiver which will restart this service via a worker
            Intent broadcastIntent = new Intent(this, RestartServiceReceiver.class);
            sendBroadcast(broadcastIntent);
        }
        super.onDestroy();
    }
}