package co.stellarskys.stella.features.msc.profileUtils.screen

import co.stellarskys.stella.api.config.ui.Palette
import co.stellarskys.stella.api.config.ui.Palette.withAlpha
import co.stellarskys.stella.api.horizon.mc.BaseElement
import co.stellarskys.stella.api.horizon.animation.AnimType
import co.stellarskys.stella.api.hypixel.SkyblockResponse
import co.stellarskys.stella.api.zenith.client
import co.stellarskys.stella.features.msc.profileUtils.PvScreen
import co.stellarskys.stella.utils.Utils
import net.minecraft.client.gui.GuiGraphicsExtractor
import tech.thatgravyboat.skyblockapi.platform.pushPop

class ProfileDropdown(
    val playerName: String,
    val currentMember: SkyblockResponse.SkyblockMember
): BaseElement() {
    private var isOpen = false
    private val buttonHeight = 15f
    private val PADDING = 10f       // 5px left (text at x=5) + 5px right breathing room
    private val MAX_WIDTH = 130f
    private val MIN_WIDTH = 50f
    private var targetOffset = 0f
    private var scrollOffset by Utils.animate<Float>(0.2, AnimType.EASE_OUT)

    private var dynamicWidth = MIN_WIDTH
    private var dropdownWidth = MIN_WIDTH

    private val maxVisibleItems: Int
        get() = ((rez.scaledHeight - (absoluteY + buttonHeight)) / buttonHeight).toInt().coerceAtLeast(1)

    init {
        this.width = MIN_WIDTH
        this.height = buttonHeight
        this.x = 0f
        this.y = 222f
    }

    override fun render(context: GuiGraphicsExtractor, mouseX: Float, mouseY: Float, delta: Float) {
        val profiles = currentMember.allProfiles ?: return
        if (profiles.isEmpty()) return

        val currentIcon = getProfileIcon(currentMember.profile?.gameMode)
        val currentName = currentMember.profile?.cuteName ?: "Unknown"
        val headerLabel = "$currentIcon§6$currentName"

        val labelWidth = with(ren2d) { headerLabel.width() }
        dynamicWidth = (labelWidth + PADDING).coerceIn(MIN_WIDTH, MAX_WIDTH)
        this.width = dynamicWidth

        dropdownWidth = if (isOpen) {
            val maxLabelWidth = profiles.maxOf { p ->
                val icon = getProfileIcon(p.gameMode)
                with(ren2d) { "$icon§6${p.cuteName ?: "Unknown"}".width() }
            }
            (maxLabelWidth + PADDING).coerceIn(dynamicWidth, MAX_WIDTH)
        } else dynamicWidth

        val bw = dynamicWidth.toInt()
        val dw = dropdownWidth.toInt()

        context.pushPop {
            context.pose().translate(x, y)

            val hovered = isAreaHovered(0f, 0f, dynamicWidth, buttonHeight, mouseX, mouseY)
            ren2d.drawRect(context, 0, 0, bw, buttonHeight.toInt(), Palette.Crust.withAlpha(if (hovered) 200 else 150))
            ren2d.drawHollowRect(context, 0, 0, bw, buttonHeight.toInt(), 1, Palette.Purple)
            ren2d.drawString(context, headerLabel, 5, 4)

            if (isOpen) {
                val maxItems = maxVisibleItems
                val listHeight = profiles.size * buttonHeight
                val viewportHeight = maxItems * buttonHeight

                ren2d.renderScrolled(context, 0, buttonHeight.toInt(), dw, viewportHeight.toInt(), scrollOffset) {
                    profiles.forEachIndexed { index, profile ->
                        val isCurrent = profile == currentMember.profile
                        val itemY = buttonHeight + index * buttonHeight + scrollOffset
                        val itemHovered = isAreaHovered(0f, itemY, dropdownWidth, buttonHeight, mouseX, mouseY)

                        val bgColor = if (isCurrent) Palette.Green.withAlpha(100)
                        else if (itemHovered) Palette.Surface1.withAlpha(200)
                        else Palette.Crust.withAlpha(200)

                        ren2d.drawRect(context, 0, 0, dw, buttonHeight.toInt(), bgColor)
                        ren2d.drawHollowRect(context, 0, 0, dw, buttonHeight.toInt(), 1, Palette.Purple)
                        val icon = getProfileIcon(profile.gameMode)
                        ren2d.drawString(context, "$icon§6${profile.cuteName ?: "Unknown"}", 5, 4)

                        context.pose().translate(0f, buttonHeight)
                    }
                }

                ren2d.drawScrollbar(context, (dropdownWidth + 2).toInt(), buttonHeight.toInt(), viewportHeight.toInt(), scrollOffset, listHeight.toInt(), Palette.Purple)
            }
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        val profiles = currentMember.allProfiles ?: return false

        if (isAreaHovered(0f, 0f, dynamicWidth, buttonHeight, mouseX, mouseY)) {
            isOpen = !isOpen
            targetOffset = 0f
            scrollOffset = 0f
            return true
        }

        if (isOpen) {
            for (i in profiles.indices) {
                val itemY = buttonHeight + i * buttonHeight + scrollOffset
                if (isAreaHovered(0f, itemY, dropdownWidth, buttonHeight, mouseX, mouseY)) {
                    val profile = profiles[i]
                    if (profile != currentMember.profile) {
                        val uuid = currentMember.uuid
                        if (uuid != null) {
                            val newMember = profile.members[uuid.toString().replace("-", "")]
                            if (newMember != null) {
                                newMember.profile = profile
                                newMember.allProfiles = profiles
                                newMember.uuid = uuid
                                PvScreen.open(playerName, newMember)
                            }
                        }
                    }
                    isOpen = false
                    targetOffset = 0f
                    scrollOffset = 0f
                    return true
                }
            }
            isOpen = false
        }
        return false
    }

    override fun mouseScrolled(mouseX: Float, mouseY: Float, amount: Float, horizontalAmount: Float): Boolean {
        if (!isOpen) return false
        val profiles = currentMember.allProfiles ?: return false
        if (profiles.isEmpty()) return false
        if (!isAreaHovered(0f, buttonHeight, dropdownWidth, buttonHeight * maxVisibleItems, mouseX, mouseY)) return false

        val listHeight = profiles.size * buttonHeight
        val viewportHeight = maxVisibleItems * buttonHeight
        targetOffset = ren2d.calculateScroll(targetOffset, amount, listHeight.toInt(), viewportHeight.toInt(), buttonHeight)
        scrollOffset = targetOffset
        return true
    }

    private fun getProfileIcon(gameMode: String?): String {
        return when (gameMode?.lowercase()) {
            "ironman" -> "♻ §r"
            "bingo" -> "Ⓑ §r"
            else -> ""
        }
    }
}