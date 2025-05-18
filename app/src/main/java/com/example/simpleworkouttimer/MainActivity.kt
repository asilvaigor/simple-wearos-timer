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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var timerService: TimerService? = null
    private var isBound = false
    private var targetTimeSeconds by mutableStateOf(45)
    private var isTimerRunning by mutableStateOf(false)
    private var timerInstanceKey by mutableStateOf(0)

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
                TimerService.ACTION_TIMER_FINISHED -> {}
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
        timerInstanceKey++
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START_TIMER
            putExtra(TimerService.EXTRA_TARGET_TIME_MILLIS, targetMillis)
        }
        startForegroundService(intent)
    }

    private fun stopTimerInService() {
        isTimerRunning = false
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
    timerKey: Int,
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
                    timerKey = timerKey,
                    onScreenTap = {
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

    Scaffold(
        timeText = {
            TimeText(
                timeTextStyle = TimeTextDefaults.timeTextStyle(
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onBackground
                )
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTimeSelected(selectedTime) }
                )
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Button(
                    onClick = { if (selectedTime > 5) selectedTime -= 5 },
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                    modifier = Modifier.size(ButtonDefaults.ExtraSmallButtonSize)
                ) {
                    Text("-")
                }
                Text(
                    "${selectedTime}s",
                    fontSize = 34.sp,
                    color = MaterialTheme.colors.onBackground
                )
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
}

@Composable
fun TimerScreen(
    isTimerActive: Boolean,
    targetTimeMillis: Long,
    timerKey: Int,
    onScreenTap: () -> Unit
) {
    var displayTimeMillis by remember { mutableStateOf(0L) }

    LaunchedEffect(isTimerActive, timerKey) {
        if (isTimerActive) {
            displayTimeMillis = 0L

            val timerStartTime = System.currentTimeMillis()
            launch {
                while (isActive) {
                    val elapsed = System.currentTimeMillis() - timerStartTime
                    displayTimeMillis = elapsed
                    delay(100)
                }
            }
        }
    }

    val progress = if (targetTimeMillis > 0) {
        (displayTimeMillis.toFloat() / targetTimeMillis.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var isTapProcessing by remember { mutableStateOf(false) }

    Scaffold(
        timeText = {
            TimeText(
                timeTextStyle = TimeTextDefaults.timeTextStyle(
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onBackground
                )
            )
        },
        modifier = Modifier
            .fillMaxSize()
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
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CustomArcProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize(),
                backgroundColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
                foregroundColor = MaterialTheme.colors.primary
            )

            Text(
                text = formatTime(displayTimeMillis),
                fontSize = 40.sp,
                textAlign = TextAlign.Center,
                color = if (targetTimeMillis in 1..displayTimeMillis)
                    MaterialTheme.colors.primary
                else
                    MaterialTheme.colors.onBackground
            )
        }
    }
}

@Composable
fun CustomArcProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    foregroundColor: Color,
    strokeWidth: Dp = 6.dp
) {
    Canvas(modifier = modifier) {
        val arcSize = size.minDimension - strokeWidth.toPx() * 2

        drawArc(
            color = backgroundColor,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(
                (size.width - arcSize) / 2,
                (size.height - arcSize) / 2
            ),
            size = Size(arcSize, arcSize),
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )

        val sweepAngle = progress * 300f
        if (sweepAngle > 0f) {
            drawArc(
                color = foregroundColor,
                startAngle = -60f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(
                    (size.width - arcSize) / 2,
                    (size.height - arcSize) / 2
                ),
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    val hundreds = (millis % 1000) / 100
    return String.format("%02d:%02d.%1d", minutes, remainingSeconds, hundreds)
}
