package com.example.myapplication

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import androidx.core.content.edit
import java.util.Locale

class PedometerViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val sharedPreferences: SharedPreferences = application.getSharedPreferences("PedometerPrefs", Context.MODE_PRIVATE)

    private var lastKnownDateString: String = ""

    private val _context = application.applicationContext
    private var totalStepsFromSensorSinceBoot = -1L
    private var stepCounterSensor: Sensor? = null
    private var sensorManager: SensorManager = _context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _todaySteps = MutableStateFlow(0L)
    val todaySteps: StateFlow<Long> = _todaySteps

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission

    init {
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounterSensor == null) {
            Log.e("PedometerViewModel", "Step counter sensor not available!")
        }
        _hasPermission.value = checkPermission()
        loadPersistentData()
    }

    // check the permission to count
    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
                _context,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED}

    // expose the permission state to the composable (it will get updated by the activityLauncher)
    fun updatePermissionState(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) {startStepCounting()}
        else {stopStepCounting()}
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

    override fun onCleared() {
        super.onCleared()
        stopStepCounting()
    }

}