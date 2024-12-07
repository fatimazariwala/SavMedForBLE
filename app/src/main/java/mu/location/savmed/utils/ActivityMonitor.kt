package mu.location.savmed.utils

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import androidx.annotation.UiThread
import mu.location.savmed.SavMed.Companion.coreContext
import org.linphone.core.tools.Log
import org.linphone.core.tools.service.AndroidDispatcher

@UiThread
class ActivityMonitor : ActivityLifecycleCallbacks {
    companion object {
        private const val TAG = "[Activity Monitor]"
    }

    private val activities = ArrayList<Activity>()
    private var mActive = false
    private var mRunningActivities = 0
    private var mLastChecker: InactivityChecker? = null

    @Synchronized
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.d("$TAG onActivityCreated [$activity]")
        if (!activities.contains(activity)) activities.add(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        Log.d("$TAG onActivityStarted [$activity]")
    }

    @Synchronized
    override fun onActivityResumed(activity: Activity) {
        Log.d("$TAG onActivityResumed [$activity]")
        if (!activities.contains(activity)) {
            activities.add(activity)
        }
        mRunningActivities++
        checkActivity()
    }

    @Synchronized
    override fun onActivityPaused(activity: Activity) {
        Log.d("$TAG onActivityPaused [$activity]")
        if (!activities.contains(activity)) {
            activities.add(activity)
        } else {
            mRunningActivities--
            checkActivity()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        Log.d("$TAG onActivityStopped [$activity]")
    }

    @Synchronized
    override fun onActivityDestroyed(activity: Activity) {
        Log.d("$TAG onActivityDestroyed [$activity]")
        activities.remove(activity)
    }

    private fun startInactivityChecker() {
        if (mLastChecker != null) mLastChecker!!.cancel()
        AndroidDispatcher.dispatchOnUIThreadAfter(
            InactivityChecker().also { mLastChecker = it },
            2000
        )
    }

    private fun checkActivity() {
        if (mRunningActivities == 0) {
            if (mActive) startInactivityChecker()
        } else if (mRunningActivities > 0) {
            if (!mActive) {
                mActive = true
                onForegroundMode()
            }
            if (mLastChecker != null) {
                mLastChecker!!.cancel()
                mLastChecker = null
            }
        }
    }

    private fun onBackgroundMode() {
        Log.i("$TAG onBackgroundMode()")
        //coreContext.onBackground()
    }

    private fun onForegroundMode() {
        Log.i("$TAG onForegroundMode()")
        //coreContext.onForeground()
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    internal inner class InactivityChecker : Runnable {
        private var isCanceled = false
        fun cancel() {
            isCanceled = true
        }

        override fun run() {
            if (!isCanceled) {
                if (mRunningActivities == 0 && mActive) {
                    mActive = false
                    onBackgroundMode()
                }
            }
        }
    }
}
