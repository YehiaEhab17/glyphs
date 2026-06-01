package com.opencode.counter

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.nothing.ketchum.GlyphMatrixManager
import kotlin.math.sqrt

class CounterService : Service(), SensorEventListener {
    private lateinit var glyphMatrixManager: GlyphMatrixManager
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var vibrator: Vibrator
    
    private var count = 0
    private var lastShakeTime: Long = 0
    
    private val SHAKE_THRESHOLD_GRAVITY = 2.5f
    private val SHAKE_SLOP_TIME_MS = 500

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                // MSG_GLYPH_TOY = 1
                1 -> {
                    val dataBundle = msg.data ?: return
                    // Use all common keys they might send
                    val event = dataBundle.getString("data") ?: dataBundle.getString("event") ?: dataBundle.getString(com.nothing.ketchum.GlyphToy.MSG_GLYPH_TOY_DATA)
                    if (event == com.nothing.ketchum.GlyphToy.EVENT_CHANGE || event == "change" || event == com.nothing.ketchum.GlyphToy.EVENT_ACTION_DOWN || event == "action_down") {
                        // Press detected
                        incrementCount()
                    }
                }
            }
        }
    }
    private val messenger = Messenger(handler)

    override fun onCreate() {
        super.onCreate()
        Log.d("CounterService", "Service Created")
        
        // Setup Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        // Setup Vibrator
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibrator = vibratorManager.defaultVibrator
        
        // Load saved count
        val prefs = getSharedPreferences("CounterPrefs", Context.MODE_PRIVATE)
        count = prefs.getInt("count", 0)
        
        // Init Glyph
        glyphMatrixManager = GlyphMatrixManager.getInstance(applicationContext)
        glyphMatrixManager.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(componentName: android.content.ComponentName) {
                glyphMatrixManager.register("24111") // Registering for P3
                updateMatrix()
            }
            override fun onServiceDisconnected(componentName: android.content.ComponentName) {}
        })
    }

    override fun onBind(intent: Intent?): IBinder? {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        sensorManager.unregisterListener(this)
        return super.onUnbind(intent)
    }

    private fun incrementCount() {
        count++
        if (count > 9999) count = 0
        saveCount()
        vibrate(50)
        updateMatrix()
    }

    private fun resetCount() {
        count = 0
        saveCount()
        vibrate(200)
        updateMatrix()
    }

    private fun saveCount() {
        getSharedPreferences("CounterPrefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("count", count)
            .apply()
    }

    private fun vibrate(duration: Long) {
        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun updateMatrix() {
        Log.d("CounterService", "Count is now: $count")
        try {
            val text = String.format("%04d", count)
            val matrixObject = com.nothing.ketchum.GlyphMatrixObject.Builder()
                .setText(text)
                .setPosition(2, 3) // Specific for Phone 3
                .build()
                
            val frame = com.nothing.ketchum.GlyphMatrixFrame.Builder()
                .addTop(matrixObject)
                .build(applicationContext)
                
            glyphMatrixManager.setMatrixFrame(frame.render())
        } catch (e: Exception) {
            Log.e("CounterService", "Failed to update matrix", e)
        }
    }

    // --- SensorEventListener ---
    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0] / SensorManager.GRAVITY_EARTH
        val y = event.values[1] / SensorManager.GRAVITY_EARTH
        val z = event.values[2] / SensorManager.GRAVITY_EARTH

        val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()
            if (lastShakeTime + SHAKE_SLOP_TIME_MS > now) {
                return
            }
            lastShakeTime = now
            resetCount()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
