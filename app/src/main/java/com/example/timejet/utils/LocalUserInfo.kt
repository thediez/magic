package com.timejet.bio.timejet.utils

import android.content.Context

class LocalUserInfo private constructor(appContext: Context) {
        val userName = Utils.getUserName(appContext)
        val userEmail = Utils.getUserEmail(appContext)
        val userGroup = Utils.getUserGroup(appContext)
        val userDomain = Utils.getProjectID(appContext)
        val confingFilesUrl = Utils.getConfigFilesUrl(appContext)

    companion object {
        @Volatile
        private var instance: LocalUserInfo? = null

        fun getUserName(appContext: Context): String? = getInstance(appContext).userName

        fun getUserEmail(appContext: Context): String? = getInstance(appContext).userEmail?.toLowerCase()

        fun getUserGroup(appContext: Context): String? = getInstance(appContext).userGroup

        fun getUserDomain(appContext: Context): String? = getInstance(appContext).userDomain

        fun getConfigFilesUrl(appContext: Context): String? = getInstance(appContext).confingFilesUrl

        fun getInstance(appContext: Context): LocalUserInfo {
            if (instance == null) {
                synchronized(LocalUserInfo::class.java) {
                    if (instance == null) {
                        instance = LocalUserInfo(appContext)
                    }
                }
            }
            return instance as LocalUserInfo
        }

        fun reset() { instance = null}
    }
}
