package com.example.myapplication

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import androidx.core.content.edit
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale


const val STEP_COUNT_NOTIFICATION_ID = 1

class PedometerViewModel(application: Application) : AndroidViewModel(application) {

    @SuppressLint("StaticFieldLeak") // I am getting the MainActivity context from the application context
    private val _context = application.applicationContext

    private val _uiTodaySteps = MutableStateFlow(0L)
    val uiTodaySteps: StateFlow<Long> = _uiTodaySteps.asStateFlow()

    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    @SuppressLint("StaticFieldLeak")
    private var pedometerService: PedometerService? = null
    private var bound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PedometerService.LocalBinder
            pedometerService = binder.getService()
            bound = true
            _isServiceBound.value = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
            pedometerService = null
            _isServiceBound.value = false
            Log.d("PedometerViewModel", "Service disconnected")
        }
    }

    private val _hasActivityRecognitionPermission = MutableStateFlow(checkActivityRecognitionPermission())
    val hasActivityRecognitionPermission: StateFlow<Boolean> = _hasActivityRecognitionPermission.asStateFlow()
    init {
        checkAndStartPedometerService()
    }

    fun checkAndStartPedometerService(){
        if (checkActivityRecognitionPermission())
        {
            _hasActivityRecognitionPermission.value = true
            Intent(_context, PedometerService::class.java).also { intent ->
                _context.startForegroundService(intent) }
            bindToService()
            }
        else {
            _hasActivityRecognitionPermission.value = false
            Log.d("PedometerViewModel", "Activity recognition permission not granted.")
        }
    }

    private fun checkActivityRecognitionPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            _context,
            android.Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun bindToService() {
        if (!bound) {
            Intent(_context, PedometerService::class.java).also { intent ->
                _context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }
        }
    }

    fun unbindFromService() {
        if (bound) {
            _context.unbindService(serviceConnection)
            pedometerService = null // **CRUCIAL: Clear the reference here**
            bound = false
            _isServiceBound.value = false
            Log.d("PedometerViewModel", "Service unbound and reference cleared.")
        }
    }

    fun updateActivityRecognitionPermissionState(granted: Boolean) {
        _hasActivityRecognitionPermission.value = granted
        if (granted) {
            checkAndStartPedometerService()
        } else {
            // Optionally stop service if permission is revoked while running,
            // though the service itself should also handle this.
            // stopPedometerService()
        }
    }

    override fun onCleared() {
        super.onCleared()
        unbindFromService()
    }



}