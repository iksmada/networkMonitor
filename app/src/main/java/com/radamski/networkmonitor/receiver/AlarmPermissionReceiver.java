package com.radamski.networkmonitor.receiver;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.radamski.networkmonitor.ActivityDiscovery;
import com.radamski.networkmonitor.Utils.Manager;
import com.radamski.networkmonitor.Utils.Prefs;

public class AlarmPermissionReceiver extends BroadcastReceiver {
    private final String TAG = "AlarmPermissionReceiver";

    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive called");
        if (intent.getAction().equals(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Manager manager = Manager.getById(prefs.getInt(Prefs.KEY_MANAGER, Prefs.DEFAULT_MANAGER));
            if (manager == Manager.ALARM_MANAGER) {
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        ActivityDiscovery.scheduleAlarm(alarmManager, context);
                    } else {
                        Log.w(TAG, "Permission not granted or revoked");
                    }
                }
            } else {
                Log.w(TAG, String.format("Manager is %s, nothing to be done here", manager));
            }
        }
    }
}
