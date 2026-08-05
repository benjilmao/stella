package co.stellarskys.stella.features.msc

import co.stellarskys.stella.Stella
import co.stellarskys.stella.annotations.Module
import co.stellarskys.stella.api.handlers.Quasar
import co.stellarskys.stella.api.hypixel.HypixelApi
import co.stellarskys.stella.features.Feature
import co.stellarskys.stella.api.lumina.renderer.ChromaUtils
import co.stellarskys.stella.utils.config
import co.stellarskys.stella.utils.render.Render2D
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.FormattedCharSequence
import tech.thatgravyboat.skyblockapi.utils.extentions.stripColor
import java.awt.Color
import java.util.WeakHashMap

@Module
object Cosmetics : Feature("cosmetics") {
    private val sequenceCache = WeakHashMap<String, FormattedCharSequence>()
    private val nameCache = mutableMapOf<String, NameData>()
    override fun initialize() { updateNames() }

    val selfEnabled by config.property<Boolean>("selfCosmetic.enabled")
    val selfName by config.property<String>("selfCosmetic.name")
    val selfTag by config.property<String>("selfCosmetic.tag")
    val selfMode by config.property<Int>("selfCosmetic.mode")
    val selfColor1 by config.property<Color>("selfCosmetic.color1")
    val selfColor2 by config.property<Color>("selfCosmetic.color2")
    val selfEnable3rd by config.property<Boolean>("selfCosmetic.enable3rd")
    val selfColor3 by config.property<Color>("selfCosmetic.color3")

    fun getSelfNameData(): NameData? {
        if (!selfEnabled) return null
        val playerName = co.stellarskys.stella.api.zenith.client.player?.name?.string
        val targetName = if (selfName.isNotBlank()) selfName else (playerName ?: return null)

        val colors = if (selfMode == 1) {
            val list = mutableListOf(
                String.format("#%06X", selfColor1.rgb and 0xFFFFFF),
                String.format("#%06X", selfColor2.rgb and 0xFFFFFF)
            )
            if (selfEnable3rd) {
                list.add(String.format("#%06X", selfColor3.rgb and 0xFFFFFF))
            }
            list
        } else null

        val extraList = if (selfTag.isNotBlank()) {
            listOf(ExtraPart(text = selfTag, chroma = ChromaData(colors = colors), rainbow = selfMode == 0))
        } else null

        return NameData(
            text = targetName,
            rainbow = selfMode == 0,
            chroma = if (selfMode == 1) ChromaData(colors = colors) else null,
            extra = extraList
        )
    }

    @JvmStatic
    fun handleCharSequence(seq: FormattedCharSequence): FormattedCharSequence {
        if (!isEnabled()) return seq
        val selfData = getSelfNameData()
        if (nameCache.isEmpty() && selfData == null) return seq
        val full = buildString { seq.accept { _, _, cp -> appendCodePoint(cp); true }}

        if (selfData != null && full.contains(selfData.text, ignoreCase = true)) {
            return processWithData(seq, full, selfData.text, selfData)
        }

        if (nameCache.keys.none { full.contains(it, true) }) return seq
        return sequenceCache.computeIfAbsent(full) { process(seq, it) }
    }

    fun processWithData(seq: FormattedCharSequence, full: String, target: String, data: NameData): FormattedCharSequence {
        val parts = full.split(Regex("(?i)$target"), 2)
        val idx = parts[0].codePointCount(0, parts[0].length)
        val targetIdx = target.codePointCount(0, target.length)

        val before = slice(seq, 0, idx)
        val mid = data.getComponent().visualOrderText

        return if (parts.size > 1 && parts[1].isNotEmpty()) {
            val after = slice(seq, idx + targetIdx, Int.MAX_VALUE)
            FormattedCharSequence.composite(before, mid, processWithData(after, parts[1], target, data))
        } else FormattedCharSequence.composite(before, mid)
    }

    fun process(seq: FormattedCharSequence, full: String): FormattedCharSequence {
        val target = nameCache.keys.find { full.contains(it, true) } ?: return seq
        val data = nameCache[target.lowercase()] ?: return seq
        return processWithData(seq, full, target, data)
    }

    fun slice(source: FormattedCharSequence, start: Int, end: Int) = FormattedCharSequence { sink ->
        var current = 0
        source.accept { index, style, cp ->
            if (current in start..<end) {
                current++
                sink.accept(index, style, cp)
            } else { current++; true }
        }
    }

    fun updateNames() {
        Quasar.fetch<Map<String, NameData>>("${Stella.ETHER}/names.json") { result ->
            result.onSuccess { data ->
                data.forEach { (uuid, ndata) ->
                    HypixelApi.getName(uuid) { name ->
                        name?.let { nameCache[it.lowercase()] = ndata }
                    }
                }
            }.onFailure { Stella.LOGGER.error("Failed to fetch names: ${it.message}") }
        }
    }

    data class ChromaData(
        val mode: String? = null,
        val speed: Float? = null,
        val scale: Float? = null,
        val colors: List<String>? = null,
        val color1: String? = null,
        val color2: String? = null,
        val color3: String? = null
    )

    data class ExtraPart(
        val text: String,
        val color: String? = null,
        val rainbow: Boolean = false,
        val chroma: ChromaData? = null
    )

    data class NameData(
        val text: String,
        val extra: List<ExtraPart>? = null,
        val rainbow: Boolean = false,
        val chroma: ChromaData? = null
    ) {
        fun getComponent(): MutableComponent {
            val base = buildTextComponent(text, rainbow, chroma)
            extra?.forEach { part ->
                base.append(buildTextComponent(part.text, part.rainbow || part.color.equals("rainbow", true) || part.color.equals("chroma", true), part.chroma, part.color))
            }
            return base
        }

        private fun buildTextComponent(txt: String, isRainbow: Boolean, cData: ChromaData?, fallbackColor: String? = null): MutableComponent {
            if (cData != null || isRainbow) {
                val parsedColors = cData?.colors?.mapNotNull { parseHexColor(it) }
                    ?: listOfNotNull(parseHexColor(cData?.color1), parseHexColor(cData?.color2), parseHexColor(cData?.color3)).takeIf { it.isNotEmpty() }

                if (!parsedColors.isNullOrEmpty()) {
                    val spd = cData?.speed ?: 1f
                    val scl = cData?.scale ?: 1f
                    val time = ChromaUtils.currentTime() * spd
                    val step = (0.012f / (ProfileViewer.chromaScale * scl))

                    val comp = Component.literal("")
                    val clean = txt.stripColor()
                    for (i in clean.indices) {
                        val rgb = Render2D.interpolateMultiGradient(parsedColors, time, step, i)
                        comp.append(Component.literal(clean[i].toString()).withColor(rgb))
                    }
                    return comp
                }
                return Render2D.getChromaText(txt).copy()
            }
            val rgb = fallbackColor?.let { parseHexColor(it) } ?: 0xFFFFFF
            return Component.literal(txt).withColor(rgb)
        }

        private fun parseHexColor(hex: String?): Int? {
            if (hex.isNullOrBlank()) return null
            return try {
                val clean = hex.removePrefix("#").removePrefix("0x").trim()
                clean.toInt(16) and 0xFFFFFF
            } catch (e: Exception) { null }
        }
    }
}