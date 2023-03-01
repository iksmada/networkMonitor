package com.radamski.networkmonitor;

import android.os.AsyncTask;

import com.radamski.networkmonitor.Network.HostBean;
import com.radamski.networkmonitor.Utils.TaskInterface;

import java.lang.ref.WeakReference;

public abstract class AbstractDiscoveryTask extends AsyncTask<Void, HostBean, Void> {

    private final String TAG = "AbstractDiscoveryTask";

    protected int hosts_done = 0;
    final protected WeakReference<TaskInterface> weakComm;

    protected long ip;
    protected long start = 0;
    protected long end = 0;
    protected long size = 0;

    public static final String TRACKED_DEVICES = "trackedDevices";
    public static final String CONNECTED_TRACKED_DEVICES = "connectedTrackedDevices";
    public static final String COUNT_DOWN_DEVICES = "countDownDevices";

    public AbstractDiscoveryTask(TaskInterface discover) {
        weakComm = new WeakReference<>(discover);
    }

    public void setNetwork(long ip, long start, long end) {
        this.ip = ip;
        this.start = start;
        this.end = end;
    }

    abstract protected Void doInBackground(Void... params);

    @Override
    protected void onPreExecute() {
        if (weakComm != null) {
            final TaskInterface comm = weakComm.get();
            if (comm != null) {
                comm.setTaskProgress(0);
            }
        }
    }

    @Override
    protected void onProgressUpdate(HostBean... host) {
        if (weakComm != null) {
            final TaskInterface comm = weakComm.get();
            if (comm != null) {
                if (!isCancelled()) {
                    if (host[0] != null) {
                        comm.onProgressUpdate(host[0]);
                    }
                    if (size > 0) {
                        comm.setTaskProgress((int) (hosts_done * 10000 / size));
                    }
                }

            }
        }
    }

    @Override
    protected void onPostExecute(Void unused) {
        if (weakComm != null) {
            final TaskInterface comm = weakComm.get();
            if (comm != null) {
                comm.onTaskCompleted(false);
            }
        }
    }

    @Override
    protected void onCancelled() {
        if (weakComm != null) {
            final TaskInterface comm = weakComm.get();
            if (comm != null) {
                comm.onTaskCompleted(true);
            }
        }
        super.onCancelled();
    }
}
