package com.chiiii5640.thsrapp.core.logging

import android.util.Log

object ThsrLog {
    fun i(message: String) {
        runCatching { Log.i("THSRApp", message) }
    }

    fun w(message: String) {
        runCatching { Log.w("THSRApp", message) }
    }

    fun e(message: String, throwable: Throwable? = null) {
        runCatching {
            if (throwable != null) {
                Log.e("THSRApp", message, throwable)
            } else {
                Log.e("THSRApp", message)
            }
        }
    }
}
