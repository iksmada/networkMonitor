/*
 * Copyright (C) 2009-2010 Aubort Jean-Baptiste (Rorist)
 * Licensed under GNU's GPL 2, see README
 */

package com.radamski.networkmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.radamski.networkmonitor.Network.HardwareAddress;
import com.radamski.networkmonitor.Network.HostBean;
import com.radamski.networkmonitor.Network.NetInfo;
import com.radamski.networkmonitor.Network.RateControl;
import com.radamski.networkmonitor.Utils.Prefs;
import com.radamski.networkmonitor.Utils.Save;
import com.radamski.networkmonitor.Utils.TaskInterface;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DefaultDiscoveryTask extends AbstractDiscoveryTask {

    private final String TAG = "DefaultDiscoveryTask";
    private final static int[] DPORTS = { 139, 445, 22, 80 };
    private final static int TIMEOUT_SCAN = 900; // seconds
    private final static int TIMEOUT_SHUTDOWN = 10; // seconds
    private final static int THREADS = 10; //FIXME: Test, plz set in options again ?
    private final int mRateMult = 5; // Number of alive hosts between Rate
    private final SharedPreferences prefs;
    private final NetInfo net;
    private final boolean useThreads;
    private int pt_move = 2; // 1=backward 2=forward
    private ExecutorService mPool;
    private boolean doRateControl;
    private RateControl mRateControl;
    private Save mSave;

    public DefaultDiscoveryTask(TaskInterface comm, Context ctxt, boolean useThreads) {
        super(comm);
        prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
        net = new NetInfo(ctxt);
        mRateControl = new RateControl();
        mSave = new Save();
        this.useThreads = useThreads;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        size = (int) (end - start + 1);
        doRateControl = prefs.getBoolean(Prefs.KEY_RATECTRL_ENABLE,
                Prefs.DEFAULT_RATECTRL_ENABLE);
    }

    @Override
    protected Void doInBackground(Void... params) {
        Log.v(TAG, "start=" + NetInfo.getIpFromLongUnsigned(start) + " (" + start
                + "), end=" + NetInfo.getIpFromLongUnsigned(end) + " (" + end
                + "), length=" + size);
        mPool = Executors.newFixedThreadPool(THREADS);
        if (ip <= end && ip >= start && useThreads) {
            Log.i(TAG, "Back and forth scanning");
            // gateway
            launch(start);

            // hosts
            long pt_backward = ip;
            long pt_forward = ip + 1;
            long size_hosts = size - 1;

            for (int i = 0; i < size_hosts; i++) {
                // Set pointer if of limits
                if (pt_backward <= start) {
                    pt_move = 2;
                } else if (pt_forward > end) {
                    pt_move = 1;
                }
                // Move back and forth
                if (pt_move == 1) {
                    launch(pt_backward);
                    pt_backward--;
                    pt_move = 2;
                } else if (pt_move == 2) {
                    launch(pt_forward);
                    pt_forward++;
                    pt_move = 1;
                }
            }
        } else {
            Log.i(TAG, "Sequential scanning");
            for (long i = start; i <= end; i++) {
                launch(i);
            }
        }
        mPool.shutdown();
        try {
            if(!mPool.awaitTermination(TIMEOUT_SCAN, TimeUnit.SECONDS)){
                mPool.shutdownNow();
                Log.w(TAG, "Shutting down pool");
                if(!mPool.awaitTermination(TIMEOUT_SHUTDOWN, TimeUnit.SECONDS)){
                    Log.w(TAG, "Pool did not terminate");
                }
            }
        } catch (InterruptedException e){
            Log.e(TAG, e.getMessage());
            mPool.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            mSave.closeDb();
        }
        return null;
    }

    @Override
    protected void onCancelled() {
        if (mPool != null) {
            synchronized (mPool) {
                mPool.shutdownNow();
                // FIXME: Prevents some task to end (and close the Save DB)
            }
        }
        super.onCancelled();
    }

    private void launch(long i) {
        if(!mPool.isShutdown()) {
            if(useThreads) {
                mPool.execute(new CheckRunnable(NetInfo.getIpFromLongUnsigned(i)));
            } else {
                // synchronized method
                new CheckRunnable(NetInfo.getIpFromLongUnsigned(i)).run();
            }
        }
    }

    private int getRate() {
        if (doRateControl) {
            return mRateControl.rate;
        }
        return Integer.parseInt(prefs.getString(Prefs.KEY_TIMEOUT_DISCOVER,
                Prefs.DEFAULT_TIMEOUT_DISCOVER));
    }

    private class CheckRunnable implements Runnable {
        private String addr;

        CheckRunnable(String addr) {
            this.addr = addr;
        }

        public void run() {
            if(isCancelled()) {
                publish(null);
            }
            Log.d(TAG, "run="+addr);
            // Create host object
            final HostBean host = new HostBean();
            host.responseTime = getRate();
            host.ipAddress = addr;
            try {
                InetAddress h = InetAddress.getByName(addr);
                // Rate control check
                if (doRateControl && mRateControl.indicator != null && hosts_done % mRateMult == 0) {
                    mRateControl.adaptRate();
                }
                // Arp Check #1
                host.hardwareAddress = HardwareAddress.getHardwareAddress(addr);
                if(!NetInfo.NOMAC.equals(host.hardwareAddress)){
                    Log.i(TAG, "found using arp #1 "+addr);
                    publish(host);
                    return;
                }
                // Native InetAddress check
                if (h.isReachable(getRate())) {
                    Log.i(TAG, "found using InetAddress ping "+addr);
                    publish(host);
                    // Set indicator and get a rate
                    if (doRateControl && mRateControl.indicator == null) {
                        mRateControl.indicator = addr;
                        mRateControl.adaptRate();
                    }
                    return;
                }
                // Arp Check #2
                host.hardwareAddress = HardwareAddress.getHardwareAddress(addr);
                if(!NetInfo.NOMAC.equals(host.hardwareAddress)){
                    Log.i(TAG, "found using arp #2 "+addr);
                    publish(host);
                    return;
                }
                // Custom check
                int port;
                // TODO: Get ports from options
                Socket s = new Socket();
                for (int i = 0; i < DPORTS.length; i++) {
                    try {
                        s.bind(null);
                        s.connect(new InetSocketAddress(addr, DPORTS[i]), getRate());
                        Log.v(TAG, "found using TCP connect "+addr+" on port=" + DPORTS[i]);
                    } catch (IOException e) {
                    } catch (IllegalArgumentException e) {
                    } finally {
                        try {
                            s.close();
                        } catch (Exception e){
                        }
                    }
                }

                /*
                if ((port = Reachable.isReachable(h, getRate())) > -1) {
                    Log.v(TAG, "used Network.Reachable object, "+addr+" port=" + port);
                    publish(host);
                    return;
                }
                */
                // Arp Check #3
                host.hardwareAddress = HardwareAddress.getHardwareAddress(addr);
                if(!NetInfo.NOMAC.equals(host.hardwareAddress)){
                    Log.i(TAG, "found using arp #3 "+addr);
                    publish(host);
                    return;
                }
                publish(null);

            } catch (IOException e) {
                publish(null);
                Log.e(TAG, e.getMessage());
            } 
        }
    }

    private void publish(final HostBean host) {
        hosts_done++;
        if(host == null){
            publishProgress((HostBean) null);
            return; 
        }

        // Mac Addr not already detected
        if(NetInfo.NOMAC.equals(host.hardwareAddress)){
            host.hardwareAddress = HardwareAddress.getHardwareAddress(host.ipAddress);
        }

        // Is gateway ?
        if (net.gatewayIp.equals(host.ipAddress)) {
            host.deviceType = HostBean.TYPE_GATEWAY;
        }

        // FQDN
        // Static
        if ((host.hostname = mSave.getCustomName(host)) == null) {
            // DNS
            if (prefs.getBoolean(Prefs.KEY_RESOLVE_NAME,
                    Prefs.DEFAULT_RESOLVE_NAME) == true) {
                try {
                    host.hostname = (InetAddress.getByName(host.ipAddress)).getCanonicalHostName();
                } catch (UnknownHostException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
            // TODO: NETBIOS
            //try {
            //    host.hostname = NbtAddress.getByName(addr).getHostName();
            //} catch (UnknownHostException e) {
            //    Log.i(TAG, e.getMessage());
            //}
        }

        publishProgress(host);
    }
}
