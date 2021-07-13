package com.example.timejet.repository.models;

import java.util.Date;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class UserActivity_DB extends RealmObject {
    public static final String TASK_UID_FIELD = "taskUID"; // unique task ID
    public static final String ID_FIELD = "ID"; // local id of events
    public static final String EVENT_NAME_FIELD = "eventName";
    public static final String PROJECT_NAME = "projectName";
    public static final String DATE_TIME = "eventDateTime";
    public static final String USER_ASSIGNED = "userName";
    public static final String DATE_DATE = "dateTime";

    @PrimaryKey
    private long ID;
    private String userName;
    private String projectName;
    private String taskName;
    private String stepName;
    private String eventDateTime;
    private String eventName; // ie startPTS_event, phonecall_event, e-mail_event <- это тоже таски, просто (таск-ид старт-пауза-стоп)
    //
    private long taskUID;
    private long eventDuration;
    // текущее время со старта UNIX 1 янв 1970 utc0 в мс
    private long curTimeMillis = 0;
    private Date dateTime;

    public UserActivity_DB() {
    }

    public UserActivity_DB(String userName, String projectName, String stepName, long eventDuration, Date dateTime) {
        this.userName = userName;
        this.projectName = projectName;
        this.stepName = stepName;
        this.eventDuration = eventDuration;
        this.dateTime = dateTime;
    }

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

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public String getEventDateTime() {
        return eventDateTime;
    }

    public void setEventDateTime(String eventDateTime) {
        this.eventDateTime = eventDateTime;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public long getTaskUID() {
        return taskUID;
    }

    public void setTaskUID(long taskID) {
        this.taskUID = taskID;
    }

    public long getEventDuration() {
        return eventDuration;
    }

    public void setEventDuration(long eventDuration) {
        this.eventDuration = eventDuration;
    }

    public long getCurTimeMillis() {
        return curTimeMillis;
    }

    public void setCurTimeMillis(long curTimeMillis) {
        this.curTimeMillis = curTimeMillis;
    }

    public Date getDateTime(){
        return dateTime;
    }

    public void setDateTime(Date dateTime){
        this.dateTime = dateTime;
    }
}
