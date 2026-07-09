package co.stellarskys.stella.utils.render

import co.stellarskys.stella.api.handlers.Chronos
import co.stellarskys.stella.api.handlers.Chronos.millis
import co.stellarskys.stella.api.lumina.Lumina
import co.stellarskys.stella.api.zenith.Zenith
import co.stellarskys.stella.api.zenith.client
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import org.joml.Matrix3x2f
import java.awt.Color
import java.util.Optional
import java.util.UUID
import tech.thatgravyboat.skyblockapi.platform.PlayerSkin
import tech.thatgravyboat.skyblockapi.platform.pushPop
import tech.thatgravyboat.skyblockapi.platform.texture
import tech.thatgravyboat.skyblockapi.platform.textureUrl
import tech.thatgravyboat.skyblockapi.utils.extentions.stripColor
import tech.thatgravyboat.skyblockapi.utils.text.TextUtils.splitLines
import co.stellarskys.stella.api.config.ui.Palette
import co.stellarskys.stella.api.lumina.renderer.ChromaUtils
import co.stellarskys.stella.features.msc.ProfileViewer
import net.minecraft.network.chat.TextColor

object Render2D {
    private val textureCache = mutableMapOf<UUID, PlayerSkin>()
    private var lastCacheClear = Chronos.zero
    private val formattingRegex = "(?<!\\\\\\\\)&(?=[0-9a-fk-orz])".toRegex()
    private const val CHROMA_STEP = 0.012f

    fun drawImage(ctx: GuiGraphicsExtractor, image: Identifier?, x: Int, y: Int, width: Int, height: Int) {
        if (image == null) return
        ctx.blit(RenderPipelines.GUI_TEXTURED, image, x, y, 0f, 0f, width, height, width, height, width, height)
    }

    private data class ChromaParams(
        val time: Float,
        val step: Float,
        val sat: Float,
        val bright: Float
    ) {
        fun hueAt(i: Int): Float = ((time - i * step) % 1.0f).let { if (it < 0) it + 1f else it }
        fun rgbAt(i: Int): Int  = net.minecraft.util.Mth.hsvToRgb(hueAt(i), sat, bright)
        fun colorAt(i: Int): Color = Color.getHSBColor(hueAt(i), sat, bright)
    }

    private fun chromaParams() = ChromaParams(
        time  = ChromaUtils.currentTime(),
        step  = CHROMA_STEP / ProfileViewer.chromaScale,
        sat   = ProfileViewer.chromaSaturation,
        bright = ProfileViewer.chromaBrightness
    )

    fun getChromaText(str: String): Component {
        val clean = str.stripColor()
        val comp = Component.literal("")
        val p = chromaParams()
        for (i in clean.indices) {
            comp.append(Component.literal(clean[i].toString()).withColor(p.colorAt(i).rgb))
        }
        return comp
    }

    private fun isSapphire(comp: Component): Boolean {
        return comp.style.color?.value == Palette.Sapphire.rgb and 0xFFFFFF
    }

    fun convertComponentToChroma(comp: Component): Component {
        val clean = comp.string
        val out = Component.literal("")
        val p = chromaParams()
        for (i in clean.indices) {
            out.append(Component.literal(clean[i].toString()).withStyle(comp.style.withColor(TextColor.fromRgb(p.colorAt(i).rgb))))
        }
        return out
    }

    fun parseFormattingToComponent(str: String): Component {
        if (!str.contains('§')) return Component.literal(str)
        val root = Component.literal("")
        val p = chromaParams()
        val parts = str.split('§')
        var currentStyle = net.minecraft.network.chat.Style.EMPTY
        var isChroma = false
        var charCount = 0

        fun appendText(text: String) {
            if (isChroma) {
                for (c in text) {
                    root.append(Component.literal(c.toString()).withStyle(currentStyle.withColor(TextColor.fromRgb(p.rgbAt(charCount)))))
                    charCount++
                }
            } else {
                root.append(Component.literal(text).withStyle(currentStyle))
                charCount += text.length
            }
        }

        for (i in parts.indices) {
            val part = parts[i]
            if (i == 0) {
                if (part.isNotEmpty()) { root.append(Component.literal(part)); charCount += part.length }
                continue
            }
            if (part.isEmpty()) continue

            val code = part[0].lowercaseChar()
            val text = part.substring(1)

            when (code) {
                '0' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0x000000)); isChroma = false }
                '1' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0x0000AA)); isChroma = false }
                '2' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0x00AA00)); isChroma = false }
                '3' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0x00AAAA)); isChroma = false }
                '4' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0xAA0000)); isChroma = false }
                '5' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0xAA00AA)); isChroma = false }
                '6' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0xFFAA00)); isChroma = false }
                '7' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0xAAAAAA)); isChroma = false }
                '8' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0x555555)); isChroma = false }
                '9' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0x5555FF)); isChroma = false }
                'a' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0x55FF55)); isChroma = false }
                'b' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0x55FFFF)); isChroma = false }
                'c' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0xFF5555)); isChroma = false }
                'd' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0xFF55FF)); isChroma = false }
                'e' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0xFFFF55)); isChroma = false }
                'f' -> { currentStyle = currentStyle.withColor(TextColor.fromRgb(0xFFFFFF)); isChroma = false }
                'k' -> { currentStyle = currentStyle.withObfuscated(true) }
                'l' -> { currentStyle = currentStyle.withBold(true) }
                'm' -> { currentStyle = currentStyle.withStrikethrough(true) }
                'n' -> { currentStyle = currentStyle.withUnderlined(true) }
                'o' -> { currentStyle = currentStyle.withItalic(true) }
                'r' -> { currentStyle = net.minecraft.network.chat.Style.EMPTY; isChroma = false }
                'z' -> { isChroma = true }
                else -> { appendText(part); continue }
            }

            if (text.isNotEmpty()) appendText(text)
        }

        return root
    }

    @JvmOverloads
    fun drawRect(ctx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Color = Color.WHITE) {
        if (ProfileViewer.chromaMaxBars && color.rgb and 0xFFFFFF == Palette.Sapphire.rgb and 0xFFFFFF) {
            ctx.drawLumina {
                Lumina.chromaRect(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
            }
        } else {
            ctx.fill(RenderPipelines.GUI, x, y, x + width, y + height, color.rgb)
        }
    }

    @JvmOverloads
    fun drawHollowRect(ctx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, thickness: Int, color: Color = Color.WHITE) {
        if (thickness <= 0) return
        val rgb = color.rgb
        ctx.fill(RenderPipelines.GUI, x, y, x + width, y + thickness, rgb)
        ctx.fill(RenderPipelines.GUI, x, y + height - thickness, x + width, y + height, rgb)
        ctx.fill(RenderPipelines.GUI, x, y + thickness, x + thickness, y + height - thickness, rgb)
        ctx.fill(RenderPipelines.GUI, x + width - thickness, y + thickness, x + width, y + height - thickness, rgb)
    }

    @JvmOverloads
    fun drawString(ctx: GuiGraphicsExtractor, str: String, x: Int, y: Int, scale: Float = 1f, shadow: Boolean = true) {
        val matrices = ctx.pose()
        if (scale != 1f) {
            matrices.pushMatrix()
            matrices.scale(scale, scale)
        }

        ctx.text(
            client.font,
            parseFormattingToComponent(str.replace(formattingRegex, "${ChatFormatting.PREFIX_CODE}")),
            x,
            y,
            -1,
            shadow
        )

        if (scale != 1f) matrices.popMatrix()
    }

    @JvmOverloads
    fun drawString(ctx: GuiGraphicsExtractor, str: Component, x: Int, y: Int, scale: Float = 1f, shadow: Boolean = true) {
        val matrices = ctx.pose()
        if (scale != 1f) {
            matrices.pushMatrix()
            matrices.scale(scale, scale)
        }

        val textToDraw = if (isSapphire(str)) convertComponentToChroma(str) else str

        ctx.text(
            client.font,
            textToDraw,
            x,
            y,
            -1,
            shadow
        )

        if (scale != 1f) matrices.popMatrix()
    }

    @JvmOverloads
    fun drawString(ctx: GuiGraphicsExtractor, str: String, x: Int, y: Int, scale: Float = 1f, color: Color, shadow: Boolean = true) {
        val matrices = ctx.pose()
        if (scale != 1f) {
            matrices.pushMatrix()
            matrices.scale(scale, scale)
        }

        if (color.rgb and 0xFFFFFF == Palette.Sapphire.rgb and 0xFFFFFF) {
            ctx.text(
                client.font,
                getChromaText(str),
                x,
                y,
                -1,
                shadow
            )
        } else {
            ctx.text(
                client.font,
                parseFormattingToComponent(str.replace(formattingRegex, "${ChatFormatting.PREFIX_CODE}")),
                x,
                y,
                color.rgb,
                shadow
            )
        }

        if (scale != 1f) matrices.popMatrix()
    }

    fun renderItem(context: GuiGraphicsExtractor, item: ItemStack, x: Float, y: Float, scale: Float) {
        context.pose().pushMatrix()
        context.pose().translate(x, y)
        context.pose().scale(scale, scale)
        context.item(item, 0, 0)
        context.itemDecorations(client.font, item, 0, 0)
        context.pose().popMatrix()
    }

    fun drawPlayerHead(context: GuiGraphicsExtractor, x: Int, y: Int, size: Int, uuid: UUID) {
        if (lastCacheClear.since.millis > 300000L) {
            textureCache.clear()
            lastCacheClear = Chronos.now
        }

        val textures = textureCache.getOrElse(uuid) {
            val profile = client.connection?.getPlayerInfo(uuid)?.profile
            val skin = if (profile != null) { client.skinManager.get(profile).getNow(Optional.empty()).orElseGet { DefaultPlayerSkin.get(uuid) } } else { DefaultPlayerSkin.get(uuid) }
            val defaultSkin = DefaultPlayerSkin.get(uuid)
            if (skin.texture != defaultSkin.texture) textureCache[uuid] = skin
            skin
        }

        textures.textureUrl
        PlayerFaceExtractor.extractRenderState(context, textures, x, y, size)
    }


    fun String.width(): Int {
        val lines = split('\n')
        return lines.maxOf { client.font.width(it.stripColor()) }
    }

    fun MutableComponent.width(): Int {
        val lines = splitLines()
        return lines.maxOf { client.font.width(it)}
    }

    fun String.height(): Int {
        val lineCount = count { it == '\n' } + 1
        return client.font.lineHeight * lineCount
    }

    fun MutableComponent.height(): Int {
        val lineCount = this.string.count { it == '\n' } + 1
        return client.font.lineHeight * lineCount
    }

    fun GuiGraphicsExtractor.drawLumina(scaled: Boolean = true, flush: Boolean = true, block: (snapshot: Matrix3x2f) -> Unit) {
        val snapshot = Matrix3x2f(this.pose())
        Lumina.push()
        if (scaled) {
            val sf = Zenith.Res.scaleFactor.toFloat() / Lumina.dpr
            Lumina.resetTransform()
            Lumina.setTransform(Matrix3x2f(
                snapshot.m00 * sf, snapshot.m01 * sf,
                snapshot.m10 * sf, snapshot.m11 * sf,
                snapshot.m20 * sf, snapshot.m21 * sf
            ))
        }
        block(snapshot)
        Lumina.pop()
        if (flush) Lumina.flush(this)
    }

    fun renderScrolled(ctx: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, scrollOffset: Float, block: () -> Unit) {
        ctx.enableScissor(x, y, x + width, y + height)
        ctx.pushPop {
            ctx.pose().translate(x.toFloat(), y.toFloat())
            ctx.pose().translate(0f, scrollOffset)
            block()
        }

        ctx.disableScissor()
    }

    fun calculateScroll(currentTarget: Float, amount: Float, totalHeight: Int, viewportHeight: Int, speed: Float = 20f): Float {
        val maxScroll = (totalHeight - viewportHeight).toFloat().coerceAtLeast(0f)
        return (currentTarget + amount * speed).coerceIn(-maxScroll, 0f)
    }

    fun drawScrollbar(ctx: GuiGraphicsExtractor, x: Int, y: Int, viewportHeight: Int, scrollOffset: Float, totalHeight: Int, color: Color) {
        if (totalHeight <= viewportHeight) return
        val barHeight = (viewportHeight.toFloat() / totalHeight) * viewportHeight
        val barY = (-scrollOffset / totalHeight) * viewportHeight
        drawRect(ctx, x, y + barY.toInt(), 1, barHeight.toInt(), color)
    }
}