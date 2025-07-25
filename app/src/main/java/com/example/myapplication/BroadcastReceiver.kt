package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) {
            Log.e("BootCompletedReceiver", "Context is null, cannot start service.")
            return
        }
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootCompletedReceiver", "Boot completed event received.")
            val serviceIntent = Intent(context, PedometerService::class.java)
            try{
                context.startForegroundService(serviceIntent)
            }
            catch (e: Exception)
            {
                Log.e("BootCompletedReceiver", "Error starting service: ${e.message}", e)
            }
        }

    }
}