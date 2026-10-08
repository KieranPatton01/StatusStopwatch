package com.statusstopwatch

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import com.statusstopwatch.databinding.ActivityMainBinding

/**
 * Main Activity providing Material 3 controls, permission configuration,
 * and live calibration for the Status Bar Stopwatch overlay.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: StopwatchPreferences

    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiUpdateRunnable = object : Runnable {
        override fun run() {
            refreshTimerDisplay()
            if (preferences.isRunning) {
                uiHandler.postDelayed(this, 100L)
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        updatePermissionStatuses()
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == StopwatchService.BROADCAST_TIME_TICK) {
                refreshTimerDisplay()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = StopwatchPreferences(this)

        setupButtons()
        setupCalibrationSliders()
        setupPermissions()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        refreshTimerDisplay()
        registerTickReceiver()
        startUiLoop()
    }

    override fun onPause() {
        super.onPause()
        stopUiLoop()
        unregisterTickReceiver()
    }

    private fun registerTickReceiver() {
        val filter = IntentFilter(StopwatchService.BROADCAST_TIME_TICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(tickReceiver, filter)
        }
    }

    private fun unregisterTickReceiver() {
        try {
            unregisterReceiver(tickReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver not registered
        }
    }

    private fun startUiLoop() {
        uiHandler.removeCallbacks(uiUpdateRunnable)
        uiHandler.post(uiUpdateRunnable)
    }

    private fun stopUiLoop() {
        uiHandler.removeCallbacks(uiUpdateRunnable)
    }

    // ==========================================
    // ACTION CONTROLS (START / PAUSE / RESET)
    // ==========================================

    private fun setupButtons() {
        binding.btnStartResume.setOnClickListener {
            if (!hasOverlayPermission()) {
                requestOverlayPermission()
                Toast.makeText(this, "Overlay permission is required to display the status bar pill", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            preferences.start()
            val serviceIntent = Intent(this, StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_START
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            refreshTimerDisplay()
            startUiLoop()
        }

        binding.btnPause.setOnClickListener {
            preferences.pause()
            val serviceIntent = Intent(this, StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_PAUSE
            }
            startService(serviceIntent)
            stopUiLoop()
            refreshTimerDisplay()
        }

        binding.btnReset.setOnClickListener {
            preferences.reset()
            val serviceIntent = Intent(this, StopwatchService::class.java).apply {
                action = StopwatchService.ACTION_RESET
            }
            startService(serviceIntent)
            stopUiLoop()
            refreshTimerDisplay()
        }
    }

    // ==========================================
    // PERMISSIONS MANAGEMENT
    // ==========================================

    private fun setupPermissions() {
        binding.btnGrantOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        binding.btnGrantNotifications.setOnClickListener {
            requestNotificationPermission()
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updatePermissionStatuses() {
        // Overlay permission status
        val overlayGranted = hasOverlayPermission()
        if (overlayGranted) {
            binding.btnGrantOverlay.text = getString(R.string.badge_granted)
            binding.btnGrantOverlay.isEnabled = false
            binding.btnGrantOverlay.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        } else {
            binding.btnGrantOverlay.text = getString(R.string.btn_grant)
            binding.btnGrantOverlay.isEnabled = true
            binding.btnGrantOverlay.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }

        // Notification permission status
        val notificationGranted = hasNotificationPermission()
        if (notificationGranted) {
            binding.btnGrantNotifications.text = getString(R.string.badge_granted)
            binding.btnGrantNotifications.isEnabled = false
            binding.btnGrantNotifications.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        } else {
            binding.btnGrantNotifications.text = getString(R.string.btn_grant)
            binding.btnGrantNotifications.isEnabled = true
            binding.btnGrantNotifications.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    // ==========================================
    // POSITION CALIBRATION & SLIDERS
    // ==========================================

    private fun setupCalibrationSliders() {
        // Overlay toggle switch
        binding.switchOverlayEnabled.isChecked = preferences.isOverlayEnabled
        binding.switchOverlayEnabled.setOnCheckedChangeListener { _, isChecked ->
            preferences.isOverlayEnabled = isChecked
            sendServiceAction(StopwatchService.ACTION_TOGGLE_OVERLAY)
        }

        // X Offset calibration
        val currentX = preferences.offsetX.coerceIn(0, 300)
        binding.sliderOffsetX.value = currentX.toFloat()
        binding.tvOffsetXValue.text = "$currentX dp"

        binding.sliderOffsetX.addOnChangeListener(Slider.OnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val intVal = value.toInt()
                preferences.offsetX = intVal
                binding.tvOffsetXValue.text = "$intVal dp"
                sendServiceAction(StopwatchService.ACTION_UPDATE_OFFSETS)
            }
        })

        binding.btnOffsetXMinus.setOnClickListener {
            val newX = (preferences.offsetX - 1).coerceAtLeast(0)
            preferences.offsetX = newX
            binding.sliderOffsetX.value = newX.toFloat()
            binding.tvOffsetXValue.text = "$newX dp"
            sendServiceAction(StopwatchService.ACTION_UPDATE_OFFSETS)
        }

        binding.btnOffsetXPlus.setOnClickListener {
            val newX = (preferences.offsetX + 1).coerceAtMost(300)
            preferences.offsetX = newX
            binding.sliderOffsetX.value = newX.toFloat()
            binding.tvOffsetXValue.text = "$newX dp"
            sendServiceAction(StopwatchService.ACTION_UPDATE_OFFSETS)
        }

        // Y Offset calibration
        val currentY = preferences.offsetY.coerceIn(-20, 80)
        binding.sliderOffsetY.value = currentY.toFloat()
        binding.tvOffsetYValue.text = "$currentY dp"

        binding.sliderOffsetY.addOnChangeListener(Slider.OnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val intVal = value.toInt()
                preferences.offsetY = intVal
                binding.tvOffsetYValue.text = "$intVal dp"
                sendServiceAction(StopwatchService.ACTION_UPDATE_OFFSETS)
            }
        })

        binding.btnOffsetYMinus.setOnClickListener {
            val newY = (preferences.offsetY - 1).coerceAtLeast(-20)
            preferences.offsetY = newY
            binding.sliderOffsetY.value = newY.toFloat()
            binding.tvOffsetYValue.text = "$newY dp"
            sendServiceAction(StopwatchService.ACTION_UPDATE_OFFSETS)
        }

        binding.btnOffsetYPlus.setOnClickListener {
            val newY = (preferences.offsetY + 1).coerceAtMost(80)
            preferences.offsetY = newY
            binding.sliderOffsetY.value = newY.toFloat()
            binding.tvOffsetYValue.text = "$newY dp"
            sendServiceAction(StopwatchService.ACTION_UPDATE_OFFSETS)
        }

        // Reset offsets
        binding.btnResetOffsets.setOnClickListener {
            preferences.offsetX = StopwatchPreferences.DEFAULT_OFFSET_X
            preferences.offsetY = StopwatchPreferences.DEFAULT_OFFSET_Y
            binding.sliderOffsetX.value = StopwatchPreferences.DEFAULT_OFFSET_X.toFloat()
            binding.sliderOffsetY.value = StopwatchPreferences.DEFAULT_OFFSET_Y.toFloat()
            binding.tvOffsetXValue.text = "${StopwatchPreferences.DEFAULT_OFFSET_X} dp"
            binding.tvOffsetYValue.text = "${StopwatchPreferences.DEFAULT_OFFSET_Y} dp"
            sendServiceAction(StopwatchService.ACTION_UPDATE_OFFSETS)
            Toast.makeText(this, "Position reset to defaults", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, StopwatchService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }

    // ==========================================
    // UI REFRESH & DISPLAY
    // ==========================================

    private fun refreshTimerDisplay() {
        val elapsed = preferences.getElapsedTime()
        val isRunning = preferences.isRunning
        val hasAccumulated = preferences.accumulatedTime > 0L

        // Update high-precision large timer
        binding.tvLargeTimer.text = TimeFormatter.formatDetailed(elapsed)

        // Update progressive pill preview
        binding.tvPillPreviewText.text = TimeFormatter.format(elapsed)

        // Update status badge & button states
        when {
            isRunning -> {
                binding.tvStatusBadge.text = getString(R.string.status_running)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                binding.btnStartResume.text = getString(R.string.status_running)
                binding.btnStartResume.isEnabled = false
                binding.btnPause.isEnabled = true
                binding.btnReset.isEnabled = true
            }
            hasAccumulated -> {
                binding.tvStatusBadge.text = getString(R.string.status_paused)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
                binding.btnStartResume.text = getString(R.string.btn_resume)
                binding.btnStartResume.isEnabled = true
                binding.btnPause.isEnabled = false
                binding.btnReset.isEnabled = true
            }
            else -> {
                binding.tvStatusBadge.text = getString(R.string.status_stopped)
                binding.tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                binding.btnStartResume.text = getString(R.string.btn_start)
                binding.btnStartResume.isEnabled = true
                binding.btnPause.isEnabled = false
                binding.btnReset.isEnabled = false
            }
        }
    }
}
