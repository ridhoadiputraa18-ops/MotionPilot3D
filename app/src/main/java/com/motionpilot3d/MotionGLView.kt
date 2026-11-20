package com.motionpilot3d

import android.content.Context
import android.opengl.GLSurfaceView

class MotionGLView(context: Context) : GLSurfaceView(context) {
    private val flightRenderer = GameRenderer()
    init {
        setEGLContextClientVersion(2)
        setRenderer(flightRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
    fun setFlightInput(roll: Float, pitch: Float) = flightRenderer.setFlightInput(roll, pitch)
}
