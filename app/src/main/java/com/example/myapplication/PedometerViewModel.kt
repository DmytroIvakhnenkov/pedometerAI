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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date


const val STEP_COUNT_NOTIFICATION_ID = 1

class PedometerViewModel(application: Application) : AndroidViewModel(application) {

    @SuppressLint("StaticFieldLeak") // I am getting the MainActivity context from the application context
    private val _context = application.applicationContext

    private val _uiTodaySteps = MutableStateFlow(0L)
    val uiTodaySteps: StateFlow<Long> = _uiTodaySteps.asStateFlow()

    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    private val _lastSevenDaysSteps = MutableStateFlow<List<StepData>>(emptyList())
    val lastSevenDaysSteps: StateFlow<List<StepData>> = _lastSevenDaysSteps.asStateFlow()

    private val database by lazy {
        AppDatabase.getDatabase(application)}
    private val stepDataDao by lazy {
        database.stepDataDao()}

    @SuppressLint("StaticFieldLeak")
    private var pedometerService: PedometerService? = null
    private var bound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PedometerService.LocalBinder
            pedometerService = binder.getService()
            bound = true
            _isServiceBound.value = true

            viewModelScope.launch {
                pedometerService?.serviceSteps?.collect { stepsFromService ->
                    _uiTodaySteps.value = stepsFromService
                    fetchLastSevenDaysSteps()
                }
            }
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
            Log.d("PedometerViewModel", "Attempting to bind to service. Current bound state: $bound") // ADD THIS LOG
            Intent(_context, PedometerService::class.java).also { intent ->
                Log.d("PedometerViewModel", "Binding with intent: $intent") // ADD THIS LOG
                val wasBound = _context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                Log.d("PedometerViewModel", "bindService call returned: $wasBound") // ADD THIS LOG
            }
        }
        else {
            Log.d("PedometerViewModel", "Service already considered bound, not calling bindService again.") // ADD THIS LOG
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

    fun fetchLastSevenDaysSteps(){
        viewModelScope.launch {

            val dates = mutableListOf<String>()
            val dateLabels = mutableListOf<String>()
            val dayFormatter = SimpleDateFormat("EEE", Locale.getDefault())
            val dbDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val targetDate = Calendar.getInstance()
            targetDate.add(Calendar.DATE, -1)
            var todayDateString = dbDateFormatter.format(targetDate.time)
            stepDataDao.insertOrUpdateSteps(StepData(todayDateString, 57))
            targetDate.add(Calendar.DATE, -2)
            todayDateString = dbDateFormatter.format(targetDate.time)
            stepDataDao.insertOrUpdateSteps(StepData(todayDateString, 67))

            for (i in 0..6) {
                val targetDate = Calendar.getInstance()
                targetDate.add(Calendar.DATE, -i)
                dates.add(dbDateFormatter.format(targetDate.time))
                dateLabels.add(dayFormatter.format(targetDate.time))
            }
            dates.reverse()
            dateLabels.reverse()

            Log.d("PedometerViewModel", "Fetching steps for dates: $dates")
            //Fetch from Room
            stepDataDao.getStepsForDates(dates).first().let { stepFromDb ->
                val stepsMap = stepFromDb.associateBy { steps -> steps.date}
                val chartDataList = mutableListOf<StepData>()

                val todayDateString = dbDateFormatter.format(Date())

                for (i in dates.indices){
                    val dateStr = dates[i]
                    val label = dateLabels[i]
                    val stepsForDay: Long

                    if (dateStr == todayDateString){
                        stepsForDay = _uiTodaySteps.value
                    }
                    else {
                        stepsForDay = stepsMap[dateStr]?.steps ?: 0L
                    }
                    chartDataList.add(StepData(label, stepsForDay))
                }
                _lastSevenDaysSteps.value = chartDataList
                Log.d("PedometerViewModel", "Chart data: $chartDataList")

            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        unbindFromService()
    }



}