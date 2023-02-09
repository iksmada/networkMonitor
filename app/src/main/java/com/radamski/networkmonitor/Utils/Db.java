/*
 * Copyright (C) 2009-2010 Aubort Jean-Baptiste (Rorist)
 * Licensed under GNU's GPL 2, see README
 */

package com.radamski.networkmonitor.Utils;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.zip.GZIPInputStream;

public class Db {

    private final static String TAG = "Db";
    private Context ctxt = null;

    // Databases information
    public static final String PATH = "/data/data/com.radamski.networkmonitor/files/";
    public static final String DB_SERVICES = "services.db";
    public static final String DB_PROBES = "probes.db";
    public static final String DB_NIC = "nic.db";
    public static final String DB_SAVES = "saves.db";

    public Db(Context ctxt) {
        this.ctxt = ctxt;
        // new File(PATH).mkdirs();
    }

    public static SQLiteDatabase openDb(String db_name) {
        return openDb(db_name, SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    }

    public static SQLiteDatabase openDb(String db_name, int flags) {
        try {
            return SQLiteDatabase.openDatabase(PATH + db_name, null, flags);
        } catch (SQLiteException e) {
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    public void copyDbToDevice(int res, String db_name) throws NullPointerException, IOException {
        // InputStream in = ctxt.getResources().openRawResource(res);
        GZIPInputStream in = new GZIPInputStream(ctxt.getResources().openRawResource(res));
        OutputStream out = ctxt.openFileOutput(db_name, Context.MODE_PRIVATE);
        final ReadableByteChannel ic = Channels.newChannel(in);
        final WritableByteChannel oc = Channels.newChannel(out);
        fastChannelCopy(ic, oc);
    }

    public static void fastChannelCopy(final ReadableByteChannel src, final WritableByteChannel dest)
            throws IOException, NullPointerException {
        if (src != null && dest != null) {
            final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
            while (src.read(buffer) != -1) {
                // prepare the buffer to be drained
                buffer.flip();
                // write to the channel, may block
                dest.write(buffer);
                // If partial transfer, shift remainder down
                // If buffer is empty, same as doing clear()
                buffer.compact();
            }
            // EOF will leave buffer in fill state
            buffer.flip();
            // make sure the buffer is fully drained.
            while (buffer.hasRemaining()) {
                dest.write(buffer);
            }
        }
    }
}
