/*
 * Copyright (C) 2009-2010 Aubort Jean-Baptiste (Rorist)
 * Licensed under GNU's GPL 2, see README
 */

// Inspired by http://connectbot.googlecode.com/svn/trunk/connectbot/src/org/connectbot/bean/HostBean.java
package com.radamski.networkmonitor.Network;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField;
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputObject;
import com.radamski.networkmonitor.ActivityDiscovery;

import java.util.ArrayList;
import java.util.HashMap;

@TaskerInputObject(key = "host")
public class HostBean implements Parcelable {

    public static final String EXTRA = ActivityDiscovery.PKG + ".extra";
    public static final int TYPE_GATEWAY = 0;
    public static final int TYPE_COMPUTER = 1;

    public int deviceType = TYPE_COMPUTER;
    public int isAlive = 1;
    public int position = 0;
    public int responseTime = 0; // ms
    @TaskerInputField(key = "ipAddress")
    public String ipAddress = null;
    @TaskerInputField(key = "hostname")
    public String hostname = null;
    @TaskerInputField(key = "hardwareAddress")
    public String hardwareAddress = NetInfo.NOMAC;
    public String nicVendor = "Unknown";
    public String os = "Unknown";
    public HashMap<Integer, String> services = null;
    public HashMap<Integer, String> banners = null;
    public ArrayList<Integer> portsOpen = null;
    public ArrayList<Integer> portsClosed = null;

    public HostBean() {
        // New object
    }

    public HostBean(Parcel in) {
        // Object from parcel
        readFromParcel(in);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(deviceType);
        dest.writeInt(isAlive);
        dest.writeString(ipAddress);
        dest.writeString(hostname);
        dest.writeString(hardwareAddress);
        dest.writeString(nicVendor);
        dest.writeString(os);
        dest.writeInt(responseTime);
        dest.writeInt(position);
        dest.writeMap(services);
        dest.writeMap(banners);
        dest.writeList(portsOpen);
        dest.writeList(portsClosed);
    }

    @SuppressWarnings("unchecked")
    private void readFromParcel(Parcel in) {
        deviceType = in.readInt();
        isAlive = in.readInt();
        ipAddress = in.readString();
        hostname = in.readString();
        hardwareAddress = in.readString();
        nicVendor = in.readString();
        os = in.readString();
        responseTime = in.readInt();
        position = in.readInt();
        services = in.readHashMap(null);
        banners = in.readHashMap(null);
        portsOpen = in.readArrayList(Integer.class.getClassLoader());
        portsClosed = in.readArrayList(Integer.class.getClassLoader());
    }

    @SuppressWarnings("unchecked")
    public static final Creator CREATOR = new Creator() {
        public HostBean createFromParcel(Parcel in) {
            return new HostBean(in);
        }

        public HostBean[] newArray(int size) {
            return new HostBean[size];
        }
    };

    @Override
    public boolean equals(Object other)
    {
        if(other instanceof HostBean)
        {
            HostBean otherHost = (HostBean) other;
            if(this.hardwareAddress.equals(otherHost.hardwareAddress)) {
                if (this.hostname != null && !this.hostname.equals(this.ipAddress)) {
                    return this.hostname.equals(otherHost.hostname);
                }
                // if the host name is not available we can only compare the mac address
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + hardwareAddress.hashCode();
        if (hostname != null && !hostname.equals(ipAddress)) {
            hash = 31 * hash + this.hostname.hashCode();
        }
        return hash;
    }

    @NonNull
    @Override
    public String toString() {
        if (hostname != null && !hostname.equals(ipAddress)) {
            return String.format("%s (%s)", hostname, ipAddress);
        } else {
            return ipAddress;
        }
    }
}
