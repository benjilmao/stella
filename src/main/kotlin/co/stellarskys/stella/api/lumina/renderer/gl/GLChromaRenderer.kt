package co.stellarskys.stella.api.lumina.renderer.gl

import co.stellarskys.stella.api.lumina.Lumina
import co.stellarskys.stella.api.lumina.renderer.ChromaUtils
import co.stellarskys.stella.features.msc.ProfileViewer
import org.joml.Vector2f
import org.lwjgl.opengl.GL33C
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer

internal object GLChromaRenderer {
    private const val FLOATS = 7
    private const val MAX_VERTS = 65536

    private var program = 0; private var vao = 0; private var vbo = 0
    private var uProjection = -1; private var uTime = -1
    private var uChromaSize = -1; private var uSaturation = -1
    private var initialized = false
    private var buf: FloatBuffer = MemoryUtil.memAllocFloat(MAX_VERTS * FLOATS)
    private var vCount = 0

    fun render(shapes: List<Lumina.ChromaShape>, vw: Int, vh: Int) {
        if (shapes.isEmpty()) return
        ensureInit()
        vCount = 0; buf.clear()

        val brightness = ProfileViewer.chromaBrightness
        for (s in shapes) quad(s, brightness)
        if (vCount == 0) return

        val state = GLUtils.saveGLState()
        GL33C.glUseProgram(program); GL33C.glBindVertexArray(vao)
        GL33C.glEnable(GL33C.GL_BLEND); GL33C.glDisable(GL33C.GL_DEPTH_TEST); GL33C.glDisable(GL33C.GL_CULL_FACE)
        GL33C.glBlendFunc(GL33C.GL_ONE, GL33C.GL_ONE_MINUS_SRC_ALPHA)
        GL33C.glUniformMatrix4fv(uProjection, false, GLUtils.orthoProjection(vw, vh))
        GL33C.glUniform1f(uTime, ChromaUtils.currentTime())
        GL33C.glUniform1f(uChromaSize, ChromaUtils.chromaSize(vw))
        GL33C.glUniform1f(uSaturation, ProfileViewer.chromaSaturation)
        GLUtils.uploadVertices(vbo, buf, vCount, FLOATS)

        var i = 0
        while (i < shapes.size) {
            val sc = shapes[i].scissor
            GLUtils.applyScissor(sc, vh)
            var j = i
            while (j < shapes.size && shapes[j].scissor == sc) j++
            val start = i * 6
            val count = (j - i) * 6
            GL33C.glDrawArrays(GL33C.GL_TRIANGLES, start, count)
            i = j
        }
        state.restore()
    }

    private fun quad(s: Lumina.ChromaShape, brightness: Float) {
        if (vCount + 6 > MAX_VERTS) return
        val tl = s.transform.transformPosition(Vector2f(s.x, s.y))
        val tr = s.transform.transformPosition(Vector2f(s.x + s.w, s.y))
        val bl = s.transform.transformPosition(Vector2f(s.x, s.y + s.h))
        val br = s.transform.transformPosition(Vector2f(s.x + s.w, s.y + s.h))
        val r = 0f; val g = 0f; val b = 0f; val cov = 1f
        emit(tl, r, g, b, brightness, cov); emit(tr, r, g, b, brightness, cov); emit(bl, r, g, b, brightness, cov)
        emit(tr, r, g, b, brightness, cov); emit(br, r, g, b, brightness, cov); emit(bl, r, g, b, brightness, cov)
    }

    private fun emit(p: Vector2f, r: Float, g: Float, b: Float, a: Float, cov: Float) {
        buf.put(p.x).put(p.y).put(r).put(g).put(b).put(a).put(cov); vCount++
    }

    private fun ensureInit() {
        if (initialized) return
        program = GLUtils.linkProgram("shaders/gl/lumina_shape.vsh", "shaders/gl/chroma_shape.fsh", "LuminaChromaGL")
        uProjection = GL33C.glGetUniformLocation(program, "uProjection")
        uTime = GL33C.glGetUniformLocation(program, "uTime")
        uChromaSize = GL33C.glGetUniformLocation(program, "uChromaSize")
        uSaturation = GL33C.glGetUniformLocation(program, "uSaturation")
        vao = GL33C.glGenVertexArrays(); vbo = GL33C.glGenBuffers()
        GL33C.glBindVertexArray(vao)
        GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, vbo)
        GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, MAX_VERTS.toLong() * FLOATS * 4L, GL33C.GL_DYNAMIC_DRAW)
        val stride = FLOATS * 4
        GL33C.glEnableVertexAttribArray(0); GL33C.glVertexAttribPointer(0, 2, GL33C.GL_FLOAT, false, stride, 0L)
        GL33C.glEnableVertexAttribArray(1); GL33C.glVertexAttribPointer(1, 4, GL33C.GL_FLOAT, false, stride, 8L)
        GL33C.glEnableVertexAttribArray(2); GL33C.glVertexAttribPointer(2, 1, GL33C.GL_FLOAT, false, stride, 24L)
        GL33C.glBindVertexArray(0); GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0)
        initialized = true
    }

    fun destroy() {
        if (!initialized) return
        GL33C.glDeleteProgram(program); GL33C.glDeleteVertexArrays(vao); GL33C.glDeleteBuffers(vbo)
        MemoryUtil.memFree(buf); program = 0; vao = 0; vbo = 0; initialized = false
    }
}
