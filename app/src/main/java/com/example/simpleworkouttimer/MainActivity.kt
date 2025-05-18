package com.example.simpleworkouttimer

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
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    var targetTimeSeconds by remember { mutableStateOf(45) }
    var currentTimeMillis by remember { mutableStateOf(0L) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(true) }
    val vibrator = androidx.compose.ui.platform.LocalContext.current.getSystemService(Vibrator::class.java)

    val coroutineScope = rememberCoroutineScope()
    var timerJob by remember { mutableStateOf<Job?>(null) }
    var currentTimerId by remember { mutableStateOf(0) }
    var hasVibratedForCurrentRun by remember { mutableStateOf(false) } // Added: Flag to track vibration

    fun stopTimer() {
        timerJob?.cancel()
        isTimerRunning = false
    }

    fun startTimerInstance() {
        timerJob?.cancel()

        currentTimerId++
        val newTimerId = currentTimerId
        hasVibratedForCurrentRun = false // Added: Reset vibration flag for the new timer instance

        isTimerRunning = true
        timerJob = coroutineScope.launch {
            val effectiveStartTime = System.currentTimeMillis() - currentTimeMillis
            try {
                while (isActive) {
                    if (currentTimerId == newTimerId) {
                        currentTimeMillis = System.currentTimeMillis() - effectiveStartTime

                        val targetMillis = targetTimeSeconds * 1000L
                        if (!hasVibratedForCurrentRun && targetMillis > 0 && currentTimeMillis >= targetMillis) {
                            vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                            hasVibratedForCurrentRun = true // Set flag to ensure vibration only happens once
                        }
                    }
                    delay(10)
                }
            } finally {
                // Optional: Actions on coroutine completion/cancellation
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
                currentTimeMillis = 0L
            }
        }

        androidx.wear.compose.material.SwipeToDismissBox(
            state = swipeToDismissBoxState,
            onDismissed = {
                showPicker = true
                currentTimeMillis = 0L
            }
        ) { isBackground ->
            if (isBackground) {
                TimePickerScreen(
                    initialValue = targetTimeSeconds,
                    onTimeSelected = {}
                )
            } else {
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
                modifier = Modifier
                    .pointerInput(Unit) {}
                    .size(ButtonDefaults.ExtraSmallButtonSize)
            ) {
                Text("-")
            }
            Text("${selectedTime}s", fontSize = 28.sp)
            Button(
                onClick = { selectedTime += 5 },
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                modifier = Modifier
                    .pointerInput(Unit) {}
                    .size(ButtonDefaults.ExtraSmallButtonSize)
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
            text = formatTime(currentTimeMillis),
            fontSize = 40.sp,
            textAlign = TextAlign.Center,
            color = if (currentTimeMillis >= targetTimeMillis && targetTimeMillis > 0) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground
        )
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    val hundreds = (millis % 1000) / 10
    return String.format("%02d:%02d.%02d", minutes, remainingSeconds, hundreds)
}
