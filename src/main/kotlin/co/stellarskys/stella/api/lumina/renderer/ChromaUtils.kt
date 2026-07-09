package co.stellarskys.stella.api.lumina.renderer

import co.stellarskys.stella.features.msc.ProfileViewer

internal object ChromaUtils {
    const val CYCLE_MS = 3000L

    fun currentTime(): Float {
        val speed = ProfileViewer.chromaSpeed
        if (speed <= 0f) return 0f
        val cycle = (CYCLE_MS / speed).toLong()
        if (cycle == 0L) return 0f
        return (System.currentTimeMillis() % cycle) / cycle.toFloat()
    }

    fun chromaSize(vw: Int): Float = vw * 0.3f * ProfileViewer.chromaScale
}
