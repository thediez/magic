package com.timejet.bio.timejet.utils

import android.content.Context
import android.content.DialogInterface
import android.os.AsyncTask
import com.timejet.bio.timejet.Mail
import com.timejet.bio.timejet.R
import com.timejet.bio.timejet.repository.databases.localDB.showToast
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException

class SendEmailAsyncTask : AsyncTask<Context, Void, Boolean>() {
    var m: Mail? = null
    var activity: DialogInterface.OnClickListener? = null

    override fun doInBackground(vararg params: Context): Boolean? {
        val context = params[0]
        try {
            if (m!!.send()) {
                showToast(context.getString(R.string.email_sent), context)
            } else {
                showToast(context.getString(R.string.email_failed_to_send), context)
            }
            return true
        } catch (e: AuthenticationFailedException) {
            showToast(context.getString(R.string.auth_failed), context)
            return false
        } catch (e: MessagingException) {
            showToast(context.getString(R.string.email_failed_to_send), context)
            return false
        } catch (e: Exception) {
            showToast(context.getString(R.string.email_unexpected_error), context)
            return false
        }
    }
}