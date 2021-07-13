package com.example.timejet.repository.models;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class User_DB extends RealmObject {
    public static final String REALM_USER_DB_NAME = "User_DB";
    public static final String REALM_USER_DB_USER_PASSWORD = "userPassword";

    // primaryKey, unique task ID = 1, 2, 3, 4... etc
    @PrimaryKey
    private long ID;
    private String userName = "";
    private String userEmail = "";
    private String phoneNumber = "";
    private String groupName = "";
    private String domainName = "";
    private String userPassword = "";

    public long getID() {
        return ID;
    }

    public void setID(long ID) {
        this.ID = ID;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }
}
