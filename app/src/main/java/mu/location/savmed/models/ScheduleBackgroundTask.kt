package mu.location.savmed.models

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import mu.location.savmed.SavMed.Companion.corePreferences

class ScheduleBackgroundTask(
    context: Context,
    workerParams: WorkerParameters
): CoroutineWorker(context,workerParams) {

    override suspend fun doWork(): Result {
        Log.i("Work Manager","in scheduler.....")
        corePreferences.keepServiceAlive = true
        return Result.success()
    }

}