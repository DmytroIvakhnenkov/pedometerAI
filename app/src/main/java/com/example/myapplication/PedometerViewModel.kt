package com.example.myapplication

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PedometerViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {
    private val _context = application.applicationContext
    private var initialSteps = -1L
    private var stepsSinceLastReset = 0L
    private var stepCounterSensor: Sensor? = null
    private var sensorManager: SensorManager = _context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val _todaySteps = MutableStateFlow(0L)
    val todaySteps: StateFlow<Long> = _todaySteps

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission

    init {
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (stepCounterSensor == null) {
            Log.e("PedometerViewModel", "Step counter sensor not available!")
        }
        _hasPermission.value = checkPermission()
    }

    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                _context,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    fun startStepCounting(){
        if (stepCounterSensor != null && checkPermission()) {

        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // logic to handle step data
        event?.let{
            if(it.sensor.type == Sensor.TYPE_STEP_DETECTOR){
                val totalStepsFromSensor = it.values[0].toLong()
                Log.d("PedometerViewModel", "Total steps from sensor: $totalStepsFromSensor")
                if(initialSteps == -1L)
                {
                    initialSteps = totalStepsFromSensor
                    Log.d("PedometerViewModel", "Initial steps set to: $initialSteps")
                }
                // TO DO: reset this every day
                stepsSinceLastReset =  totalStepsFromSensor - initialSteps
                _todaySteps.value = stepsSinceLastReset

                Log.d("PedometerViewModel", "Steps since last reset: $stepsSinceLastReset")

            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("PedometerViewModel", "Sensor accuracy changed: $accuracy")
    }

}