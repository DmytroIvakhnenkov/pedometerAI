package com.example.myapplication

import android.app.Application
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PedometerService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var totalStepsFromSensorSinceBoot = -1L
    private var currentDaySteps = 0L
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var lastKnownDateString: String

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): PedometerService = this@PedometerService
    }

    private val _serviceSteps = MutableStateFlow(0L)
    val serviceSteps: StateFlow<Long> = _serviceSteps.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        sharedPreferences = getSharedPreferences("PedometerPrefs", Context.MODE_PRIVATE)
        loadPersistentData()

        if (stepCounterSensor == null)
        {
            Log.e("PedometerService", "Step counter sensor not available!")
            stopSelf()
            return
        }
        startStepCounting()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("PedometerService", "onStartCommand called")
        val notification = createNotification()
    }

    private fun getCurrentDateString(): String {
        return dateFormat.format(java.util.Date())
    }

    private fun loadPersistentData() {
        lastKnownDateString = sharedPreferences.getString("lastKnownDateString", getCurrentDateString()) ?: getCurrentDateString()

        if (lastKnownDateString != getCurrentDateString()) {
            Log.d("PedometerViewModel", "New day detected on load. Previous date: $lastKnownDateString")
            lastKnownDateString = getCurrentDateString()
            sharedPreferences.edit {
                putString("lastKnownDateString", lastKnownDateString)
            }
        }
    }
    // start  providing events to OnSensorChanged
    fun startStepCounting(){
        if (stepCounterSensor != null && checkPermission()) {
            Log.d("PedometerViewModel", "Registering step counter listener.")
            totalStepsFromSensorSinceBoot = -1L
            _todaySteps.value = 0L
            sensorManager.registerListener(
                this, // 'this' refers to the PedometerViewModel instance, which is a SensorEventListener
                stepCounterSensor,
                SensorManager.SENSOR_DELAY_UI)
        }
        else if (!checkPermission()) {Log.w("PedometerViewModel", "Cannot start step counting: Permission not granted.")}
        else {Log.w("PedometerViewModel", "Cannot start step counting: Sensor not available.")}
    }

    fun stopStepCounting(){
        Log.d("PedometerViewModel", "Unregistering step counter listener.")
        sensorManager.unregisterListener(this)
    }

    private fun createNotification(steps: Long): Notification {
        val context = getApplication<Application>().applicationContext

        val notification = NotificationCompat.Builder(context, MainActivity.STEP_COUNT_CHANNEL_ID)
            .setContentTitle("Pedometer")
            .setContentText("Today's steps: $todaySteps")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        notificationManager.notify(STEP_COUNT_NOTIFICATION_ID, notification)
    }

    // part of SensorEventListener, works because I have registered a listener in startStepCounting()
    override fun onSensorChanged(event: SensorEvent?) {
        Log.d("PedometerVM_Sensor", "onSensorChanged called. Event Sensor Type: ${event?.sensor?.type}, Value0: ${event?.values?.getOrNull(0)}")
        event?.let{
            if(it.sensor.type == Sensor.TYPE_STEP_COUNTER){
                val totalStepsFromSensor = it.values[0].toLong()
                Log.d("PedometerViewModel", "Total steps from sensor: $totalStepsFromSensor")
                val currentDateString = getCurrentDateString()
                if(totalStepsFromSensorSinceBoot == -1L) {
                    totalStepsFromSensorSinceBoot = totalStepsFromSensor
                    Log.d("PedometerViewModel", "Initial steps set to: $totalStepsFromSensorSinceBoot")
                }
                // resets todaySteps if it's a new day
                if (currentDateString != lastKnownDateString) {
                    Log.d("PedometerViewModel", "New day detected. Previous date: $lastKnownDateString")
                    lastKnownDateString = currentDateString
                    sharedPreferences.edit {
                        putString("lastKnownDateString", lastKnownDateString)
                    }
                    _todaySteps.value = 0
                }
                _todaySteps.value += totalStepsFromSensor - totalStepsFromSensorSinceBoot
                totalStepsFromSensorSinceBoot = totalStepsFromSensor
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("PedometerViewModel", "Sensor accuracy changed: $accuracy")
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.d("PedometerService", "onBind called")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("PedometerService", "onUnbind called")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

}