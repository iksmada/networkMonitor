/*
 * Copyright (C) 2009-2010 Aubort Jean-Baptiste (Rorist)
 * Licensed under GNU's GPL 2, see README
 */

package com.radamski.networkmonitor;

import static com.radamski.networkmonitor.AbstractDiscoveryTask.TRACKED_DEVICES;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.radamski.networkmonitor.Network.HostBean;
import com.radamski.networkmonitor.Network.NetInfo;
import com.radamski.networkmonitor.Utils.TaskInterface;
import com.radamski.networkmonitor.Utils.Prefs;
import com.radamski.networkmonitor.Utils.TinyDB;
import com.radamski.networkmonitor.service.NetworkSniffService;
import com.radamski.networkmonitor.service.RestartWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final public class ActivityDiscovery extends ActivityNet implements TaskInterface {

    private final String TAG = "ActivityDiscovery";
    public static final String PKG = "com.radamski.networkmonitor";
    public final static long VIBRATE = (long) 250;
    public final static int SCAN_PORT_RESULT = 1;
    public static final int MENU_SCAN_SINGLE = 0;
    public static final int MENU_OPTIONS = 1;
    public static final int MENU_HELP = 2;
    private static final int MENU_EXPORT = 3;
    private static LayoutInflater mInflater;
    private int currentNetwork = 0;
    private long network_ip = 0;
    private long network_start = 0;
    private long network_end = 0;
    private List<HostBean> hosts = new ArrayList<>();;
    private HostsAdapter discoverAdapter;
    private HostsAdapter trackedAdapter;
    private Button btn_discover;
    private AbstractDiscoveryTask mDiscoveryTask = null;

    private TinyDB<HostBean> tinydb;
    private List<HostBean> trackedHosts = new ArrayList<>();
    private ListView discoverList;
    private ListView trackedList;

    // private SlidingDrawer mDrawer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.discovery);
        mInflater = LayoutInflater.from(ctxt);
        tinydb = new TinyDB<>(ctxt);

        // Discover
        btn_discover = (Button) findViewById(R.id.btn_discover);
        btn_discover.setOnClickListener(v -> startDiscovering());

        // Discover Hosts list
        discoverAdapter = new HostsAdapter(ctxt, hosts);
        discoverList = (ListView) findViewById(R.id.output);
        discoverList.setAdapter(discoverAdapter);
        discoverList.setItemsCanFocus(false);
        // Tracked Hosts list
        trackedHosts = tinydb.getListObject(TRACKED_DEVICES, HostBean.class);
        startService();
        trackedAdapter = new HostsAdapter(ctxt, false, trackedHosts);
        trackedList = (ListView) findViewById(R.id.tracked);
        trackedList.setAdapter(trackedAdapter);
        trackedList.setItemsCanFocus(true);
        trackedList.setEmptyView(findViewById(R.id.tracked_list_empty));
        TextView header = new TextView(ctxt);
        header.setText(R.string.tracked_title);
        trackedList.addHeaderView(header);

        startServiceViaWorker();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    public void setInfo(String info_ip_str, String info_in_str, String info_mo_str) {
        // Info
        ((TextView) findViewById(R.id.info_ip)).setText(info_ip_str);
        ((TextView) findViewById(R.id.info_in)).setText(info_in_str);
        ((TextView) findViewById(R.id.info_mo)).setText(info_mo_str);

        // Scan button state
        if (mDiscoveryTask != null) {
            setButton(btn_discover, R.drawable.cancel, false);
            btn_discover.setText(R.string.btn_discover_cancel);
            btn_discover.setOnClickListener(v -> cancelTasks());
        }

        if (currentNetwork != net.hashCode()) {
            Log.i(TAG, "Network info has changed");
            currentNetwork = net.hashCode();

            // Cancel running tasks
            cancelTasks();
        } else {
            return;
        }

        // Get ip information
        network_ip = NetInfo.getUnsignedLongFromIp(net.ip);
        if (prefs.getBoolean(Prefs.KEY_IP_CUSTOM, Prefs.DEFAULT_IP_CUSTOM)) {
            // Custom IP
            network_start = NetInfo.getUnsignedLongFromIp(prefs.getString(Prefs.KEY_IP_START,
                    Prefs.DEFAULT_IP_START));
            network_end = NetInfo.getUnsignedLongFromIp(prefs.getString(Prefs.KEY_IP_END,
                    Prefs.DEFAULT_IP_END));
        } else {
            // Custom CIDR
            if (prefs.getBoolean(Prefs.KEY_CIDR_CUSTOM, Prefs.DEFAULT_CIDR_CUSTOM)) {
                net.cidr = Integer.parseInt(prefs.getString(Prefs.KEY_CIDR, Prefs.DEFAULT_CIDR));
            }
            // Detected IP
            int shift = (32 - net.cidr);
            if (net.cidr < 31) {
                network_start = (network_ip >> shift << shift) + 1;
                network_end = (network_start | ((1 << shift) - 1)) - 1;
            } else {
                network_start = (network_ip >> shift << shift);
                network_end = (network_start | ((1 << shift) - 1));
            }
            // Reset ip start-end (is it really convenient ?)
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString(Prefs.KEY_IP_START, NetInfo.getIpFromLongUnsigned(network_start));
            edit.putString(Prefs.KEY_IP_END, NetInfo.getIpFromLongUnsigned(network_end));
            edit.commit();
        }
    }

    public void setButtons(boolean disable) {
        if (disable) {
            setButtonOff(btn_discover, R.drawable.disabled);
        } else {
            setButtonOn(btn_discover, R.drawable.discover);
        }
    }

    public void cancelTasks() {
        if (mDiscoveryTask != null) {
            mDiscoveryTask.cancel(true);
            mDiscoveryTask = null;
        }
    }

    static class ViewHolder {
        TextView host;
        TextView mac;
        TextView vendor;
        ImageView logo;
        Button track;
        Button delete;
    }

    // Custom ArrayAdapter
    private class HostsAdapter extends ArrayAdapter<HostBean> {
        private final boolean showTrack;

        public HostsAdapter(Context ctxt, boolean showTrack, List<HostBean> elements) {
            super(ctxt, R.layout.list_host, elements);
            this.showTrack = showTrack;
        }

        public HostsAdapter(Context ctxt, List<HostBean> elements) {
            this(ctxt, true, elements);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.list_host, null);
                holder = new ViewHolder();
                holder.host = (TextView) convertView.findViewById(R.id.list);
                holder.mac = (TextView) convertView.findViewById(R.id.mac);
                holder.vendor = (TextView) convertView.findViewById(R.id.vendor);
                holder.logo = (ImageView) convertView.findViewById(R.id.logo);
                holder.track = (Button) convertView.findViewById(R.id.track);
                holder.delete = (Button) convertView.findViewById(R.id.delete);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            final HostBean host = getItem(position);
            if (host.deviceType == HostBean.TYPE_GATEWAY) {
                holder.logo.setImageResource(R.drawable.router);
            } else if (host.isAlive == 1 || !host.hardwareAddress.equals(NetInfo.NOMAC)) {
                holder.logo.setImageResource(R.drawable.computer);
            } else {
                holder.logo.setImageResource(R.drawable.computer_down);
            }
            if (host.hostname != null && !host.hostname.equals(host.ipAddress)) {
                holder.host.setText(String.format("%s (%s)", host.hostname, host.ipAddress));
            } else {
                holder.host.setText(host.ipAddress);
            }
            if (!host.hardwareAddress.equals(NetInfo.NOMAC)) {
                holder.mac.setText(host.hardwareAddress);
                if(host.nicVendor != null){
                    holder.vendor.setText(host.nicVendor);
                } else {
                    holder.vendor.setText(R.string.info_unknown);
                }
                holder.mac.setVisibility(View.VISIBLE);
                holder.vendor.setVisibility(View.VISIBLE);
            } else {
                holder.mac.setVisibility(View.GONE);
                holder.vendor.setVisibility(View.GONE);
            }
            if (showTrack) {
                holder.delete.setVisibility(View.GONE);
                holder.track.setVisibility(View.VISIBLE);
                holder.track.setOnClickListener(view -> {
                    addHost(host);
                    Toast.makeText(getApplicationContext(), String.format("Tracking %s", host.hostname), Toast.LENGTH_SHORT).show();
                });
            }
            else
            {
                holder.track.setVisibility(View.GONE);
                holder.delete.setVisibility(View.VISIBLE);
                holder.delete.setOnClickListener(view -> {
                    removeHost(host);
                    Toast.makeText(getApplicationContext(), String.format("Removed %s", host.hostname), Toast.LENGTH_SHORT).show();
                });
            }
            return convertView;
        }
    }

    private void removeHost(HostBean host) {
        trackedHosts.remove(host);

        // Add to DB (SharedPrefs json string)
        tinydb.putListObject(TRACKED_DEVICES, trackedHosts);
        trackedAdapter.notifyDataSetChanged();
    }

    private void addHost(HostBean host) {
        trackedHosts.add(host);
        // Add to DB (SharedPrefs json string)
        tinydb.putListObject(TRACKED_DEVICES, trackedHosts);
        // don't need to notifyDataSetChanged because the list is not visible
    }

    /**
     * Discover hosts
     */
    private void startDiscovering() {
        stopService();
        mDiscoveryTask = new DefaultDiscoveryTask(ActivityDiscovery.this, ActivityDiscovery.this);
        mDiscoveryTask.setNetwork(network_ip, network_start, network_end);
        mDiscoveryTask.execute();
        btn_discover.setText(R.string.btn_discover_cancel);
        setButton(btn_discover, R.drawable.cancel, false);
        btn_discover.setOnClickListener(v -> cancelTasks());
        trackedList.setVisibility(View.GONE);
        discoverList.setVisibility(View.VISIBLE);
        makeToast(R.string.discover_start);
        setProgressBarVisibility(true);
        setProgressBarIndeterminateVisibility(true);
        initList();
    }

    @Override
    public void onTaskCompleted(boolean wasCancelled) {
        if(wasCancelled) {
            makeToast(R.string.discover_canceled);
            setButtonOn(btn_discover, R.drawable.discover);
            btn_discover.setOnClickListener(v -> startDiscovering());
            btn_discover.setText(R.string.btn_discover);
            trackedAdapter.notifyDataSetChanged();
            discoverList.setVisibility(View.GONE);
            trackedList.setVisibility(View.VISIBLE);
            startService();
        }
        else {
            makeToast(R.string.discover_finished);
            setButtonOn(btn_discover, R.drawable.arrow_left);
            btn_discover.setOnClickListener(v -> onTaskCompleted(true));
            btn_discover.setText(R.string.btn_discover_return);
        }
        Log.e(TAG, "stopDiscovering()");
        mDiscoveryTask = null;
        setProgressBarVisibility(false);
        setProgressBarIndeterminateVisibility(false);
    }
    @Override
    public void setTaskProgress(int i) {
        this.setProgress(i);
    }

    @Override
    public void onProgressUpdate(HostBean host) {
        host.position = hosts.size();
        hosts.add(host);
        discoverAdapter.notifyDataSetChanged();
    }

    private void initList() {
        // setSelectedHosts(false);
        discoverAdapter.clear();
    }

    public void makeToast(int msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void setButton(Button btn, int res, boolean disable) {
        if (disable) {
            setButtonOff(btn, res);
        } else {
            setButtonOn(btn, res);
        }
    }

    private void setButtonOff(Button b, int drawable) {
        b.setClickable(false);
        b.setEnabled(false);
        b.setCompoundDrawablesWithIntrinsicBounds(drawable, 0, 0, 0);
    }

    private void setButtonOn(Button b, int drawable) {
        b.setClickable(true);
        b.setEnabled(true);
        b.setCompoundDrawablesWithIntrinsicBounds(drawable, 0, 0, 0);
    }

    // Service init section
    public void startService() {
        Log.d(TAG, "startService called");
        if (!NetworkSniffService.isServiceRunning && !trackedHosts.isEmpty()) {
            NetworkSniffService.breakLoop = false;
            Intent serviceIntent = new Intent(this, NetworkSniffService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
        }
    }

    public void stopService() {
        Log.d(TAG, "stopService called");
        if (NetworkSniffService.isServiceRunning) {
            NetworkSniffService.breakLoop = true;
            Intent serviceIntent = new Intent(this, NetworkSniffService.class);
            stopService(serviceIntent);
        }
    }

    public void startServiceViaWorker() {
        Log.d(TAG, "startServiceViaWorker called");
        String UNIQUE_WORK_NAME = "StartNetworkSniffServiceViaWorker";
        WorkManager workManager = WorkManager.getInstance(this);

        // As per Documentation: The minimum repeat interval that can be defined is 15 minutes
        // (same as the JobScheduler API), but in practice 15 doesn't work. Using 16 here
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                        RestartWorker.class,
                        16,
                        TimeUnit.MINUTES)
                        .build();

        // to schedule a unique work, no matter how many times app is opened i.e. startServiceViaWorker gets called
        // do check for AutoStart permission
        workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
