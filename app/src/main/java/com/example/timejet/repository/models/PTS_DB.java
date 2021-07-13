package com.example.timejet.repository.models;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

import java.io.Serializable;
import java.util.Date;
import java.util.List;



public class PTS_DB extends RealmObject implements Serializable {
    // внутренние поля БД
    public static final String REALM_TASK_UID = "UID"; // unique ID field
    public static final String REALM_TASK_ID = "ID"; // unique ID field
    public static final String REALM_PROJECT_NAME = "projectName"; // project name field
    public static final String REALM_TASK_USERS_ASSIGNED = "usersAssigned";
    public static final String REALM_TASK_WORKING_USERNAME = "taskWorkingUsername";
    public static final String REALM_TASK_COMPLETED = "taskCompleted";
    public static final String REALM_PROGRESS_TIME = "pts_progress";
    public static final String REALM_TASK_NOTE = "taskNote";
    public static final String REALM_ACTUAL_START_DATETIME = "taskStartDateTime";
    public static final String REALM_ACTUAL_FINISH_DATETIME = "taskFinishDateTime";
    public static final String REALM_REMAINING_TIME = "taskRemainingTime";
    public static final String REALM_ADDITIONAL_TIME = "taskAdditionalTime";
    public static final String REALM_IS_MILESTONE = "isMilestone";
    public static final String IS_READ = "isRead";
    public static final String IS_CHECK_OVER_BUGET_IS_SHOWN = "checkOverBugetIsShown";
    public static final String IS_NOTIFICATION_SHOWED = "isNotificationShowen";
    public static final String REALM_STEP_NAME = "stepName";
    public static final String REALM_TASK_NAME = "taskName";
    public static final String REALM_PROJECT_DEADLINE = "taskDeadline";


    public static final String REALM_PTS_DB_NAME = "PTS_DB";

    // переделать в енам
    // идентификаторы UID для Звонков/Емейлов/Встреч
    public final static long PTS_PHONECALL_UID = -100501L;
    public final static long PTS_EMAIL_UID = -100502L;
    public final static long PTS_MEETING_UID = -100503L;
    public final static long PTS_TRAVEL_UID = -100504L;

    // имена событий в журнале/БД
    public static final String START_EVENT = "start";
    public static final String PAUSE_EVENT = "pause";
    public static final String END_EVENT = "end";
    public static final String PHONECALL_EVENT = "phonecall";
    public static final String EMAIL_EVENT = "email";
    public static final String MEETING_EVENT = "meeting";
    public static final String TRAVEL_EVENT = "travel";
    @PrimaryKey
    private long ID; // таких идентификаторов точно не будет в проекте
    // unique task id inside one project file
    @Index
    private long UID;
    // дата-время старта задания
    private String taskStartDateTime = "";
    // дата-время окончания задания
    private String taskFinishDateTime = "";
    // сколько времени осталось до конца задания
//    private String taskRemainingTime = "";
    // уровень вложенности, каталог или нет
    private Integer outlineLevel;
    // Имя проекта
    @Required
    private String projectName = "";
    // имя Таска, в иерархии Project - Task - Step
    private String taskName = "";
    // имя шага, Step
    @Required
    private String stepName = "";
    // имя ответственного исполнителя на Таск, задается проект менеджером
    // юзеров assigned может быть много, working Только ОДИН
    private String usersAssigned = "";
    // юзер, который уже работает над этим заданием
    private String taskWorkingUsername = "";
    // return TRUE if the current task is completed more than maxThreshold ( taskPercentComplete >= taskHighThreshold ),
    // else FALSE
    private boolean taskCompleted = false;
    // list of parent id's, 0 or null if no parents, 0, 1 or 2, 3
    private RealmList<Long> taskParentsIDlist = new RealmList<>();
    // Бюджет времени задания
    private Double timeBudget = null;
    // Прогресс задания
//    @Required
    private Double pts_progress = null;// net sync
    // Дата дедлайна таска
    private Date taskDeadline = null;
    // каталог или нет
    private boolean rollup = false;
    // небольшая заметка к заданию от юзера на устройстве -> PM
    private String taskNote = "";
    // добавочное время что запросил юзер
    private Double taskAdditionalTime = null;
    // это задание Milestone ?
    private boolean isMilestone = false;
    private boolean isActive = false;

    //Task read status
    private boolean isRead = false;

    private boolean checkOverBugetIsShown = false;

    private boolean isNotificationShowen = false;

    public PTS_DB() {
    }

    public long getID() {
        return ID;
    }

    public void setID(long ID) {
        this.ID = ID;
    }

    public long getUID() {
        return UID;
    }

    public void setUID(long UID) {
        this.UID = UID;
    }

    public String getTaskStartDateTime() {
        return taskStartDateTime;
    }

    public void setTaskStartDateTime(String taskStartDateTime) {
        this.taskStartDateTime = taskStartDateTime;
    }

    public String getTaskFinishDateTime() {
        return taskFinishDateTime;
    }

    public void setTaskFinishDateTime(String taskFinishDateTime) {
        this.taskFinishDateTime = taskFinishDateTime;
    }

    public Double getTaskRemainingTime() {

        if(taskCompleted) return 0.0;

        Double taskRemainingTime = null;

        if (this.timeBudget != null && this.pts_progress != null) {
            Double allTimeBudget = this.timeBudget;

            if (this.taskAdditionalTime != null) allTimeBudget += taskAdditionalTime;

            taskRemainingTime = allTimeBudget - this.pts_progress;

        }

        return taskRemainingTime;
    }


    public int getOutlineLevel() {
        return outlineLevel;
    }

    public void setOutlineLevel(int outlineLevel) {
        this.outlineLevel = outlineLevel;
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

    public String getTaskWorkingUsername() {
        return taskWorkingUsername;
    }

    public void setTaskWorkingUsername(String taskWorkingUsername) {
        this.taskWorkingUsername = taskWorkingUsername;
    }

    public boolean isPTScompleted() {
        return taskCompleted;
    }

    public void setPTScompleted(boolean completedStatus) {
        taskCompleted = completedStatus;
    }

    public RealmList<Long> getPredecessorsIDlist() {
        return taskParentsIDlist;
    }

    public void setPredecessorsUIDlist(List<Long> _taskParentsIDlist) {
        taskParentsIDlist.addAll(_taskParentsIDlist);
    }

    public Double getTimeBudget() {
        return timeBudget;
    }

    public void setTimeBudget(Double timeBudget) {
        this.timeBudget = timeBudget;
    }

    public Double getPTSprogress() {
        return pts_progress;
    }

    public void setPTSProgress(Double taskTimeProgress) {
        this.pts_progress = taskTimeProgress;
    }

    public Date getTaskDeadline() {
        return taskDeadline;
    }

    public void setTaskDeadline(Date taskDeadline) {
        this.taskDeadline = taskDeadline;
    }

    public boolean isRollup() {
        return rollup;
    }

    public void setRollup(boolean rollup) {
        this.rollup = rollup;
    }

    public String getTaskNote() {
        return taskNote;
    }

    public void setTaskNote(String taskNote) {
        this.taskNote = taskNote;
    }

    public Double getTaskAdditionalTime() {
        return taskAdditionalTime;
    }

    public void setTaskAdditionalTime(Double taskAdditionalTime) {
        this.taskAdditionalTime = taskAdditionalTime;
    }

    public boolean isMilestone() {
        return isMilestone;
    }

    public void setMilestone(boolean milestone) {
        isMilestone = milestone;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public boolean isCheckOverBugetIsShown() {
        return checkOverBugetIsShown;
    }

    public void setCheckOverBugetIsShown(boolean checkOverBugetIsShown) {
        this.checkOverBugetIsShown = checkOverBugetIsShown;
    }

    public boolean isNotificationShowen() {
        return isNotificationShowen;
    }

    public void setNotificationShowen(boolean notificationShowen) {
        isNotificationShowen = notificationShowen;
    }
}
