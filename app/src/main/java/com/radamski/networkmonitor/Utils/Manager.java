package com.radamski.networkmonitor.Utils;

import java.util.Arrays;

public enum Manager {

    WORK_MANAGER(1), ALARM_MANAGER(2), SERVICE(3);
    public final int id;
    Manager(int id) {
        this.id = id;
    }

    public static Manager getById(int id) {
        return Arrays.stream(Manager.values()).filter(manager -> manager.id == id).findAny().orElseThrow(RuntimeException::new);
    }
}
