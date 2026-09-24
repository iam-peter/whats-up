package app.whatsup.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Runs when the calendar or contacts provider changes (content URI
 * trigger, debounced by WorkManager) and re-arms itself (FR-U1).
 */
class ContentChangeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.refreshAll(applicationContext)
        UpdateScheduler.observeContent(applicationContext, rearm = true)
        return Result.success()
    }
}
