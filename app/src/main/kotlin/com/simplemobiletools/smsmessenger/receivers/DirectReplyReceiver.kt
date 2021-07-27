package com.simplemobiletools.smsmessenger.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.simplemobiletools.commons.extensions.notificationManager
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.conversationsDB
import com.simplemobiletools.smsmessenger.extensions.markThreadMessagesRead
import com.simplemobiletools.smsmessenger.helpers.*

class DirectReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(THREAD_NUMBER)
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val msg = RemoteInput.getResultsFromIntent(intent).getCharSequence(REPLY)?.toString() ?: return

        /*
        val notificationChannelId = intent.getStringExtra(NOTIFICATION_CHANNEL_ID) ?: NOTIFICATION_CHANNEL_REPLIES
        */

        val settings = Settings()
        settings.useSystemSending = true
        settings.deliveryReports = true

        val transaction = Transaction(context, settings)
        val message = com.klinker.android.send_message.Message(msg, address)

        try {
            val smsSentIntent = Intent(context, SmsStatusSentReceiver::class.java)
            val deliveredIntent = Intent(context, SmsStatusDeliveredReceiver::class.java)

            transaction.setExplicitBroadcastForSentSms(smsSentIntent)
            transaction.setExplicitBroadcastForDeliveredSms(deliveredIntent)

            transaction.sendNewMessage(message, threadId)
        } catch (e: Exception) {
            context.showErrorToast(e)
        }

        /*
        val repliedNotification = NotificationCompat.Builder(context, notificationChannelId)
            .setSmallIcon(R.drawable.ic_messenger)
            .setContentText(msg)
            .build()
        context.notificationManager.notify(threadId.hashCode(), repliedNotification)
        */

        context.notificationManager.cancel(threadId.hashCode())

        ensureBackgroundThread {
            context.markThreadMessagesRead(threadId)
            context.conversationsDB.markRead(threadId)
        }
    }
}
