package com.radamski.networkmonitor.service;

import static com.radamski.networkmonitor.AbstractDiscoveryTask.TRACKED_DEVICES;
import static com.radamski.networkmonitor.Utils.Prefs.DEFAULT_IP_END;
import static com.radamski.networkmonitor.Utils.Prefs.DEFAULT_IP_START;
import static com.radamski.networkmonitor.Utils.Prefs.KEY_IP_END;
import static com.radamski.networkmonitor.Utils.Prefs.KEY_IP_START;

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
import com.radamski.networkmonitor.receiver.RestartReceiver;
import com.radamski.networkmonitor.state.DeviceUpdate;

import java.util.ArrayList;
import java.util.List;

public class NetworkSniffService extends Service implements TaskInterface {
    private final String TAG = "NetworkSniffService";
    public static boolean isServiceRunning;
    public static boolean breakLoop;
    private final String CHANNEL_ID = "NOTIFICATION_CHANNEL";
    private DefaultDiscoveryTask mDiscoveryTask;
    private long network_ip;
    private long network_start;
    private long network_end;
    private TinyDB<HostBean> tinydb;
    private List<HostBean> trackedHosts;
    private List<HostBean> foundHosts = new ArrayList<>();

    public NetworkSniffService() {
        Log.d(TAG, "constructor called");
        isServiceRunning = false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate called");
        createNotificationChannel();
        isServiceRunning = true;
        tinydb = new TinyDB<>(this);
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


        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        NetInfo net = new NetInfo(this);
        network_ip = NetInfo.getUnsignedLongFromIp(net.ip);
        network_start = NetInfo.getUnsignedLongFromIp(prefs.getString(KEY_IP_START, DEFAULT_IP_START));
        network_end = NetInfo.getUnsignedLongFromIp(prefs.getString(KEY_IP_END, DEFAULT_IP_END));

        // TODO add a receiver if this change
        trackedHosts = tinydb.getListObject(TRACKED_DEVICES, HostBean.class);

        if(trackedHosts.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        trackedHosts = tinydb.getListObject(TRACKED_DEVICES, HostBean.class);
        // start task
        initTask();

        // setting notification to avoid exception
        Intent notificationIntent = new Intent(this, ActivityDiscovery.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
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
        mDiscoveryTask = new DefaultDiscoveryTask(this, this);
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
                if(!foundHosts.contains(host)) {
                    Log.i(TAG, String.format("Tracked host not found: %s", host.hostname));
                    sendToTasker(host, false);
                }
            }

            // sleep

            initTask();
        }
    }

    private void sendToTasker(HostBean host, boolean isConnected) {
        // TODO set time out before sending that a device is off
        // Only send if there is a change, (was disconnect and became connected or vice-versa)
        // dont send if there was no change since last run (it can break ANY`s logic)
        BasicStateRunner.Companion.requestQuery(NetworkSniffService.this, ActivityDeviceState.class,
                new DeviceUpdate(host, isConnected));
    }

    @Override
    public void setTaskProgress(int i) {

    }

    @Override
    public void onProgressUpdate(HostBean host) {
        if(trackedHosts.contains(host))
        {
            Log.i(TAG, String.format("Found a tracked host: %s", host.hostname));
            sendToTasker(host, true);
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

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy called");
        if(mDiscoveryTask != null) {
            mDiscoveryTask.cancel(true);
        }
        isServiceRunning = false;
        stopForeground(true);

        trackedHosts = tinydb.getListObject(TRACKED_DEVICES, HostBean.class);

        if(!trackedHosts.isEmpty() && !breakLoop) {
            // call RestartReceiver which will restart this service via a worker
            Intent broadcastIntent = new Intent(this, RestartReceiver.class);
            sendBroadcast(broadcastIntent);
        }
        super.onDestroy();
    }
}