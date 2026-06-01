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
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.ketchum.GlyphToy
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
                1 -> {
                    val dataBundle = msg.data ?: return
                    val event = dataBundle.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
                    
                    if (event == GlyphToy.EVENT_CHANGE) {
                        incrementCount()
                    } else if (event == GlyphToy.EVENT_AOD) {
                        updateMatrix()
                    }
                }
            }
        }
    }
    private val messenger = Messenger(handler)

    override fun onCreate() {
        super.onCreate()
        Log.d("CounterService", "Service Created")
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibrator = vibratorManager.defaultVibrator
        
        val prefs = getSharedPreferences("CounterPrefs", Context.MODE_PRIVATE)
        count = prefs.getInt("count", 0)
        
        glyphMatrixManager = GlyphMatrixManager.getInstance(applicationContext)
        glyphMatrixManager.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(componentName: android.content.ComponentName) {
                when {
                    Common.is25111p() -> glyphMatrixManager.register(Glyph.DEVICE_25111p)
                    Common.is25111() -> glyphMatrixManager.register(Glyph.DEVICE_25111)
                    Common.is24111() -> glyphMatrixManager.register(Glyph.DEVICE_24111)
                    Common.is23112() -> glyphMatrixManager.register(Glyph.DEVICE_23112)
                    Common.is23111() -> glyphMatrixManager.register(Glyph.DEVICE_23111)
                    Common.is23113() -> glyphMatrixManager.register(Glyph.DEVICE_23113)
                    else -> glyphMatrixManager.register(Glyph.DEVICE_24111)
                }
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
        try {
            val text = String.format("%04d", count)
            val matrixObject = GlyphMatrixObject.Builder()
                .setText(text)
                .setPosition(2, 3)
                .build()
                
            val frame = GlyphMatrixFrame.Builder()
                .addTop(matrixObject)
                .build(applicationContext)
                
            glyphMatrixManager.setMatrixFrame(frame.render())
        } catch (e: Exception) {
            Log.e("CounterService", "Failed to update matrix", e)
        }
    }

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
