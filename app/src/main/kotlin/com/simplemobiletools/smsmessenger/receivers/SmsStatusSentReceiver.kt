package com.simplemobiletools.smsmessenger.receivers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.klinker.android.send_message.SentReceiver
import com.simplemobiletools.commons.extensions.getAdjustedPrimaryColor
import com.simplemobiletools.commons.extensions.getMyContactsCursor
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.ThreadActivity
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.NOTIFICATION_CHANNEL_REPLIES
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

class SmsStatusSentReceiver : SentReceiver() {

    override fun onMessageStatusUpdated(context: Context, intent: Intent, receiverResultCode: Int) {
        if (intent.extras?.containsKey("message_uri") == true) {
            val uri = Uri.parse(intent.getStringExtra("message_uri"))
            val messageId = uri?.lastPathSegment?.toLong() ?: 0L
            ensureBackgroundThread {
                val type = if (intent.extras!!.containsKey("errorCode")) {
                    showSendingFailedNotification(context, messageId)
                    Telephony.Sms.MESSAGE_TYPE_FAILED
                } else {
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX
                }

                context.updateMessageType(messageId, type)
                context.messagesDB.updateType(messageId, type)
                refreshMessages()
            }
        }
    }

    private fun showSendingFailedNotification(context: Context, messageId: Long) {
        Handler(Looper.getMainLooper()).post {
            val privateCursor = context.getMyContactsCursor(false, true)?.loadInBackground()
            ensureBackgroundThread {
                val address = context.getMessageRecipientAddress(messageId)
                val threadId = context.getThreadId(address)
                val senderName = context.getNameFromAddress(address, privateCursor)
                showNotification(context, senderName, threadId)
            }
        }
    }

    @SuppressLint("NewApi")
    private fun showNotification(context: Context, recipientName: String, threadId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val intent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }

        val pendingIntent = PendingIntent.getActivity(context, threadId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT)
        val summaryText = String.format(context.getString(R.string.message_sending_error), recipientName)

        val largeIcon = SimpleContactsHelper(context).getContactLetterIcon(recipientName)
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_REPLIES)
            .setContentTitle(context.getString(R.string.message_not_sent_short))
            .setContentText(summaryText)
            .setColor(context.getAdjustedPrimaryColor())
            .setSmallIcon(R.drawable.ic_messenger)
            .setLargeIcon(largeIcon)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setSound(soundUri, AudioManager.STREAM_NOTIFICATION)
            .setChannelId(NOTIFICATION_CHANNEL_REPLIES)

        notificationManager.notify(threadId.hashCode(), builder.build())
    }
}
