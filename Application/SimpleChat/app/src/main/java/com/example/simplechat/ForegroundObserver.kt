package com.example.simplechat

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle

class ForegroundObserver : Application() {

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                if (startedActivities == 0) {
                    // App has entered foreground
                    GlobalVars.isInForeground = true
                }
                startedActivities++
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities == 0) {
                    // App has entered background
                    GlobalVars.isInForeground = false
                    GlobalVars.context.stopService(Intent(GlobalVars.context, KeepAliveService::class.java))
                }
            }

            // The rest can be left empty
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}