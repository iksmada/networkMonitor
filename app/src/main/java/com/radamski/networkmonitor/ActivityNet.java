package com.radamski.networkmonitor;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.radamski.networkmonitor.Network.NetInfo;
import com.radamski.networkmonitor.receiver.NetwortkStatusReceiver;

public abstract class ActivityNet extends Activity {

    private final String TAG = "ActivityNet";

    protected final static String EXTRA_WIFI = "wifiDisabled";
    protected Context ctxt;
    protected SharedPreferences prefs = null;
    protected NetInfo net = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ctxt = getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
        net = new NetInfo(ctxt);
    }

    @Override
    public void onResume() {
        super.onResume();
        setButtons(true);
        // Listening for network events
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        registerReceiver(receiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    public abstract void setInfo(String info_ip_str, String info_in_str, String info_mo_str);

    public abstract void setButtons(boolean disable);

    public abstract void cancelTasks();

    private final BroadcastReceiver receiver = new NetwortkStatusReceiver(this);
}
