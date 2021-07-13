package com.example.timejet.repository.models;

import com.timejet.bio.timejet.repository.RealmHandler;

import java.io.Serializable;


public class FirestorePTS_DB implements Serializable {

    public final static String FB_UID = "uid";
    public final static String FB_IS_COMPLETED = "completed";
    public final static String FB_PROJECT_NAME = "projectName";
    public final static String FB_TASK_NAME = "taskName";
    public final static String FB_USERS_ASSIGNED = "usersAssigned";
    public final static String FB_USER_WORKING = "userWorking";
    public final static String FB_TIME_PROGRESS = "timeProgress";
    public final static String FB_TASK_NOTE = "taskNote";
    public final static String FB_ACTUAL_START = "actualStart";
    public final static String FB_ACTUAL_FINISH = "actualFinish";
    public final static String FB_ADDITIONAL_TIME = "additionalTime";
    public final static String FB_IS_READ = "read";
    public final static String FB_REMAINING_TIME = "remainingTime";

    // UID
    private long uid;
    // isCompleted
    private boolean isCompleted;
    // project Name
    private String projectName;
    // Task Name
    private String taskName;
    // Step Name
    private String stepName;
    // записываем ответственных юзеров, метод возвращает "Имя, Емейл" в userPTS
    private String usersAssigned;
    // записываем кто работает над заданием
    private String userWorking;
    //PTS_time_progress
    private Double timeProgress;
    // PTS Task Note
    private String taskNote;
    // PTS Actual Start
    private String actualStart;
    // PTS Actual Finish
    private String actualFinish;
    // PTS Additional Time
    private Double additionalTime;
    // PTS Read status
    private boolean isRead;

    public FirestorePTS_DB() {
    }

    public FirestorePTS_DB(long uid, String projectName, String userEmail) {
        PTS_DB ptsDb = RealmHandler.Companion.getInstance().getPTSbyUIDProjectNameUserNameComaEmail(uid, projectName, userEmail);
        if (ptsDb == null) return;

        this.uid = ptsDb.getUID();
        this.isCompleted = ptsDb.isPTScompleted();
        this.usersAssigned = ptsDb.getUsersAssigned();
        this.userWorking = ptsDb.getTaskWorkingUsername();
        this.timeProgress = ptsDb.getPTSprogress();
        this.taskNote = ptsDb.getTaskNote();
        this.actualStart = ptsDb.getTaskStartDateTime();
        this.actualFinish = ptsDb.getTaskFinishDateTime();
        this.additionalTime = ptsDb.getTaskAdditionalTime();
        this.projectName = ptsDb.getProjectName();
        this.taskName = ptsDb.getTaskName();
        this.stepName = ptsDb.getStepName();
        this.isRead = ptsDb.isRead();
    }

    public long getUid() {
        return uid;
    }

    public void setUid(long uid) {
        this.uid = uid;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
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

    public String getUsersAssigned() {
        return usersAssigned;
    }

    public void setUsersAssigned(String usersAssigned) {
        this.usersAssigned = usersAssigned;
    }

    public Double getTimeProgress() {
        return timeProgress;
    }

    public void setTimeProgress(Double timeProgress) {
        this.timeProgress = timeProgress;
    }

    public String getTaskNote() {
        return taskNote;
    }

    public void setTaskNote(String taskNote) {
        this.taskNote = taskNote;
    }

    public String getActualStart() {
        return actualStart;
    }

    public void setActualStart(String actualStart) {
        this.actualStart = actualStart;
    }

    public String getActualFinish() {
        return actualFinish;
    }

    public void setActualFinish(String actualFinish) {
        this.actualFinish = actualFinish;
    }

    public Double getAdditionalTime() {
        return additionalTime;
    }

    public void setAdditionalTime(Double additionalTime) {
        this.additionalTime = additionalTime;
    }

    public String getUserWorking() {
        return userWorking;
    }

    public void setUserWorking(String userWorking) {
        this.userWorking = userWorking;
    }

    public boolean getRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }
}
