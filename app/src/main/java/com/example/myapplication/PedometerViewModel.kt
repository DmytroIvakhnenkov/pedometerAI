package com.example.myapplication

import android.app.Application
import android.hardware.SensorEventListener
import androidx.lifecycle.AndroidViewModel

class PedometerViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) {
        // logic to handle step data
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // handle accuracy changes (optional)
    }

}