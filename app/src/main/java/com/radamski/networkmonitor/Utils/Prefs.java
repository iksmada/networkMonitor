/*
 * Copyright (C) 2009-2010 Aubort Jean-Baptiste (Rorist)
 * Licensed under GNU's GPL 2, see README
 */

package com.radamski.networkmonitor.Utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;

import com.radamski.networkmonitor.ActivityDiscovery;
import com.radamski.networkmonitor.R;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;


public class Prefs extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    // TODO: Show values in summary

    private final String TAG = "Prefs";

    public final static String KEY_RESOLVE_NAME = "resolve_name";
    public final static boolean DEFAULT_RESOLVE_NAME = true;

    public final static String KEY_VIBRATE_FINISH = "vibrate_finish";
    public final static boolean DEFAULT_VIBRATE_FINISH = false;

    public final static String KEY_PORT_START = "port_start";
    public final static String DEFAULT_PORT_START = "1";

    public final static String KEY_PORT_END = "port_end";
    public final static String DEFAULT_PORT_END = "1024";
    public final static int MAX_PORT_END = 65535;

    public static final String KEY_SSH_USER = "ssh_user";
    public static final String DEFAULT_SSH_USER = "root";

    //public static final String KEY_NTHREADS = "nthreads";
    //public static final String DEFAULT_NTHREADS = "8";

    public static final String KEY_RESET_NICDB = "resetdb";
    public static final int DEFAULT_RESET_NICDB = 1;

    public static final String KEY_RESET_SERVICESDB = "resetservicesdb";
    public static final int DEFAULT_RESET_SERVICESDB = 0;

    public static final String KEY_METHOD_MONITOR = "monitor_method";
    public static final String DEFAULT_METHOD_MONITOR = String.valueOf(Manager.ALARM_MANAGER.id);

    // public static final String KEY_METHOD_PORTSCAN = "method_portscan";
    // public static final String DEFAULT_METHOD_PORTSCAN = "0";

    public final static String KEY_TIMEOUT_FORCE = "timeout_force";
    public final static boolean DEFAULT_TIMEOUT_FORCE = false;

    public final static String KEY_TIMEOUT_PORTSCAN = "timeout_portscan";
    public final static String DEFAULT_TIMEOUT_PORTSCAN = "500";

    public static final String KEY_RATECTRL_ENABLE = "ratecontrol_enable";
    public static final boolean DEFAULT_RATECTRL_ENABLE = true;

    public final static String KEY_TIMEOUT_DISCOVER = "timeout_discover";
    public final static String KEY_TIMEOUT_MONITOR = "timeout_monitor";
    public final static String DEFAULT_TIMEOUT_DISCOVER = "1000";
    public final static String DEFAULT_TIMEOUT_MONITOR = "60000";
    public final static String KEY_TRIGGER_COUNTDOWN = "trigger_countdown";
    public final static String DEFAULT_TRIGGER_COUNTDOWN = "3";

    public static final String KEY_BANNER = "banner";
    public static final boolean DEFAULT_BANNER = true;

    public static final String KEY_MOBILE = "allow_mobile";
    public static final boolean DEFAULT_MOBILE = false;

    public static final String KEY_INTF = "interface";
    public static final String DEFAULT_INTF = null;

    public static final String DEFAULT_IP_START = "0.0.0.0";
    public static final String DEFAULT_IP_END = "0.0.0.0";

    public static final String KEY_DONATE = "donate";
    public static final String KEY_WEBSITE = "website";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_VERSION = "version";
    public static final String KEY_WIFI = "wifi";
    public static final String KEY_ALARM_PERIOD = "alarmPeriod";
    public static final long DEFAULT_ALARM_PERIOD = 180000L; // 3 minutes in milliseconds

    private static final String URL_DONATE = "https://www.paypal.com/donate/?business=3DRJ3KX9YKJ7E&no_recurring=1&item_name=Help+a+developer+who+help+you&currency_code=CAD";
    private static final String URL_WEB = "https://github.com/iksmada/networkMonitor";
    private static final String URL_EMAIL = "raphaeladamski@hotmail.com";

    private Context ctxt;
    private PreferenceScreen ps = null;
    private String before_port_start;
    private String before_port_end;

    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);
        ctxt = getApplicationContext();

        ps = getPreferenceScreen();
        ps.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        // Default state of checkboxes
        checkTimeout(KEY_TIMEOUT_DISCOVER, KEY_RATECTRL_ENABLE, false);

        // Reset Nic DB click listener
        Preference resetdb = (Preference) ps.findPreference(KEY_RESET_NICDB);
        resetdb.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                // TODO
                return false;
            }
        });

        // Before change values
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
        before_port_start = prefs.getString(KEY_PORT_START, DEFAULT_PORT_START);
        before_port_end = prefs.getString(KEY_PORT_END, DEFAULT_PORT_END);

        // Interfaces list
        ListPreference intf = (ListPreference) ps.findPreference(KEY_INTF);
        try {
            ArrayList<NetworkInterface> nis = Collections.list(NetworkInterface
                    .getNetworkInterfaces());
            final int len = nis.size();
            // If there's more than just 2 interfaces (local + network)
            if (len > 2) {
                String[] intf_entries = new String[len - 1];
                String[] intf_values = new String[len - 1];
                int i = 0;
                for (int j = 0; j < len; j++) {
                    NetworkInterface ni = nis.get(j);
                    if (!ni.getName().equals("lo")) {
                        intf_entries[i] = ni.getDisplayName();
                        intf_values[i] = ni.getName();
                        i++;
                    }
                }
                intf.setEntries(intf_entries);
                intf.setEntryValues(intf_values);
            } else {
                intf.setEnabled(false);
            }
        } catch (SocketException e) {
            Log.e(TAG, e.getMessage());
            intf.setEnabled(false);
        }

        // Wifi settings listener
        ((Preference) ps.findPreference(KEY_WIFI))
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    public boolean onPreferenceClick(Preference preference) {
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                        return true;
                    }
                });

        // Donate click listener
        ((Preference) ps.findPreference(KEY_DONATE))
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    public boolean onPreferenceClick(Preference preference) {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(URL_DONATE));
                        startActivity(i);
                        return true;
                    }
                });

        // Website
        Preference website = (Preference) ps.findPreference(KEY_WEBSITE);
        website.setSummary(URL_WEB);
        website.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(URL_WEB));
                startActivity(i);
                return true;
            }
        });

        // Contact
        Preference contact = (Preference) ps.findPreference(KEY_EMAIL);
        contact.setSummary(URL_EMAIL);
        contact.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                final Intent emailIntent = new Intent(Intent.ACTION_SEND);
                emailIntent.setType("plain/text");
                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { URL_EMAIL });
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Network Discovery");
                try {
                    startActivity(emailIntent);
                } catch (ActivityNotFoundException e) {
                }
                return true;
            }
        });

        // Version
        Preference version = (Preference) ps.findPreference(KEY_VERSION);
        try {
            version.setSummary(getPackageManager().getPackageInfo(ActivityDiscovery.PKG, 0).versionName);
        } catch (NameNotFoundException e) {
            version.setSummary("something is wrong");
        }

    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        //} else if (key.equals(KEY_NTHREADS)) {
        //    checkMaxThreads();
        if (key.equals(KEY_RATECTRL_ENABLE)) {
            checkTimeout(KEY_TIMEOUT_DISCOVER, KEY_RATECTRL_ENABLE, false);
        }
    }

    private void checkTimeout(String key_pref, String key_cb, boolean value) {
        EditTextPreference timeout = (EditTextPreference) ps.findPreference(key_pref);
        CheckBoxPreference cb = (CheckBoxPreference) ps.findPreference(key_cb);
        if (cb.isChecked()) {
            timeout.setEnabled(value);
        } else {
            timeout.setEnabled(!value);
        }
    }

    //private void checkMaxThreads() {
    //    // Check if nthreads is numeric and between 1-256
    //    EditTextPreference threads = (EditTextPreference) ps.findPreference(KEY_NTHREADS);
    //    int nthreads = 0;
    //    try {
    //        nthreads = Integer.parseInt(threads.getText());
    //    } catch (NumberFormatException e) {
    //        threads.setText(DEFAULT_NTHREADS);
    //        Toast.makeText(ctxt, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
    //    }
    //    if (nthreads < 1 || nthreads > 256) {
    //        threads.setText(DEFAULT_NTHREADS);
    //        Toast.makeText(ctxt, R.string.preferences_error2, Toast.LENGTH_LONG).show();
    //    }
    //}
}
