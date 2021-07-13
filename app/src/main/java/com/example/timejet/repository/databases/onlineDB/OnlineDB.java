package com.example.timejet.repository.databases.onlineDB;

public interface OnlineDB {
    boolean attemptToInitCloudIsSuccess(String loginEmail, String loginPassword) throws Exception;
}
