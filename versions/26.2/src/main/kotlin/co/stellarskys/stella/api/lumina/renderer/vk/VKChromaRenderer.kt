package co.stellarskys.stella.api.lumina.renderer.vk

import co.stellarskys.stella.api.lumina.Lumina
import co.stellarskys.stella.api.lumina.renderer.ChromaUtils
import co.stellarskys.stella.features.msc.ProfileViewer
import org.joml.Vector2f
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK13.*
import java.nio.FloatBuffer

internal object VKChromaRenderer {
    private const val FLOATS = 7
    private const val MAX_VERTS = 65536
    private var buf: FloatBuffer = MemoryUtil.memAllocFloat(MAX_VERTS * FLOATS)
    private var vCount = 0
    private var bufferOffset = 0
    private var vertexBuf: VKUtils.VmaBuffer? = null

    fun init() { vertexBuf = VKUtils.createHostVertexBuffer(MAX_VERTS.toLong() * FLOATS * 4) }
    fun resetFrame() { bufferOffset = 0 }

    fun render(cmd: VkCommandBuffer, shapes: List<Lumina.ChromaShape>, vw: Int, vh: Int) {
        if (shapes.isEmpty()) return
        vCount = 0; buf.clear()

        val brightness = ProfileViewer.chromaBrightness
        for (s in shapes) quad(s, brightness)
        if (vCount == 0) return

        val vb = vertexBuf!!; val byteOffset = bufferOffset.toLong() * FLOATS * 4
        buf.position(0).limit(vCount * FLOATS)
        MemoryUtil.memCopy(MemoryUtil.memAddress(buf), vb.mappedPtr + byteOffset, vCount.toLong() * FLOATS * 4)

        VKBackend.beginRenderPassIfNeeded()
        vkCmdBindVertexBuffers(cmd, 0, longArrayOf(vb.buffer), longArrayOf(byteOffset))
        MemoryStack.stackPush().use { stack ->
            val pc = stack.mallocFloat(19)
            pc.put(VKUtils.orthoProjection(vw, vh))
            pc.put(ChromaUtils.currentTime()).put(ChromaUtils.chromaSize(vw)).put(ProfileViewer.chromaSaturation)
            pc.flip()
            vkCmdPushConstants(cmd, VKPipelineManager.chromaPipelineLayout, VK_SHADER_STAGE_VERTEX_BIT or VK_SHADER_STAGE_FRAGMENT_BIT, 0, pc)
            val viewport = VkViewport.calloc(1, stack)
            viewport[0].x(0f).y(0f).width(vw.toFloat()).height(vh.toFloat()).minDepth(0f).maxDepth(1f)
            vkCmdSetViewport(cmd, 0, viewport)
        }

        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, VKPipelineManager.chromaPipeline)

        var i = 0
        while (i < shapes.size) {
            val sc = shapes[i].scissor
            applyScissor(cmd, sc, vw, vh)
            var j = i
            while (j < shapes.size && shapes[j].scissor == sc) j++
            val start = i * 6
            val count = (j - i) * 6
            vkCmdDraw(cmd, count, 1, start, 0)
            i = j
        }
        bufferOffset += vCount
    }

    private fun applyScissor(cmd: VkCommandBuffer, scissor: Lumina.ScissorRect?, vw: Int, vh: Int) {
        MemoryStack.stackPush().use { stack ->
            val rect = VkRect2D.calloc(1, stack)
            if (scissor != null) {
                rect[0].offset().x(maxOf(0, scissor.x.toInt())).y(maxOf(0, vh - scissor.y.toInt() - scissor.h.toInt()))
                rect[0].extent().width(maxOf(0, scissor.w.toInt())).height(maxOf(0, scissor.h.toInt()))
            } else {
                rect[0].offset().x(0).y(0); rect[0].extent().width(vw).height(vh)
            }
            vkCmdSetScissor(cmd, 0, rect)
        }
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

    fun destroy() { vertexBuf?.let { VKUtils.destroyBuffer(it) }; vertexBuf = null; MemoryUtil.memFree(buf) }
}
