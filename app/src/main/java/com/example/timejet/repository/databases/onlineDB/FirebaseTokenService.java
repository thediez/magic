package com.example.timejet.repository.databases.onlineDB;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.timejet.bio.timejet.utils.Utils;

import org.jetbrains.annotations.NotNull;

public class FirebaseTokenService extends FirebaseMessagingService {
    @Override
    public void onNewToken(@NotNull String newToken) {
        super.onNewToken(newToken);
        Utils.Companion.saveTokenFirebase(getApplicationContext(), newToken);
    }
}
