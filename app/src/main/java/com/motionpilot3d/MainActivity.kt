package com.motionpilot3d

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager

class MainActivity : Activity(), SensorEventListener {
    private lateinit var manager: SensorManager
    private lateinit var game: MotionGLView
    private var roll = 0f
    private var pitch = 0f
    private var lastGyro = 0L
    private var gyroRoll = 0f
    private var gyroPitch = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        game = MotionGLView(this)
        setContentView(game)
        manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    override fun onResume() {
        super.onResume()
        game.onResume()
        manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        manager.unregisterListener(this)
        game.onPause()
        super.onPause()
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = e.values[0] / SensorManager.GRAVITY_EARTH
                val ay = e.values[1] / SensorManager.GRAVITY_EARTH
                roll = roll * 0.88f + (-ax).coerceIn(-1f, 1f) * 0.12f
                pitch = pitch * 0.88f + ay.coerceIn(-1f, 1f) * 0.12f
            }
            Sensor.TYPE_GYROSCOPE -> {
                val now = e.timestamp
                if (lastGyro != 0L) {
                    val dt = ((now - lastGyro) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    gyroRoll += e.values[2] * dt * 0.35f
                    gyroPitch += e.values[0] * dt * 0.35f
                }
                lastGyro = now
                roll = (roll * 0.92f + gyroRoll.coerceIn(-1f, 1f) * 0.08f).coerceIn(-1f, 1f)
                pitch = (pitch * 0.92f + gyroPitch.coerceIn(-1f, 1f) * 0.08f).coerceIn(-1f, 1f)
            }
        }
        game.setFlightInput(roll, pitch)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
