package com.radamski.networkmonitor.Utils;

import java.util.Arrays;

public enum Manager {

    ALARM_MANAGER(0), WORK_MANAGER(1), SERVICE(2);
    public final int id;
    Manager(int id) {
        this.id = id;
    }

    public static Manager getById(String idString) {
        int id = Integer.parseInt(idString);
        return Arrays.stream(Manager.values()).filter(manager -> manager.id == id).findAny().orElseThrow(RuntimeException::new);
    }
}
