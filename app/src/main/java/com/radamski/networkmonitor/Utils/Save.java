/*
 * Copyright (C) 2009-2010 Aubort Jean-Baptiste (Rorist)
 * Licensed under GNU's GPL 2, see README
 */

package com.radamski.networkmonitor.Utils;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.radamski.networkmonitor.Network.HostBean;

public class Save {

    private static final String TAG = "Save";
    private static final String SELECT = "select name from nic where mac=?";
    private static final String INSERT = "insert or replace into nic (name,mac) values (?,?)";
    private static final String DELETE = "delete from nic where mac=?";
    private static SQLiteDatabase db;

    public void closeDb(){
        if(db != null && db.isOpen()){
            db.close();
        }
    }

    public synchronized String getCustomName(HostBean host) {
        String name = null;
        Cursor c = null;
        try {
            db = getDb();
            c = db.rawQuery(SELECT, new String[] { host.hardwareAddress.replace(":", "").toUpperCase() });
            if (c.moveToFirst()) {
                name = c.getString(0);
            } else if(host.hostname != null) {
                name = host.hostname;
            }
        } catch (SQLiteException e) {
            Log.e(TAG, e.getMessage());
        } catch (IllegalStateException e) {
            Log.e(TAG, e.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return name;
    }

    private static synchronized SQLiteDatabase getDb(){
        if(db == null || !db.isOpen()) {
            // FIXME: read only ?
            db = Db.openDb(Db.DB_SAVES, SQLiteDatabase.NO_LOCALIZED_COLLATORS|SQLiteDatabase.OPEN_READONLY);
        }
        return db;
    }
}
