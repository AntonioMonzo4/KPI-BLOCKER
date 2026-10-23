package com.kpiblocker.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kpiblocker.app.block.BlockService
import com.kpiblocker.app.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.serviceEnabled) return
        BlockService.start(context)
    }
}
