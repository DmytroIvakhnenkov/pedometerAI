package com.example.myapplication

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.myapplication.MainActivity.Companion.STEP_COUNT_CHANNEL_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PedometerService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var totalStepsFromSensorSinceBoot = -1L
    private var currentDaySteps = 0L
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var lastKnownDateString: String

    //For Room Database
    private val appDatabase by lazy {
        AppDatabase.getDatabase(this)
    }
    private val stepDataDao by lazy {
        appDatabase.stepDataDao()
    }
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    //

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): PedometerService = this@PedometerService
    }

    private val _serviceSteps = MutableStateFlow(0L)
    val serviceSteps: StateFlow<Long> = _serviceSteps.asStateFlow()

    // TASKS:
    // handle if the user turns off the app for a couple of days
    // database has to be filled for those days
    override fun onCreate() {
        super.onCreate()
        Log.d("PedometerService", "onCreate called")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        sharedPreferences = getSharedPreferences("PedometerPrefs", Context.MODE_PRIVATE)
        loadInitialData()

        if (stepCounterSensor == null)
        {
            Log.e("PedometerService", "Step counter sensor not available!")
            stopSelf()
            return
        }
        startStepCounting()
    }

    // permission management is handled in startStepCounting, which is executed in onCreate
    // but maybe I need to add permission checking here as well
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("PedometerService", "onStartCommand called")
        createNotificationChannel()
        val notification = createNotification(currentDaySteps)
        startForeground(STEP_COUNT_NOTIFICATION_ID, notification)

        return START_STICKY
    }


    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun loadInitialData() {
        lastKnownDateString = sharedPreferences.getString("lastKnownDateString", getCurrentDateString()) ?: getCurrentDateString()
        currentDaySteps = sharedPreferences.getLong("steps_$lastKnownDateString", 0L)
    }
    // start providing events to OnSensorChanged
    fun startStepCounting(){
        if (stepCounterSensor != null &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            Log.d("PedometerViewModel", "Registering step counter listener.")
            totalStepsFromSensorSinceBoot = -1L
            sensorManager.registerListener(
                this, // 'this' refers to the PedometerViewModel instance, which is a SensorEventListener
                stepCounterSensor,
                SensorManager.SENSOR_DELAY_NORMAL)
             }
        else {
            Log.w("PedometerViewModel", "Cannot start step counting.")
            stopSelf()
        }
    }

    fun stopStepCounting(){
        Log.d("PedometerViewModel", "Unregistering step counter listener.")
        sensorManager.unregisterListener(this)
    }

    private fun createNotification(steps: Long): Notification {
        val context = applicationContext
        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(context, MainActivity.STEP_COUNT_CHANNEL_ID)
            .setContentTitle("Pedometer")
            .setContentText("Today's steps: $steps")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }


    // part of SensorEventListener, works because I have registered a listener in startStepCounting()
    override fun onSensorChanged(event: SensorEvent?) {
        Log.d("PedometerVM_Sensor", "onSensorChanged called. Event Sensor Type: ${event?.sensor?.type}, Value0: ${event?.values?.getOrNull(0)}")
        event?.let{
            if(it.sensor.type == Sensor.TYPE_STEP_COUNTER){

                // get steps from hardware pedometer
                val rawStepsFromSensor = it.values[0].toLong()
                Log.d("PedometerViewModel", "Raw steps from sensor: $rawStepsFromSensor")

                // either first time or device has been rebooted
                if(totalStepsFromSensorSinceBoot == -1L) {
                    totalStepsFromSensorSinceBoot = rawStepsFromSensor
                    Log.d("PedometerViewModel", "Initial steps set to: $totalStepsFromSensorSinceBoot")
                }


                // resets todaySteps if it's a new day
                val currentDateString = getCurrentDateString()
                if (currentDateString != lastKnownDateString) {
                    Log.d("PedometerViewModel", "New day detected. Previous date: $lastKnownDateString")
                    saveStepsToRoom(lastKnownDateString, currentDaySteps)
                    currentDaySteps = 0L
                    lastKnownDateString = currentDateString
                    sharedPreferences.edit {
                        putString("lastKnownDateString", lastKnownDateString)
                    }
                }

                // get delta
                val newStepsThisEvent = if (rawStepsFromSensor >= totalStepsFromSensorSinceBoot){
                    rawStepsFromSensor - totalStepsFromSensorSinceBoot
                } else {
                    Log.w("PedometerService", "Sensor counter appears to have reset.")
                    rawStepsFromSensor
                }

                // update the values
                currentDaySteps += newStepsThisEvent
                totalStepsFromSensorSinceBoot = rawStepsFromSensor

                _serviceSteps.value  = currentDaySteps
                updateNotification(currentDaySteps)
                sharedPreferences.edit {
                    putLong("steps_$currentDateString", currentDaySteps)
                }
                Log.d("PedometerViewModel", "Updated currentDaySteps.")
            }
        }
    }
    private fun updateNotification(steps: Long) {
        val notification = createNotification(steps)
        notificationManager.notify(STEP_COUNT_NOTIFICATION_ID, notification)
    }

    private fun saveStepsToRoom(dateString: String, steps: Long) {
        serviceScope.launch{
            try {
                val dailyStepsEntry = StepData(date = dateString, steps = steps)
                stepDataDao.insertOrUpdateSteps(dailyStepsEntry)
                Log.i("Pedometer Service", "Saved steps to Room: $steps")
            }
            catch (e: Exception){
                Log.e("Pedometer Service", "Error saving steps to Room: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        val name = "Step Counter"
        val descriptionText = "Displays current step count"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(STEP_COUNT_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

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
        stopStepCounting()
        Log.d("{PedometerService", "onDestroy called")
    }

}