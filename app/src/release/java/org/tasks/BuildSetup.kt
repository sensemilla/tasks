package org.tasks

import android.annotation.SuppressLint
import android.util.Log
import timber.log.Timber
import javax.inject.Inject

class BuildSetup @Inject constructor() {
    fun setup() = Timber.plant(ErrorReportingTree())

    private class ErrorReportingTree : Timber.Tree() {
        @SuppressLint("LogNotTimber")
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority < Log.WARN) {
                return
            }
            if (priority == Log.ERROR) {
                if (t == null) {
                    Log.e(tag, message)
                } else {
                    Log.e(tag, message, t)
                }
            } else if (priority == Log.WARN) {
                if (t == null) {
                    Log.w(tag, message)
                } else {
                    Log.w(tag, message, t)
                }
            }
        }
    }
}