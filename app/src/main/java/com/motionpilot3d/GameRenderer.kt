package com.motionpilot3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin

class GameRenderer : GLSurfaceView.Renderer {
    private val vs = """
        uniform mat4 uMvp;
        attribute vec3 aPosition;
        void main() { gl_Position = uMvp * vec4(aPosition, 1.0); }
    """
    private val fs = """
        precision mediump float;
        uniform vec4 uColor;
        void main() { gl_FragColor = uColor; }
    """
    private var program = 0
    @Volatile private var roll = 0f
    @Volatile private var pitch = 0f
    private var time = 0f
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mv = FloatArray(16)
    private val mvp = FloatArray(16)

    private val aircraft = floatArrayOf(
        0f,0f,1.2f, -0.16f,0f,-0.45f, 0.16f,0f,-0.45f,
        0f,0f,1.2f, -1.05f,0f,-0.28f, 0f,0f,0.02f,
        0f,0f,1.2f, 0f,0f,0.02f, 1.05f,0f,-0.28f,
        0f,0.08f,-0.35f, -0.42f,0.05f,-0.78f, 0.42f,0.05f,-0.78f
    )
    private val cube = floatArrayOf(
        -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f,
        -0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f,
        -0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,-0.5f,0.5f,
        -0.5f,-0.5f,0.5f, -0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f,
        -0.5f,-0.5f,-0.5f, -0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f,
        -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, 0.5f,-0.5f,-0.5f,
        -0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f,
        -0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f
    )

    fun setFlightInput(r: Float, p: Float) { roll = r; pitch = p }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.35f, 0.68f, 0.92f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        program = createProgram(vs, fs)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h.coerceAtLeast(1))
        Matrix.perspectiveM(projection, 0, 62f, w.toFloat() / h.coerceAtLeast(1), 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        time += 0.016f
        GLES20.glUseProgram(program)
        Matrix.setLookAtM(view, 0, 0f, 2.2f, -5.5f, 0f, 0f, 1.5f, 0f, 1f, 0f)

        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, roll * 1.25f, pitch * 0.75f, 0f)
        Matrix.rotateM(model, 0, -roll * 32f, 0f, 0f, 1f)
        Matrix.rotateM(model, 0, pitch * 16f, 1f, 0f, 0f)
        draw(aircraft, model, 0.88f, 0.91f, 0.96f)

        for (i in 0..5) {
            val z = 5f - ((time * 3f + i * 3.1f) % 18f)
            val x = sin(i * 1.7f + time * 0.15f) * 2f
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, x, sin(i.toFloat()) * 0.5f, z)
            Matrix.scaleM(model, 0, 0.7f, 0.7f, 0.7f)
            draw(cube, model, 0.95f, 0.32f, 0.16f)
        }
    }

    private fun draw(vertices: FloatArray, modelMatrix: FloatArray, r: Float, g: Float, b: Float) {
        val buffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().put(vertices).also { it.position(0) }
        Matrix.multiplyMM(mv, 0, view, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(program, "uColor"), r, g, b, 1f)
        val pos = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, 0, buffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices.size / 3)
        GLES20.glDisableVertexAttribArray(pos)
    }

    private fun shader(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, source)
        GLES20.glCompileShader(id)
        return id
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        val v = shader(GLES20.GL_VERTEX_SHADER, vertex)
        val f = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, v)
            GLES20.glAttachShader(it, f)
            GLES20.glLinkProgram(it)
        }
    }
}
