package com.simplemobiletools.smsmessenger.receivers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.klinker.android.send_message.DeliveredReceiver
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.extensions.messagesDB
import com.simplemobiletools.smsmessenger.extensions.updateMessageType
import com.simplemobiletools.smsmessenger.helpers.refreshMessages

class SmsStatusDeliveredReceiver : DeliveredReceiver() {

    private fun updateMessageDB(context: Context, messageId: Long, type: Int, retry: Int) {
        ensureBackgroundThread {
            val updated = context.messagesDB.updateType(messageId, type)
            if (updated == 0) {
                if (retry > 0) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        updateMessageDB(context, messageId, type, retry - 1);
                    }, 250)
                }
            } else {
                Handler(Looper.getMainLooper()).post {
                    refreshMessages()
                }
            }
        }
    }

    override fun onMessageStatusUpdated(context: Context, intent: Intent, receiverResultCode: Int) {
        if (intent.extras?.containsKey("message_uri") == true) {
            val uri = Uri.parse(intent.getStringExtra("message_uri"))
            val messageId = uri?.lastPathSegment?.toLong() ?: 0L
            ensureBackgroundThread {
                val type = Telephony.Sms.MESSAGE_TYPE_SENT
                context.updateMessageType(messageId, type)
                updateMessageDB(context, messageId, type, 10)
            }
        }
    }
}
