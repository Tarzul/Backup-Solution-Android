package com.rezerv.upload.navigation

import android.app.Activity
import android.content.Intent
import com.rezerv.upload.HistoryDetailsActivity
import com.rezerv.upload.TaskDetailsActivity
import com.rezerv.upload.TaskWizardActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationManager @Inject constructor() {

    fun openTaskDetails(activity: Activity, taskId: String) {
        val intent = Intent(activity, TaskDetailsActivity::class.java).apply {
            putExtra("taskId", taskId)
        }
        activity.startActivity(intent)
    }

    fun openCreateTask(activity: Activity) {
        val intent = Intent(activity, TaskWizardActivity::class.java)
        activity.startActivity(intent)
    }

    fun openHistoryDetails(activity: Activity, time: Long) {
        val intent = Intent(activity, HistoryDetailsActivity::class.java).apply {
            putExtra("time", time)
        }
        activity.startActivity(intent)
    }
}