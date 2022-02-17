package com.haishinkit.graphics.gles

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Size
import android.view.Surface
import com.haishinkit.graphics.ImageOrientation
import com.haishinkit.graphics.ResampleFilter
import com.haishinkit.graphics.VideoGravity
import com.haishinkit.lang.Utilize
import com.haishinkit.util.aspectRatio
import com.haishinkit.util.swap
import javax.microedition.khronos.opengles.GL10

internal class GlKernel(
    override var utilizable: Boolean = false
) : Utilize {
    var surface: Surface? = null
        set(value) {
            field = value
            inputSurfaceWindow.setSurface(value)
        }
    var imageOrientation: ImageOrientation = ImageOrientation.UP
        set(value) {
            field = value
            invalidateLayout = true
        }
    var videoGravity: VideoGravity = VideoGravity.RESIZE_ASPECT_FILL
        set(value) {
            field = value
            invalidateLayout = true
        }
    var extent: Size = Size(0, 0)
        set(value) {
            field = value
            invalidateLayout = true
        }
    var resampleFilter: ResampleFilter = ResampleFilter.NEAREST
    var surfaceRotation: Int = Surface.ROTATION_0
        set(value) {
            field = value
            invalidateLayout = true
        }
    private val inputSurfaceWindow: GlWindowSurface = GlWindowSurface()
    private val vertexBuffer = GlUtil.createFloatBuffer(VERTECES)
    private val texCoordBuffer = GlUtil.createFloatBuffer(TEX_COORDS_ROTATION_0)
    private var program = INVALID_VALUE
    private var positionHandle = INVALID_VALUE
    private var texCoordHandle = INVALID_VALUE
    private var textureHandle = INVALID_VALUE
    private var invalidateLayout = true
    private var display = EGL14.EGL_NO_DISPLAY
        set(value) {
            field = value
            inputSurfaceWindow.display = value
        }
    private var context = EGL14.EGL_NO_CONTEXT
        set(value) {
            field = value
            inputSurfaceWindow.context = value
        }

    override fun setUp() {
        if (utilizable) return

        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException()
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw RuntimeException()
        }

        val config = chooseConfig() ?: return
        inputSurfaceWindow.config = config
        context = EGL14.eglCreateContext(
            display,
            config,
            EGL14.EGL_NO_CONTEXT,
            CONTEXT_ATTRIBUTES,
            0
        )
        GlUtil.checkGlError("eglCreateContext")
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, context)

        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GL10.GL_TEXTURE_MIN_FILTER,
            resampleFilter.glValue
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GL10.GL_TEXTURE_MAG_FILTER,
            resampleFilter.glValue
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GL10.GL_TEXTURE_WRAP_S,
            GL10.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GL10.GL_TEXTURE_WRAP_T,
            GL10.GL_CLAMP_TO_EDGE
        )

        program = GlUtil.createProgram(GlShader.VERTEX, GlShader.FRAGMENT)
        positionHandle = GLES20.glGetAttribLocation(program, "position")
        GLES20.glEnableVertexAttribArray(positionHandle)
        texCoordHandle = GLES20.glGetAttribLocation(program, "texcoord")
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        textureHandle = GLES20.glGetAttribLocation(program, "texture")

        utilizable = true
    }

    override fun tearDown() {
        if (!utilizable) return

        program = INVALID_VALUE
        positionHandle = INVALID_VALUE
        texCoordHandle = INVALID_VALUE
        texCoordHandle = INVALID_VALUE

        utilizable = false
    }

    fun render(textureId: Int, textureSize: Size, timestamp: Long) {
        if (invalidateLayout) {
            layout(textureSize)
            invalidateLayout = false
        }

        GLES20.glUseProgram(program)

        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GlUtil.checkGlError("glVertexAttribPointer")

        GLES20.glUniform1i(textureHandle, 0)
        GlUtil.checkGlError("glUniform1i")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GlUtil.checkGlError("glBindTexture")

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glUseProgram(0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

        inputSurfaceWindow.setPresentationTime(timestamp)
        inputSurfaceWindow.swapBuffers()
    }

    private fun layout(newTextureSize: Size) {
        val swapped = if (extent.width < extent.height) {
            newTextureSize.height < newTextureSize.width
        } else {
            newTextureSize.width < newTextureSize.height
        }

        var degrees = when (imageOrientation) {
            ImageOrientation.UP -> 0
            ImageOrientation.DOWN -> 180
            ImageOrientation.LEFT -> 270
            ImageOrientation.RIGHT -> 90
            ImageOrientation.UP_MIRRORED -> 0
            ImageOrientation.DOWN_MIRRORED -> 180
            ImageOrientation.LEFT_MIRRORED -> 270
            ImageOrientation.RIGHT_MIRRORED -> 90
        }
        degrees += when (surfaceRotation) {
            0 -> 0
            1 -> 90
            2 -> 180
            3 -> 270
            else -> 0
        }

        if (degrees.rem(180) == 0 && (imageOrientation == ImageOrientation.RIGHT || imageOrientation == ImageOrientation.RIGHT_MIRRORED)) {
            degrees += 180
        }

        when (degrees.rem(360)) {
            0 -> texCoordBuffer.put(TEX_COORDS_ROTATION_0)
            90 -> texCoordBuffer.put(TEX_COORDS_ROTATION_90)
            180 -> texCoordBuffer.put(TEX_COORDS_ROTATION_180)
            270 -> texCoordBuffer.put(TEX_COORDS_ROTATION_270)
        }

        texCoordBuffer.position(0)
        val textureSize = newTextureSize.swap(swapped)

        when (videoGravity) {
            VideoGravity.RESIZE_ASPECT -> {
                val xRatio = extent.width.toFloat() / textureSize.width.toFloat()
                val yRatio = extent.height.toFloat() / textureSize.height.toFloat()
                if (yRatio < xRatio) {
                    GLES20.glViewport(
                        ((extent.width - textureSize.width * yRatio) / 2).toInt(),
                        0,
                        (textureSize.width * yRatio).toInt(),
                        extent.height
                    )
                } else {
                    GLES20.glViewport(
                        0,
                        ((extent.height - textureSize.height * xRatio) / 2).toInt(),
                        extent.width,
                        (textureSize.height * xRatio).toInt()
                    )
                }
            }
            VideoGravity.RESIZE_ASPECT_FILL -> {
                val iRatio = extent.aspectRatio
                val fRatio = textureSize.aspectRatio
                if (iRatio < fRatio) {
                    GLES20.glViewport(
                        ((extent.width - extent.height * fRatio) / 2).toInt(),
                        0,
                        (extent.height * fRatio).toInt(),
                        extent.height
                    )
                } else {
                    GLES20.glViewport(
                        0,
                        ((extent.height - extent.width / fRatio) / 2).toInt(),
                        extent.width,
                        (extent.width / fRatio).toInt()
                    )
                }
            }
        }
    }

    private fun chooseConfig(): EGLConfig? {
        val attributes: IntArray = CONFIG_ATTRIBUTES_WITH_CONTEXT
        val configs: Array<EGLConfig?> = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(
                display,
                attributes,
                0,
                configs,
                0,
                configs.size,
                numConfigs,
                0
            )
        ) {
            return null
        }
        return configs[0]
    }

    companion object {
        private const val INVALID_VALUE = 0

        private const val EGL_RECORDABLE_ANDROID: Int = 0x3142

        private val CONTEXT_ATTRIBUTES =
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        private val SURFACE_ATTRIBUTES = intArrayOf(EGL14.EGL_NONE)

        private val CONFIG_ATTRIBUTES_WITH_CONTEXT = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        private val VERTECES = floatArrayOf(
            -1.0f, 1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f,
            1.0f, -1.0f, 0.0f
        )
        private val TEX_COORDS_ROTATION_0 = floatArrayOf(
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            1.0f, 1.0f
        )
        private val TEX_COORDS_ROTATION_90 = floatArrayOf(
            1.0f, 0.0f,
            0.0f, 0.0f,
            1.0f, 1.0f,
            0.0f, 1.0f
        )
        private val TEX_COORDS_ROTATION_180 = floatArrayOf(
            1.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            0.0f, 0.0f
        )
        private val TEX_COORDS_ROTATION_270 = floatArrayOf(
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
        )
    }
}