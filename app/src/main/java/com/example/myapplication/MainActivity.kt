package com.example.myapplication

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val STEP_COUNT_CHANNEL_ID = "step_count_channel"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val pedometerViewModel: PedometerViewModel = viewModel()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PedometerScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = pedometerViewModel
                    )
                }
            }
        }
        createNotificationChannel()
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
}



@Composable
fun PedometerScreen(modifier: Modifier = Modifier,viewModel: PedometerViewModel) {
    val todaySteps by viewModel.todaySteps.collectAsState()
    val hasPermission by viewModel.hasActivityRecognitionPermission.collectAsState()

    // get the onActivityResultLauncher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> viewModel.updateActivityRecognitionPermissionState(isGranted)  })

    // get the lifecycle owner
    // key1 is to enter the UI when it first composes
    // LaunchedEffect adds a new observer to the lifecycle
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(key1 = lifecycleOwner, key2 = hasPermission) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (hasPermission) {viewModel.startStepCounting()}
                    else {permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)}
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.stopStepCounting()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }
    Column(
        modifier = modifier
            .fillMaxSize() // Make the Column take the whole screen
            .padding(16.dp), // Add some padding around the content
        horizontalAlignment = Alignment.CenterHorizontally, // Center children horizontally
        verticalArrangement = Arrangement.Center // Center children vertically
    )
    {
        Text(text = "Steps Today",
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = todaySteps.toString(),
            fontSize = 48.sp,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary)

        if (!hasPermission) {
            Button(onClick = {permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)})
            {Text("Request Activity Permission")}
            Text("Activity recognition permission is needed to count steps.",
                modifier = Modifier.padding(top = 8.dp))}
        else {Text("Step sensor active.")}
    }
}

@Preview(showBackground = true)
@Composable
fun PedometerScreenPreview() {
    MyApplicationTheme {
        val pedometerViewModel: PedometerViewModel = viewModel()
        PedometerScreen(viewModel = pedometerViewModel)
    }
}