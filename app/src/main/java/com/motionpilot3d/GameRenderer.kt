package com.motionpilot3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.sin
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Motion Pilot 3D - Combat prototype.
 * Controls remain supplied by MainActivity through setFlightInput().
 */
class GameRenderer : GLSurfaceView.Renderer {
    private val vertexShader = """
        uniform mat4 uMvp;
        attribute vec3 aPosition;
        void main() { gl_Position = uMvp * vec4(aPosition, 1.0); }
    """
    private val fragmentShader = """
        precision mediump float;
        uniform vec4 uColor;
        void main() { gl_FragColor = uColor; }
    """

    private var program = 0
    @Volatile private var roll = 0f
    @Volatile private var pitch = 0f

    private var elapsed = 0f
    private var lastFrameNs = 0L
    private var score = 0
    private var health = 100f
    private var fireClock = 0f
    private var damageClock = 0f

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mv = FloatArray(16)
    private val mvp = FloatArray(16)
    private val identity = FloatArray(16)

    private data class Enemy(var x: Float, var y: Float, var z: Float, var alive: Boolean = true)
    private data class Shot(var x: Float, var y: Float, var z: Float)

    private val enemies = mutableListOf(
        Enemy(-1.2f, 0.4f, 11f),
        Enemy(1.3f, -0.2f, 15f),
        Enemy(0.2f, 0.8f, 19f)
    )
    private val shots = mutableListOf<Shot>()

    // A low-poly fighter mesh: nose points toward +Z.
    private val fuselage = floatArrayOf(
        0f,0f,1.35f, -0.13f,0f,-0.75f, 0.13f,0f,-0.75f,
        0f,0f,1.35f, 0.13f,0f,-0.75f, 0f,0f,-0.95f,
        0f,0.10f,0.55f, -0.13f,0f,-0.75f, 0.13f,0f,-0.75f
    )
    private val wings = floatArrayOf(
        -0.10f,0f,0.42f, -1.65f,0f,-0.42f, -0.30f,0f,-0.48f,
         0.10f,0f,0.42f,  0.30f,0f,-0.48f,  1.65f,0f,-0.42f,
        -0.10f,0f,0.42f, -0.30f,0f,-0.48f, 0.10f,0f,0.42f,
         0.10f,0f,0.42f,  0.30f,0f,-0.48f, -0.10f,0f,0.42f
    )
    private val tail = floatArrayOf(
        0f,0f,-0.48f, -0.62f,0f,-1.05f, 0f,0f,-0.88f,
        0f,0f,-0.48f, 0f,0f,-0.88f, 0.62f,0f,-1.05f,
        0f,0f,-0.45f, 0f,0.62f,-0.95f, 0f,0f,-1.02f
    )
    private val canopy = floatArrayOf(
        0f,0.12f,0.72f, -0.10f,0.04f,0.08f, 0.10f,0.04f,0.08f,
        0f,0.12f,0.72f, 0.10f,0.04f,0.08f, 0f,0.04f,-0.18f
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

    fun setFlightInput(r: Float, p: Float) {
        roll = r.coerceIn(-1f, 1f)
        pitch = p.coerceIn(-1f, 1f)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.25f, 0.58f, 0.86f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        program = createProgram(vertexShader, fragmentShader)
        Matrix.setIdentityM(identity, 0)
        lastFrameNs = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 62f, width.toFloat() / height.coerceAtLeast(1), 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 0.016f else
            ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now
        elapsed += dt
        fireClock += dt
        damageClock += dt

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        Matrix.setLookAtM(view, 0, 0f, 2.0f, -6.0f, 0f, 0f, 2.5f, 0f, 1f, 0f)

        // Player aircraft follows the phone's tilt.
        val playerX = roll * 1.35f
        val playerY = pitch * 0.78f
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, playerX, playerY - 0.25f, 0f)
        Matrix.rotateM(model, 0, -roll * 38f, 0f, 0f, 1f)
        Matrix.rotateM(model, 0, pitch * 22f, 1f, 0f, 0f)
        drawFighter(model, false)

        // Enemies advance toward the player. Destroyed enemies respawn farther away.
        for ((index, enemy) in enemies.withIndex()) {
            if (enemy.alive) {
                enemy.z -= dt * (2.0f + index * 0.18f)
                enemy.x = sin(elapsed * 0.75f + index * 2.1f) * (1.35f + index * 0.12f)
                enemy.y = sin(elapsed * 0.55f + index) * 0.65f
                if (enemy.z < -1.5f) {
                    enemy.z = 18f + index * 3f
                    health = (health - 12f).coerceAtLeast(0f)
                    damageClock = 0f
                }
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, enemy.x, enemy.y, enemy.z)
                Matrix.rotateM(model, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(model, 0, sin(elapsed + index) * 12f, 0f, 0f, 1f)
                Matrix.scaleM(model, 0, 0.82f, 0.82f, 0.82f)
                drawFighter(model, true)
            } else {
                enemy.z -= dt * 2.5f
                if (enemy.z < -2f) {
                    enemy.alive = true
                    enemy.z = 20f + index * 3f
                }
            }
        }

        // Automatic cannon fire gives the player a simple combat loop.
        if (fireClock >= 0.22f) {
            fireClock = 0f
            shots.add(Shot(playerX, playerY - 0.22f, 0.9f))
        }
        val shotIterator = shots.iterator()
        while (shotIterator.hasNext()) {
            val shot = shotIterator.next()
            shot.z += dt * 19f
            var hit = false
            for (enemy in enemies) {
                if (enemy.alive && abs(shot.z - enemy.z) < 1.5f &&
                    abs(shot.x - enemy.x) < 2.0f && abs(shot.y - enemy.y) < 1.5f) {
                    enemy.alive = false
                    score += 100
                    hit = true
                    break
                }
            }
            if (hit || shot.z > 23f) {
                shotIterator.remove()
            } else {
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, shot.x, shot.y, shot.z)
                Matrix.scaleM(model, 0, 0.055f, 0.055f, 0.55f)
                draw(cube, model, 1f, 0.88f, 0.18f)
            }
        }

        // Small distant cloud puffs made from soft white low-poly blocks.
        for (i in 0..7) {
            val z = 5f + ((i * 4.2f + elapsed * 1.1f) % 25f)
            val x = sin(i * 3.4f) * 5.2f
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, x, -1.7f + sin(i.toFloat()) * 0.35f, z)
            Matrix.scaleM(model, 0, 1.5f, 0.38f, 0.8f)
            draw(cube, model, 0.88f, 0.94f, 1f)
        }

        drawHud()
    }

    private fun drawFighter(base: FloatArray, enemy: Boolean) {
        val fuselageColor = if (enemy) floatArrayOf(0.72f, 0.16f, 0.13f) else floatArrayOf(0.72f, 0.78f, 0.84f)
        val wingColor = if (enemy) floatArrayOf(0.42f, 0.07f, 0.08f) else floatArrayOf(0.28f, 0.36f, 0.43f)
        draw(fuselage, base, fuselageColor[0], fuselageColor[1], fuselageColor[2])
        draw(wings, base, wingColor[0], wingColor[1], wingColor[2])
        draw(tail, base, wingColor[0], wingColor[1], wingColor[2])
        draw(canopy, base, if (enemy) 0.95f else 0.12f, if (enemy) 0.48f else 0.72f, if (enemy) 0.12f else 0.95f)

        // Twin engine housings.
        for (side in listOf(-1f, 1f)) {
            Matrix.setIdentityM(model, 0)
            Matrix.multiplyMM(model, 0, base, 0, model, 0)
            Matrix.translateM(model, 0, side * 0.24f, -0.02f, -0.63f)
            Matrix.scaleM(model, 0, 0.13f, 0.13f, 0.48f)
            draw(cube, model, 0.12f, 0.15f, 0.18f)
        }
    }

    private fun drawHud() {
        // HUD is drawn in clip-space after the 3D scene.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        drawRect(-0.92f, 0.82f, -0.35f, 0.88f, 0.04f, 0.08f, 0.12f, 0.72f)
        drawRect(-0.90f, 0.84f, -0.90f + 0.54f * (health / 100f), 0.86f,
            if (health > 35f) 0.15f else 0.95f, if (health > 35f) 0.92f else 0.18f, 0.28f, 1f)

        // Target reticle.
        val cx = roll * 0.16f
        val cy = -pitch * 0.12f
        drawRect(cx - 0.055f, cy + 0.006f, cx + 0.055f, cy + 0.012f, 0.95f, 0.98f, 1f, 0.9f)
        drawRect(cx - 0.055f, cy - 0.012f, cx + 0.055f, cy - 0.006f, 0.95f, 0.98f, 1f, 0.9f)
        drawRect(cx - 0.004f, cy - 0.055f, cx + 0.004f, cy + 0.055f, 0.95f, 0.98f, 1f, 0.9f)
        drawRect(-0.015f, -0.82f, 0.015f, -0.78f, 1f, 0.78f, 0.12f, 1f)

        // Seven-segment score digits.
        val digits = score.toString().takeLast(5).padStart(5, '0')
        var x = 0.50f
        for (char in digits) {
            drawDigit(char, x, 0.82f)
            x += 0.085f
        }
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawDigit(char: Char, x: Float, y: Float) {
        val segments = when (char) {
            '0' -> "ab cdef".replace(" ", "")
            '1' -> "bc"
            '2' -> "abged"
            '3' -> "abgcd"
            '4' -> "fgbc"
            '5' -> "afgcd"
            '6' -> "afgecd"
            '7' -> "abc"
            '8' -> "abcdefg"
            '9' -> "abfgcd"
            else -> ""
        }
        val w = 0.052f
        val h = 0.012f
        val v = 0.028f
        val color = floatArrayOf(1f, 0.88f, 0.28f)
        if ('a' in segments) drawRect(x, y + 0.045f, x + w, y + 0.045f + h, color[0], color[1], color[2], 1f)
        if ('g' in segments) drawRect(x, y + 0.010f, x + w, y + 0.010f + h, color[0], color[1], color[2], 1f)
        if ('d' in segments) drawRect(x, y - 0.025f, x + w, y - 0.025f + h, color[0], color[1], color[2], 1f)
        if ('f' in segments) drawRect(x - h, y + 0.012f, x, y + 0.012f + v, color[0], color[1], color[2], 1f)
        if ('b' in segments) drawRect(x + w, y + 0.012f, x + w + h, y + 0.012f + v, color[0], color[1], color[2], 1f)
        if ('e' in segments) drawRect(x - h, y - 0.025f, x, y - 0.025f + v, color[0], color[1], color[2], 1f)
        if ('c' in segments) drawRect(x + w, y - 0.025f, x + w + h, y - 0.025f + v, color[0], color[1], color[2], 1f)
    }

    private fun drawRect(left: Float, bottom: Float, right: Float, top: Float,
                          r: Float, g: Float, b: Float, a: Float) {
        val vertices = floatArrayOf(
            left,bottom,0f, right,bottom,0f, right,top,0f,
            left,bottom,0f, right,top,0f, left,top,0f
        )
        val buffer = floatBuffer(vertices)
        Matrix.setIdentityM(mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(program, "uColor"), r, g, b, a)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, buffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices.size / 3)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun draw(vertices: FloatArray, modelMatrix: FloatArray, r: Float, g: Float, b: Float) {
        Matrix.multiplyMM(mv, 0, view, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(program, "uColor"), r, g, b, 1f)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, floatBuffer(vertices))
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices.size / 3)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(values).also { it.position(0) }

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
