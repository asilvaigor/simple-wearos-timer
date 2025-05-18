package com.example.simpleworkouttimer

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.foundation.SwipeToDismissValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var timerService: TimerService? = null
    private var isBound = false
    private var targetTimeSeconds by mutableStateOf(45)
    private var isTimerRunning by mutableStateOf(false)
    private var timerInstanceKey by mutableStateOf(0) // Key to force UI refresh on timer restart

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TimerService.LocalBinder
            timerService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            timerService = null
        }
    }

    private val timerUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                TimerService.ACTION_TIMER_FINISHED -> {
                    // The UI timer will continue based on the existing isTimerRunning state.
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp(
                targetTimeSeconds = targetTimeSeconds,
                isTimerRunning = isTimerRunning,
                timerKey = timerInstanceKey, // Pass the key to WearApp
                onTargetTimeChange = { newTime -> targetTimeSeconds = newTime },
                startTimerService = { startTimeMillis -> startTimerInService(startTimeMillis) },
                stopTimerService = { stopTimerInService() }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, TimerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        val filter = IntentFilter().apply {
            addAction(TimerService.ACTION_TIMER_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerUpdateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(timerUpdateReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        unregisterReceiver(timerUpdateReceiver)
    }

    private fun startTimerInService(targetMillis: Long) {
        isTimerRunning = true
        timerInstanceKey++ // Increment key to signal a new timer instance for the UI
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START_TIMER
            putExtra(TimerService.EXTRA_TARGET_TIME_MILLIS, targetMillis)
        }
        startForegroundService(intent)
    }

    private fun stopTimerInService() {
        isTimerRunning = false
        // timerInstanceKey is not changed here, as stopping doesn't require a UI "reset" like starting does.
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_STOP_TIMER
        }
        startService(intent)
    }
}

@Composable
fun WearApp(
    targetTimeSeconds: Int,
    isTimerRunning: Boolean,
    timerKey: Int, // Receive timerKey
    onTargetTimeChange: (Int) -> Unit,
    startTimerService: (Long) -> Unit,
    stopTimerService: () -> Unit
) {
    var showPicker by remember { mutableStateOf(true) }

    val lastSelectedTargetTime = remember { mutableStateOf(targetTimeSeconds) }

    if (showPicker) {
        LaunchedEffect(Unit) {
            if (isTimerRunning) {
                stopTimerService()
            }
        }

        TimePickerScreen(
            initialValue = targetTimeSeconds,
            onTimeSelected = { selectedTargetTime ->
                onTargetTimeChange(selectedTargetTime)
                lastSelectedTargetTime.value = selectedTargetTime
                showPicker = false
                startTimerService(selectedTargetTime * 1000L)
            }
        )
    } else {
        val swipeToDismissBoxState = rememberSwipeToDismissBoxState()
        LaunchedEffect(swipeToDismissBoxState.currentValue) {
            if (swipeToDismissBoxState.currentValue == SwipeToDismissValue.Dismissed) {
                showPicker = true
                stopTimerService()
            }
        }

        androidx.wear.compose.material.SwipeToDismissBox(
            state = swipeToDismissBoxState,
            onDismissed = {
                showPicker = true
                stopTimerService()
            }
        ) { isBackground ->
            if (isBackground) {
                TimePickerScreen(
                    initialValue = targetTimeSeconds,
                    onTimeSelected = {}
                )
            } else {
                TimerScreen(
                    isTimerActive = isTimerRunning,
                    targetTimeMillis = targetTimeSeconds * 1000L,
                    timerKey = timerKey, // Pass timerKey to TimerScreen
                    onScreenTap = {
                        // Simply start the timer; the service will handle resetting if it's already running.
                        // The new timerInstanceKey will ensure TimerScreen resets its display.
                        startTimerService(lastSelectedTargetTime.value * 1000L)
                    }
                )
            }
        }
    }
}

@Composable
fun TimePickerScreen(initialValue: Int, onTimeSelected: (Int) -> Unit) {
    var selectedTime by remember { mutableStateOf(initialValue.coerceAtLeast(5)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTimeSelected(selectedTime) }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { if (selectedTime > 5) selectedTime -= 5 },
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                modifier = Modifier.size(ButtonDefaults.ExtraSmallButtonSize)
            ) {
                Text("-")
            }
            Text("${selectedTime}s", fontSize = 34.sp)
            Button(
                onClick = { selectedTime += 5 },
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                modifier = Modifier.size(ButtonDefaults.ExtraSmallButtonSize)
            ) {
                Text("+")
            }
        }
    }
}

@Composable
fun TimerScreen(isTimerActive: Boolean, targetTimeMillis: Long, timerKey: Int, onScreenTap: () -> Unit) {
    var displayTimeMillis by remember { mutableStateOf(0L) }

    LaunchedEffect(isTimerActive, timerKey) { // Add timerKey to LaunchedEffect dependencies
        if (isTimerActive) {
            displayTimeMillis = 0L // Reset display time when timer becomes active or key changes

            val timerStartTime = System.currentTimeMillis()
            launch {
                while (isActive) {
                    val elapsed = System.currentTimeMillis() - timerStartTime
                    displayTimeMillis = elapsed
                    delay(50)
                }
            }
        }
    }

    val progress = if (targetTimeMillis > 0) {
        (displayTimeMillis.toFloat() / targetTimeMillis.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var isTapProcessing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (!isTapProcessing) {
                            isTapProcessing = true
                            onScreenTap()
                            Handler(Looper.getMainLooper()).postDelayed({
                                isTapProcessing = false
                            }, 1000)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = 1f,
            modifier = Modifier.fillMaxSize(),
            indicatorColor = Color.Transparent,
            trackColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
            strokeWidth = 6.dp
        )
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            indicatorColor = MaterialTheme.colors.primary,
            trackColor = Color.Transparent,
            strokeWidth = 6.dp
        )
        Text(
            text = formatTime(displayTimeMillis),
            fontSize = 40.sp,
            textAlign = TextAlign.Center,
            color = if (targetTimeMillis in 1..displayTimeMillis) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground
        )
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    val hundreds = (millis % 1000) / 100
    return String.format("%02d:%02d.%1d", minutes, remainingSeconds, hundreds)
}
