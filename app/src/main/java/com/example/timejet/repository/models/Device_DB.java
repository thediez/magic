package com.example.timejet.repository.models;

import android.os.Build;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class Device_DB extends RealmObject {
    @PrimaryKey
    long ID;

    private String deviceModel;
    private String deviceManufacturer;
    private String deviceProduct;
    private String deviceBrand;
    private String deviceDevice;
    private String deviceDisplay;
    private String deviceSerial;
    private String deviceUser;
    private String taskLoggedDeviceName;

    public Device_DB() {
        ID = 0;

        deviceModel = Build.MODEL;
        deviceManufacturer = Build.MANUFACTURER;
        deviceProduct = Build.PRODUCT;
        deviceBrand = Build.BRAND;
        deviceDevice = Build.DEVICE;
        deviceDisplay = Build.DISPLAY;
        deviceSerial = Build.SERIAL;
        deviceUser = Build.USER;
    }

    public long getID() {
        return ID;
    }

    public void setID(long ID) {
        this.ID = ID;
    }

    String getTaskLoggedDeviceName() {
        return taskLoggedDeviceName;
    }

    void setTaskLoggedDeviceName(String taskLoggedDeviceName) {
        this.taskLoggedDeviceName = taskLoggedDeviceName;
    }

}
