package com.radamski.networkmonitor.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.radamski.networkmonitor.ActivityNet;
import com.radamski.networkmonitor.Network.NetInfo;
import com.radamski.networkmonitor.R;
import com.radamski.networkmonitor.Utils.Prefs;

import java.lang.ref.WeakReference;

public class NetwortkStatusReceiver extends BroadcastReceiver {

    private final String TAG = "NetwortkStatusReceiver";
    private final WeakReference<ActivityNet> mActivity;

    public String getInfo_ip_str() {
        return info_ip_str;
    }

    public String getInfo_mo_str() {
        return info_mo_str;
    }

    public String getInfo_in_str() {
        return info_in_str;
    }

    private String info_ip_str;
    private String info_mo_str;
    private String info_in_str;
    protected NetInfo net = null;
    private Context ctxt;
    private ConnectivityManager connMgr;
    private SharedPreferences prefs;

    public NetwortkStatusReceiver(ActivityNet discover) {
        super();
        mActivity = new WeakReference<>(discover);
    }

    public void onReceive(Context ctxt, Intent intent) {
        info_ip_str = "";
        info_mo_str = "";
        this.ctxt = ctxt;
        net = new NetInfo(ctxt);
        connMgr = (ConnectivityManager) ctxt.getSystemService(Context.CONNECTIVITY_SERVICE);
        prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);

        // Wifi state
        String action = intent.getAction();
        if (action != null) {
            if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                int WifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1);
                //Log.d(TAG, "WifiState=" + WifiState);
                switch (WifiState) {
                    case WifiManager.WIFI_STATE_ENABLING:
                        info_in_str = getString(R.string.wifi_enabling);
                        break;
                    case WifiManager.WIFI_STATE_ENABLED:
                        info_in_str = getString(R.string.wifi_enabled);
                        break;
                    case WifiManager.WIFI_STATE_DISABLING:
                        info_in_str = getString(R.string.wifi_disabling);
                        break;
                    case WifiManager.WIFI_STATE_DISABLED:
                        info_in_str = getString(R.string.wifi_disabled);
                        break;
                    default:
                        info_in_str = getString(R.string.wifi_unknown);
                }
            }

            if (action.equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION) && net.getWifiInfo()) {
                SupplicantState sstate = net.getSupplicantState();
                //Log.d(TAG, "SupplicantState=" + sstate);
                if (sstate == SupplicantState.SCANNING) {
                    info_in_str = getString(R.string.wifi_scanning);
                } else if (sstate == SupplicantState.ASSOCIATING) {
                    info_in_str = getString(R.string.wifi_associating,
                            (net.ssid != null ? net.ssid : (net.bssid != null ? net.bssid
                                    : net.macAddress)));
                } else if (sstate == SupplicantState.COMPLETED) {
                    info_in_str = getString(R.string.wifi_dhcp, net.ssid);
                }
            }
        }

        // 3G(connected) -> Wifi(connected)
        // Support Ethernet, with ConnectivityManager.TYPE_ETHER=3
        final NetworkInfo ni = connMgr.getActiveNetworkInfo();
        if (ni != null) {
            //Log.i(TAG, "NetworkState="+ni.getDetailedState());
            if (ni.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
                int type = ni.getType();
                //Log.i(TAG, "NetworkType="+type);
                if (type == ConnectivityManager.TYPE_WIFI) { // WIFI
                    net.getWifiInfo();
                    if (net.ssid != null) {
                        net.getIp();
                        info_ip_str = getString(R.string.net_ip, net.ip, net.cidr, net.intf);
                        info_in_str = getString(R.string.net_ssid, net.ssid);
                        info_mo_str = getString(R.string.net_mode, getString(
                                R.string.net_mode_wifi, net.speed, WifiInfo.LINK_SPEED_UNITS));
                        setButtons(false);
                    }
                } else if (type == ConnectivityManager.TYPE_MOBILE) { // 3G
                    if (prefs.getBoolean(Prefs.KEY_MOBILE, Prefs.DEFAULT_MOBILE)
                            || prefs.getString(Prefs.KEY_INTF, Prefs.DEFAULT_INTF) != null) {
                        net.getMobileInfo();
                        if (net.carrier != null) {
                            net.getIp();
                            info_ip_str = getString(R.string.net_ip, net.ip, net.cidr, net.intf);
                            info_in_str = getString(R.string.net_carrier, net.carrier);
                            info_mo_str = getString(R.string.net_mode,
                                    getString(R.string.net_mode_mobile));
                            setButtons(false);
                        }
                    }
                } else if (type == 3 || type == 9) { // ETH
                    net.getIp();
                    info_ip_str = getString(R.string.net_ip, net.ip, net.cidr, net.intf);
                    info_in_str = "";
                    info_mo_str = getString(R.string.net_mode) + getString(R.string.net_mode_eth);
                    setButtons(false);
                    Log.i(TAG, "Ethernet connectivity detected!");
                } else {
                    Log.i(TAG, "Connectivity unknown!");
                    info_mo_str = getString(R.string.net_mode)
                            + getString(R.string.net_mode_unknown);
                }
            } else {
                cancelTasks();
            }
        } else {
            cancelTasks();
        }

        // Always update network info
        setInfo();
    }

    private void setInfo() {
        if (mActivity != null) {
            final ActivityNet discover = mActivity.get();
            if (discover != null) {
                discover.setInfo(info_ip_str, info_in_str, info_mo_str);
            }
        }
    }

    private void cancelTasks() {
        if (mActivity != null) {
            final ActivityNet discover = mActivity.get();
            if (discover != null) {
                discover.cancelTasks();
            }
        }
    }

    private void setButtons(boolean state) {
        if (mActivity != null) {
            final ActivityNet discover = mActivity.get();
            if (discover != null) {
                discover.setButtons(state);
            }
        }
    }

    private String getString(int resId) {
        return ctxt.getString(resId);
    }

    public final String getString(int resId, Object... formatArgs) {
        return ctxt.getString(resId, formatArgs);
    }
}
