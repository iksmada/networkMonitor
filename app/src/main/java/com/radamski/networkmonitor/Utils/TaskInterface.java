package com.radamski.networkmonitor.Utils;

import com.radamski.networkmonitor.Network.HostBean;

public interface TaskInterface {
    void onTaskCompleted(boolean wasCancelled);
    void setTaskProgress(int i);
    void onProgressUpdate(HostBean host);
}
