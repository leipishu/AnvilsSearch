package top.leipishu.anvilssearch.client.theme;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import top.leipishu.tinkerssearch.client.animation.core.ColorUtil;

/**
 * 全局视觉词典。
 * 三个 Tab + 面板渲染都从这里取色、取尺寸、取绘制辅助。
 * 改一处即可全局统一。
 */
public final class AnvilTheme {

    // ===== 底层背景 =====
    public static final int PANEL_BG          = 0xFF161616;
    public static final int SECTION_BG        = 0xFF1B1B1B;
    public static final int SECTION_HEADER_BG = 0xFF242424;
    public static final int SECTION_BORDER    = 0xFF2E2E2E;

    // ===== 强调色 =====
    public static final int ACCENT            = 0xFFFFAA00;
    public static final int ACCENT_SOFT       = 0xFFFFDD77;
    public static final int ACCENT_DARK       = 0xFF6A5030;
    public static final int ACCENT_CYAN       = 0xFF55FFFF;

    // ===== 行叠加色 =====
    public static final int ROW_HOVER         = 0x22FFFFFF;
    public static final int ROW_SELECTED      = 0x44FFAA00;

    // ===== 卡片 =====
    public static final int CARD_BG           = 0xFF1E1E1E;
    public static final int CARD_BORDER       = 0xFF333333;
    public static final int CARD_ACCENT_BAR_W = 2;

    // ===== 槽位 =====
    public static final int SLOT_BG           = 0xFF222222;
    public static final int SLOT_BG_HOVER     = 0xFF303030;
    public static final int SLOT_BORDER       = 0xFF444444;
    public static final int SLOT_BORDER_HOVER = 0xFFAA8844;

    // ===== 按钮 =====
    public static final int BTN_BG            = 0xFF2A2A2A;
    public static final int BTN_BG_HOVER      = 0xFF3A3020;
    public static final int BTN_BG_ACTIVE     = 0xFF6A5030;
    public static final int BTN_BORDER        = 0xFF444444;
    public static final int BTN_TEXT          = 0xFFAAAAAA;
    public static final int BTN_TEXT_HOVER    = 0xFFFFFFFF;
    public static final int BTN_TEXT_ACTIVE   = 0xFFFFDD77;

    // ===== 文字 =====
    public static final int TEXT_PRIMARY      = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY    = 0xFFCCCCCC;
    public static final int TEXT_MUTED        = 0xFF888888;
    public static final int TEXT_DIM          = 0xFF666666;

    // ===== 尺寸 =====
    public static final int PAD_XS = 2;
    public static final int PAD_S  = 4;
    public static final int PAD_M  = 6;
    public static final int PAD_L  = 8;

    public static final int ROW_H      = 18;
    public static final int SUB_ROW_H  = 16;
    public static final int LINE_H     = 12;
    public static final int HEADER_H   = 16;
    public static final int CARD_PAD   = 8;
    public static final int CARD_GAP   = 6;
    public static final int SLOT_SIZE  = 18;
    public static final int SLOT_GAP   = 3;

    private AnvilTheme() {}

    // ============================================================
    // ===== 绘制辅助 =============================================
    // ============================================================

    public static void fill(PoseStack ps, int x, int y, int w, int h, int color) {
        GuiComponent.fill(ps, x, y, x + w, y + h, color);
    }

    public static void panelBg(PoseStack ps, int x, int y, int w, int h) {
        GuiComponent.fill(ps, x, y, x + w, y + h, PANEL_BG);
    }

    /** 内容分区：深色底 + 1px 边框。 */
    public static void section(PoseStack ps, int x, int y, int w, int h) {
        GuiComponent.fill(ps, x, y, x + w, y + h, SECTION_BG);
        GuiComponent.fill(ps, x, y, x + w, y + 1, SECTION_BORDER);
        GuiComponent.fill(ps, x, y, x + 1, y + h, SECTION_BORDER);
        GuiComponent.fill(ps, x + w - 1, y, x + w, y + h, SECTION_BORDER);
        GuiComponent.fill(ps, x, y + h - 1, x + w, y + h, SECTION_BORDER);
    }

    /** 行：透明背景（让 section 透出），hover / selected 时叠加。 */
    public static void row(PoseStack ps, int x, int y, int w, int h,
                           boolean hover, boolean selected) {
        if (selected) {
            GuiComponent.fill(ps, x, y, x + w, y + h, ROW_SELECTED);
        } else if (hover) {
            GuiComponent.fill(ps, x, y, x + w, y + h, ROW_HOVER);
        }
    }

    /** 展开行的左侧 2px 强调条。 */
    public static void rowAccentBar(PoseStack ps, int x, int y, int h, int color) {
        GuiComponent.fill(ps, x, y, x + 2, y + h, color);
    }

    /** 卡片背景 + 边框 + 左强调条（accent 传 0 则不画强调条）。 */
    public static void cardBg(PoseStack ps, int x, int y, int w, int h, int accent) {
        GuiComponent.fill(ps, x, y, x + w, y + h, CARD_BG);
        GuiComponent.fill(ps, x, y, x + w, y + 1, CARD_BORDER);
        GuiComponent.fill(ps, x, y + h - 1, x + w, y + h, CARD_BORDER);
        GuiComponent.fill(ps, x, y, x + 1, y + h, CARD_BORDER);
        GuiComponent.fill(ps, x + w - 1, y, x + w, y + h, CARD_BORDER);
        if ((accent >>> 24) != 0) {
            GuiComponent.fill(ps, x, y, x + CARD_ACCENT_BAR_W, y + h, accent);
        }
    }

    /** 槽位背景。 */
    public static void slotBg(PoseStack ps, int x, int y, int size, boolean hover) {
        int bg = hover ? SLOT_BG_HOVER : SLOT_BG;
        int border = hover ? SLOT_BORDER_HOVER : SLOT_BORDER;
        GuiComponent.fill(ps, x, y, x + size, y + size, bg);
        GuiComponent.fill(ps, x, y, x + size, y + 1, border);
        GuiComponent.fill(ps, x, y + size - 1, x + size, y + size, border);
        GuiComponent.fill(ps, x, y, x + 1, y + size, border);
        GuiComponent.fill(ps, x + size - 1, y, x + size, y + size, border);
    }

    // ============================================================
    // ===== 按钮（float 版，供动画系统使用）=======================
    // ============================================================

    /**
     * 通用按钮（float 版）：底 + 边框 + 居中文字 + active 底部金线。
     *
     * @param hoverT  0..1 之间的 hover 插值
     * @param activeT 0..1 之间的 active 插值
     */
    public static void button(PoseStack ps, Font font, int x, int y, int w, int h,
                              String label, float hoverT, float activeT) {
        int bg = ColorUtil.lerpARGB(BTN_BG, BTN_BG_HOVER, hoverT);
        bg = ColorUtil.lerpARGB(bg, BTN_BG_ACTIVE, activeT);
        GuiComponent.fill(ps, x, y, x + w, y + h, bg);
        GuiComponent.fill(ps, x, y, x + w, y + 1, BTN_BORDER);
        GuiComponent.fill(ps, x, y + h - 1, x + w, y + h, BTN_BORDER);
        GuiComponent.fill(ps, x, y, x + 1, y + h, BTN_BORDER);
        GuiComponent.fill(ps, x + w - 1, y, x + w, y + h, BTN_BORDER);

        if (activeT > 0.01f) {
            GuiComponent.fill(ps, x + 1, y + h - 2, x + w - 1, y + h - 1,
                    ColorUtil.withAlphaFactor(ACCENT, activeT));
        }

        int textColor = ColorUtil.lerpARGB(BTN_TEXT, BTN_TEXT_HOVER, hoverT);
        textColor = ColorUtil.lerpARGB(textColor, BTN_TEXT_ACTIVE, activeT);
        int tw = font.width(label);
        font.draw(ps, label, x + (w - tw) / 2,
                y + (h - font.lineHeight) / 2 + 1, textColor);
    }

    /** 通用按钮（boolean 兼容版）。 */
    public static void button(PoseStack ps, Font font, int x, int y, int w, int h,
                              String label, boolean hover, boolean active) {
        button(ps, font, x, y, w, h, label,
                hover ? 1f : 0f, active ? 1f : 0f);
    }

    // ============================================================
    // ===== Tab 栏按钮（float 版）================================
    // ============================================================

    /**
     * Tab 栏专用按钮（float 版）：无边框，仅背景 + 文字。
     *
     * <p>不再自己画下边框——由 {@code AnvilPanelRenderer} 统一画滑动指示器，
     * 与 Tinkers' Search 的 Tab 栏保持一致（1px 高金线）。
     */
    public static void tabButton(PoseStack ps, Font font, int x, int y, int w, int h,
                                 String label, float hoverT, float activeT) {
        int bg = ColorUtil.lerpARGB(BTN_BG, BTN_BG_HOVER, hoverT);
        bg = ColorUtil.lerpARGB(bg, BTN_BG_ACTIVE, activeT);
        GuiComponent.fill(ps, x, y, x + w, y + h, bg);

        int textColor = ColorUtil.lerpARGB(BTN_TEXT, BTN_TEXT_HOVER, hoverT);
        textColor = ColorUtil.lerpARGB(textColor, BTN_TEXT_ACTIVE, activeT);
        int tw = font.width(label);
        font.draw(ps, label, x + (w - tw) / 2,
                y + (h - font.lineHeight) / 2 + 1, textColor);
    }

    /** Tab 栏按钮（boolean 兼容版）。 */
    public static void tabButton(PoseStack ps, Font font, int x, int y, int w, int h,
                                 String label, boolean hover, boolean active) {
        tabButton(ps, font, x, y, w, h, label,
                hover ? 1f : 0f, active ? 1f : 0f);
    }

    public static int centeredTextY(int y, int h, Font font) {
        return y + (h - font.lineHeight) / 2 + 1;
    }

    /** 顶部 1px 分割线，用于 section 内的软分割。 */
    public static void dividerTop(PoseStack ps, int x, int y, int w) {
        GuiComponent.fill(ps, x, y, x + w, y + 1, SECTION_BORDER);
    }
}