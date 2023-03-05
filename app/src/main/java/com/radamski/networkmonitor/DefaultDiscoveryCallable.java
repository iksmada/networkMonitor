/*
 * Copyright (C) 2009-2010 Aubort Jean-Baptiste (Rorist)
 * Licensed under GNU's GPL 2, see README
 */

package com.radamski.networkmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.work.ListenableWorker;

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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class DefaultDiscoveryCallable implements Callable<ListenableWorker.Result> {

    private final String TAG = "DefaultDiscoveryCallable";

    protected int hosts_done = 0;
    protected long ip;
    protected long start = 0;
    protected long end = 0;
    protected long size = 0;
    private final static int[] DPORTS = { 139, 445, 22, 80, 25, 53, 88, 110, 123, 137, 138, 143, 192, 389, 443, 465, 500, 554, 587, 636, 993, 995};
    private final static int TIMEOUT_SCAN = 900; // seconds
    private final static int TIMEOUT_SHUTDOWN = 10; // seconds
    private final static int THREADS = 10; //FIXME: Test, plz set in options again ?
    private final int mRateMult = 5; // Number of alive hosts between Rate
    private final SharedPreferences prefs;
    private final NetInfo net;
    private final boolean useThreads;
    private final TaskInterface comm;
    private int pt_move = 2; // 1=backward 2=forward
    private ExecutorService mPool;
    private boolean doRateControl;
    private RateControl mRateControl;
    private Save mSave;
    private List<String> listToVisit;

    public DefaultDiscoveryCallable(TaskInterface comm, Context ctxt, boolean useThreads) {
        this.comm = comm;
        prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
        net = new NetInfo(ctxt);
        mRateControl = new RateControl();
        mSave = new Save();
        this.useThreads = useThreads;
    }

    public void setNetwork(long ip, long start, long end) {
        this.ip = ip;
        this.start = start;
        this.end = end;
        this.size = (int) (end - start + 1);
    }

    public void setListToVisit(List<String> listToVisit)
    {
        this.listToVisit = listToVisit;
    }

    @Override
    public ListenableWorker.Result call() {
        // onPreExecute starts
        doRateControl = prefs.getBoolean(Prefs.KEY_RATECTRL_ENABLE,
                Prefs.DEFAULT_RATECTRL_ENABLE);
        // onPreExecute ends
        // doInBackground starts
        Log.v(TAG, "start=" + NetInfo.getIpFromLongUnsigned(start) + " (" + start
                + "), end=" + NetInfo.getIpFromLongUnsigned(end) + " (" + end
                + "), length=" + size);
        ListenableWorker.Result result = ListenableWorker.Result.success();
        mPool = Executors.newFixedThreadPool(THREADS);

        if (ip <= end && ip >= start && useThreads && listToVisit == null) {
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
            if(listToVisit == null)
            {
                listToVisit = LongStream.rangeClosed(start, end).boxed().map(NetInfo::getIpFromLongUnsigned).collect(Collectors.toList());
            }
            listToVisit.forEach(this::launch);
        }
        mPool.shutdown();
        try {
            if(!mPool.awaitTermination(TIMEOUT_SCAN, TimeUnit.SECONDS)){
                mPool.shutdownNow();
                Log.w(TAG, "Shutting down pool");
                if(!mPool.awaitTermination(TIMEOUT_SHUTDOWN, TimeUnit.SECONDS)){
                    Log.w(TAG, "Pool did not terminate");
                    comm.onTaskCompleted(true);
                    result = ListenableWorker.Result.retry();
                }
            }
        } catch (InterruptedException e){
            Log.e(TAG, e.getMessage());
            mPool.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            mSave.closeDb();
        }
        // doInBackground ends
        // onPostExecute starts
        comm.onTaskCompleted(false);
        // onPostExecute ends

        return result;
    }

    private void launch(long i) {
        launch(NetInfo.getIpFromLongUnsigned(i));
    }

    private void launch(String ip) {
        if(!mPool.isShutdown()) {
            if(useThreads) {
                mPool.execute(new CheckRunnable(ip));
            } else {
                new CheckRunnable(ip).run();
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

    public ExecutorService getExecutor() {
        return mPool;
    }

    private class CheckRunnable implements Runnable {
        private String addr;

        CheckRunnable(String addr) {
            this.addr = addr;
        }

        public void run() {
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

                // Custom check
                int port;
                // TODO: Get ports from options
                Socket s = new Socket();
                for (int i = 0; i < DPORTS.length; i++) {
                    try {
                        s.bind(null);
                        // TODO export this timeout to PREFs
                        s.connect(new InetSocketAddress(addr, DPORTS[i]), 500);
                        Log.i(TAG, "found using TCP connect "+addr+" on port=" + DPORTS[i]);
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

        comm.onProgressUpdate(host);
    }
}
