package com.piercingxx.nopemode

import android.app.Application
import com.piercingxx.nopemode.log.AppLog

class NopeModeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        AppLog.installFieldDiagnostics(this)
    }
}
