package app.financas.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.financas.FinancasApp
import app.financas.MainActivity
import app.financas.R
import app.financas.domain.Money
import app.financas.domain.billsFor
import app.financas.domain.entriesIn
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit

object Reminders {
    private const val WORK = "lembretes"
    const val CHANNEL = "lembretes"

    /** Uma verificação por dia, às 21h. */
    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(21, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Lembretes", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun notify(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as FinancasApp).repository
        val s = repo.settingsNow()
        if (!s.onboarded) return Result.success()
        val today = LocalDate.now()
        val entries = repo.exportEntries()

        if (s.remindDaily && entries.none { it.epochDay == today.toEpochDay() }) {
            Reminders.notify(applicationContext, 1, "Nada lançado hoje", "Leva 5 segundos: valor, área, pronto.")
        }
        if (s.remindBills) {
            val tomorrow = today.plusDays(1)
            val month = YearMonth.from(tomorrow)
            val due = billsFor(month, repo.billsList(), entriesIn(entries, month))
                .filter { !it.paid && it.bill.active && it.due == tomorrow }
            if (due.isNotEmpty()) {
                val title = if (due.size == 1) "${due[0].bill.name} vence amanhã" else "${due.size} contas vencem amanhã"
                Reminders.notify(applicationContext, 2, title, Money.brl(due.sumOf { it.bill.amountCents }))
            }
        }
        return Result.success()
    }
}
