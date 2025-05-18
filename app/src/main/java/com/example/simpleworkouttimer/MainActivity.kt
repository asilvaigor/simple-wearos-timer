package com.example.simpleworkouttimer

import android.app.AlarmManager // Added import
import android.app.PendingIntent // Added import
import android.content.Context // Added import
import android.content.Intent // Added import
import android.os.Bundle
import android.os.Vibrator
import android.os.VibrationEffect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.foundation.SwipeToDismissValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext // Added import for LocalContext
import kotlinx.coroutines.isActive
import android.util.Log // Added import for Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

// Companion object for constants like the request code
private object WearAppConstants {
    const val TIMER_ALARM_REQUEST_CODE = 12345
}

@Composable
fun WearApp() {
    var targetTimeSeconds by remember { mutableStateOf(45) }
    var currentTimeMillis by remember { mutableStateOf(0L) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(true) }

    val context = LocalContext.current // Get context
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager // Get AlarmManager
    val vibrator = context.getSystemService(Vibrator::class.java)

    val coroutineScope = rememberCoroutineScope()
    var timerJob by remember { mutableStateOf<Job?>(null) }
    var currentTimerId by remember { mutableStateOf(0) }

    fun getTimerPendingIntent(): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            WearAppConstants.TIMER_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun stopTimer() {
        timerJob?.cancel()
        isTimerRunning = false
        // Cancel the alarm when timer stops
        alarmManager.cancel(getTimerPendingIntent())
        Log.d("WearApp", "Timer stopped and alarm cancelled.")
    }

    fun startTimerInstance() {
        timerJob?.cancel() // Cancel any existing timer job (for UI updates)
        // Cancel any previously set alarm before starting a new one
        alarmManager.cancel(getTimerPendingIntent())

        currentTimerId++ // Increment to create a new unique ID for this timer instance
        val newTimerId = currentTimerId // Capture the ID for this specific coroutine

        isTimerRunning = true
        timerJob = coroutineScope.launch {
            val effectiveStartTime = System.currentTimeMillis() - currentTimeMillis
            try {
                while (isActive) { // Loop while coroutine is active
                    if (currentTimerId == newTimerId) { // Only allow the current timer to update state
                        currentTimeMillis = System.currentTimeMillis() - effectiveStartTime
                    }
                    delay(10) // UI update interval
                }
            } finally {
                // Optional: Actions on coroutine completion/cancellation
            }
        }

        // Schedule the alarm for vibration
        val targetMillis = targetTimeSeconds * 1000L
        if (targetMillis > 0) {
            // Alarm should trigger when the timer duration has passed from now.
            // Assumes currentTimeMillis is 0 when a new timer sequence starts.
            val triggerAtMillis = System.currentTimeMillis() + targetMillis
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    getTimerPendingIntent()
                )
                Log.d("WearApp", "Alarm set for $triggerAtMillis")
            } catch (se: SecurityException) {
                Log.e("WearApp", "SecurityException: Missing SCHEDULE_EXACT_ALARM permission or other issue.", se)
                // Consider notifying user or fallback if permission is denied.
            }
        }
    }

    if (showPicker) {
        LaunchedEffect(Unit) {
            stopTimer()
        }

        TimePickerScreen(
            initialValue = targetTimeSeconds,
            onTimeSelected = { selectedTargetTime ->
                targetTimeSeconds = selectedTargetTime
                currentTimeMillis = 0L
                showPicker = false
                startTimerInstance()
            }
        )
    } else {
        val swipeToDismissBoxState = rememberSwipeToDismissBoxState()
        LaunchedEffect(swipeToDismissBoxState.currentValue) {
            if (swipeToDismissBoxState.currentValue == SwipeToDismissValue.Dismissed) {
                showPicker = true
                currentTimeMillis = 0L // Also reset time when swiping to picker
                // stopTimer() will be called by the LaunchedEffect in the showPicker block
            }
        }

        androidx.wear.compose.material.SwipeToDismissBox(
            state = swipeToDismissBoxState,
            onDismissed = {
                showPicker = true
                currentTimeMillis = 0L // Also reset time
                // stopTimer() will be called by the LaunchedEffect in the showPicker block
            }
        ) { isBackground -> // This lambda is called for both background and foreground
            if (isBackground) {
                // Render TimePickerScreen as the background content
                TimePickerScreen(
                    initialValue = targetTimeSeconds,
                    onTimeSelected = {} // Non-interactive for background preview
                )
            } else {
                // Render TimerScreen as the foreground content
                TimerScreen(
                    currentTimeMillis = currentTimeMillis,
                    targetTimeMillis = targetTimeSeconds * 1000L,
                    onScreenTap = {
                        currentTimeMillis = 0L
                        startTimerInstance()
                    }
                )
            }
        }
    }
}

@Composable
fun TimePickerScreen(initialValue: Int, onTimeSelected: (Int) -> Unit) {
    var selectedTime by remember { mutableStateOf(initialValue) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .pointerInput(Unit) { // Added pointerInput for screen tap
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
                modifier = Modifier
                    .pointerInput(Unit) {} // Consume touch events for buttons
                    .size(ButtonDefaults.ExtraSmallButtonSize) // Reduce button size
            ) {
                Text("-")
            }
            Text("${selectedTime}s", fontSize = 28.sp)
            Button(
                onClick = { selectedTime += 5 },
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                modifier = Modifier
                    .pointerInput(Unit) {} // Consume touch events for buttons
                    .size(ButtonDefaults.ExtraSmallButtonSize) // Reduce button size
            ) {
                Text("+")
            }
        }
    }
}

@Composable
fun TimerScreen(currentTimeMillis: Long, targetTimeMillis: Long, onScreenTap: () -> Unit) {
    val progress = if (targetTimeMillis > 0) (currentTimeMillis.toFloat() / targetTimeMillis.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onScreenTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = 1f, // Full circle for the background track
            modifier = Modifier.fillMaxSize(),
            indicatorColor = Color.Transparent, // Make the indicator part transparent
            trackColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f), // This is the visible track
            strokeWidth = 6.dp
        )
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            indicatorColor = MaterialTheme.colors.primary, // This is the actual progress arc
            trackColor = Color.Transparent, // Track for this one is transparent
            strokeWidth = 6.dp
        )
        Text(
            text = formatTime(currentTimeMillis),
            fontSize = 40.sp, // Adjusted font size to fit better with milliseconds
            textAlign = TextAlign.Center,
            color = if (currentTimeMillis >= targetTimeMillis && targetTimeMillis > 0) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground
        )
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    val hundreds = (millis % 1000) / 10 // Get two decimal points for seconds
    return String.format("%02d:%02d.%02d", minutes, remainingSeconds, hundreds)
}
