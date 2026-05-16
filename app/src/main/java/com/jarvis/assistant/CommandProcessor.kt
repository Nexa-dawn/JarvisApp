package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import java.util.Calendar
import java.util.regex.Pattern
import android.app.AlarmManager
import android.app.PendingIntent

class CommandProcessor(private val ctx: Context, private val api: JarvisApi) {

    suspend fun process(input: String): String {
        val response = api.chat(input)
        val pattern = Pattern.compile("\\[KOMUT:([^:]+):([^\\]]+)\\]")
        val matcher = pattern.matcher(response)
        if (matcher.find()) {
            val type = matcher.group(1) ?: ""
            val param = matcher.group(2) ?: ""
            execute(type, param)
        }
        return response
    }

    private fun execute(type: String, param: String) {
        when (type.uppercase()) {
            "ALARM" -> setAlarm(param)
            "ARA" -> callContact(param)
            "MESAJ" -> {
                val parts = param.split(":")
                if (parts.size >= 2) sendSms(parts[0], parts.drop(1).joinToString(":"))
            }
            "MUZIK" -> controlMusic(param)
            "SES" -> controlVolume(param)
            "UYGULAMA" -> openApp(param)
        }
    }

    private fun setAlarm(time: String) {
        try {
            val parts = time.split(":")
            val hour = parts[0].toInt()
            val min = if (parts.size > 1) parts[1].toInt() else 0
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
            }
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(ctx, System.currentTimeMillis().toInt(),
                Intent(ctx, AlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun callContact(name: String) {
        val number = findNumber(name) ?: name
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.startActivity(intent)
    }

    private fun findNumber(name: String): String? {
        val cursor = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"), null
        )
        cursor?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun sendSms(contact: String, message: String) {
        val number = findNumber(contact) ?: contact
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$number")
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.startActivity(intent)
    }

    private fun openApp(pkg: String) {
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(this)
        }
    }

    private fun controlMusic(action: String) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val key = when (action.uppercase()) {
            "OYNAT" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY
            "DURDUR" -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            "ILERI" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            "GERI" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return
        }
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key))
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key))
    }

    private fun controlVolume(action: String) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (action.uppercase()) {
            "ARTIR" -> am.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "AZALT" -> am.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        }
    }
}
