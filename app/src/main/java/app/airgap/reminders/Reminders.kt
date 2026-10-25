package app.airgap.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.airgap.MainActivity
import app.airgap.R
import app.airgap.vault.Card
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** Keeps the reminder list in step with the vault and shows due reminders. */
object Reminders {
    private const val CHANNEL = "reminders"
    private val CHECK_TIME: LocalTime = LocalTime.of(9, 0)
    private const val CHECK_WINDOW_MS = 2 * 60 * 60 * 1000L

    /** Called after every vault save or unlock, with the full card list. */
    fun sync(context: Context, cards: List<Card>) {
        ReminderStore(context).write(cards.map(ReminderCard::from))
        schedule(context)
        check(context)
    }

    fun clear(context: Context) {
        ReminderStore(context).clear()
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
    }

    /**
     * The next daily check, between 9 and 11 in the morning. A one-off alarm with a
     * 2-hour window needs no special permission; each check schedules the next one.
     * (A repeating inexact alarm may drift by most of a day, too late for "due today".)
     */
    fun schedule(context: Context) {
        val now = ZonedDateTime.now()
        var next = now.with(CHECK_TIME)
        if (!next.isAfter(now)) next = next.plusDays(1)
        context.getSystemService(AlarmManager::class.java).setWindow(
            AlarmManager.RTC,
            next.toInstant().toEpochMilli(),
            CHECK_WINDOW_MS,
            alarmIntent(context),
        )
    }

    /** Shows every reminder that's due and hasn't been shown yet. */
    fun check(context: Context) {
        val store = ReminderStore(context)
        val due = ReminderRules.due(store.read(), LocalDate.now()).filterNot { store.wasSent(it.key) }
        // Without permission, leave them unmarked so they show once it's granted.
        if (due.isEmpty() || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        due.forEach { post(context, it) }
        store.markSent(due.map { it.key })
    }

    private fun post(context: Context, reminder: Reminder) {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // On the lock screen: no card name, just that there's a reminder.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Airgap")
            .setContentText("You have a card reminder")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(reminder.key.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission withdrawn between the check and the post; nothing to do.
        }
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Bill and expiry reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Credit card due dates and cards about to expire"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** The daily alarm. Not exported: only Airgap's own alarm can trigger it. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.schedule(context)
        Reminders.check(context)
    }
}

/** Alarms don't survive a restart or an app update, so set the daily one again. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminders.schedule(context)
            Reminders.check(context)
        }
    }
}
