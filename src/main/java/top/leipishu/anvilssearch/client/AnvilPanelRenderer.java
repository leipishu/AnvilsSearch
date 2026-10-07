package top.leipishu.anvilssearch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import top.leipishu.anvilssearch.client.theme.AnvilTheme;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;
import top.leipishu.anvilssearch.client.animation.controller.AnvilPanelAnimations;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

public class AnvilPanelRenderer {

    public static final int TAB_BUTTON_WIDTH  = 14;
    public static final int TAB_BUTTON_HEIGHT = 30;

    private final AnvilSidebarPanel panel;

    private int[] tabHitX = new int[0];
    private int[] tabHitW = new int[0];

    public AnvilPanelRenderer(AnvilSidebarPanel panel) {
        this.panel = panel;
    }

    public void render(AnvilSidebarPanel p, GuiGraphics g,
                       int mouseX, int mouseY, float pt) {

        p.clearPendingTooltip();
        ScissorHelper.reset();

        if (!p.isVisible() && !p.isAnimating()) return;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int off = p.getAnimationOffset();
        int px  = p.getPanelX() + off;
        int py  = p.getPanelY();
        int pw  = p.getPanelWidth();
        int ph  = p.getPanelHeight();

        if (px + pw < 0) {
            RenderSystem.enableDepthTest();
            return;
        }

        // 面板底
        AnvilTheme.panelBg(g, px, py, pw, ph);
        // 外框
        g.fill(px, py, px + 1, py + ph, 0x33FFFFFF);
        g.fill(px + pw - 1, py, px + pw, py + ph, 0x22FFFFFF);
        g.fill(px, py, px + pw, py + 1, 0x22FFFFFF);
        g.fill(px, py + ph - 1, px + pw, py + ph, 0x22FFFFFF);

        renderTitleBar(g, px, py, pw, font);
        renderTabBar(g, px, py, pw, mouseX, mouseY, font);

        AnvilTab active = p.getActiveTab();

        int contentTop;
        if (active.wantsSearchBox()) {
            renderSearchBox(g, px, py, pw, mouseX, mouseY, font);
            contentTop = SEARCH_BOX_Y + SEARCH_BOX_H + 4;
        } else {
            contentTop = TAB_ITEM_Y + TAB_ITEM_HEIGHT + 4;
        }
        int contentBottom = ph - 4;

        active.renderContent(g, font, mouseX, mouseY, pt,
                px, py, pw, ph, contentTop, contentBottom);
    }

    private void renderTitleBar(GuiGraphics g, int px, int py, int pw, Font font) {
        g.fill(px + 1, py + 1, px + pw - 2, py + TITLE_BAR_HEIGHT,
                AnvilTheme.SECTION_HEADER_BG);
        g.fill(px + 1, py + TITLE_BAR_HEIGHT - 1,
                px + pw - 2, py + TITLE_BAR_HEIGHT, AnvilTheme.SECTION_BORDER);
        g.drawString(font, "\u00A76Anvil's Search", px + 5, py + 5,
                0xFFFFFF, false);
    }

    private void renderTabBar(GuiGraphics g, int px, int py, int pw,
                              int mouseX, int mouseY, Font font) {
        var tabs = panel.getTabs();
        int n = tabs.size();
        if (tabHitX.length != n) {
            tabHitX = new int[n];
            tabHitW = new int[n];
        }

        int availableW = pw - TAB_START_X * 2;
        int eachW = Math.min(TAB_ITEM_WIDTH, availableW / Math.max(1, n));
        if (eachW < 30) eachW = 30;

        int active = panel.getActiveIndex();

        int ty = py + TAB_ITEM_Y;
        int th = TAB_ITEM_HEIGHT;

        for (int i = 0; i < n; i++) {
            int tx = px + TAB_START_X + i * (eachW + 2);
            int tw = eachW;

            tabHitX[i] = tx;
            tabHitW[i] = tw;

            boolean isActive = (i == active);
            boolean isHover  = mouseX >= tx && mouseX <= tx + tw
                    && mouseY >= ty && mouseY <= ty + th;

            float hoverT = AnvilPanelAnimations.tabHover(i, isHover && !isActive);
            float activeT = isActive ? 1f : 0f;

            String label = tabs.get(i).getLabel().getString();
            AnvilTheme.tabButton(g, font, tx, ty, tw, th, label, hoverT, activeT);
        }

        // 滑动指示器：1px 高金线
        if (active >= 0 && active < n) {
            int targetX = px + TAB_START_X + active * (eachW + 2);
            int indicatorX = (int) AnvilPanelAnimations.tabIndicator(targetX);
            int indicatorY = ty + th - 1;
            g.fill(indicatorX, indicatorY,
                    indicatorX + eachW, indicatorY + 1,
                    AnvilTheme.ACCENT);
        }
    }

    private void renderSearchBox(GuiGraphics g, int px, int py, int pw,
                                 int mouseX, int mouseY, Font font) {
        var box = panel.getSearchBox();
        int boxX = px + 5;
        int boxY = py + SEARCH_BOX_Y;
        int boxW = pw - 10;
        int boxH = SEARCH_BOX_H;

        box.setBounds(boxX, boxY, boxW, boxH);
        box.render(g, mouseX, mouseY, font);

        int lineY = boxY + boxH + 2;
        g.fill(px + 5, lineY, px + pw - 5, lineY + 1,
                AnvilTheme.SECTION_BORDER);
    }

    public boolean handleMouseClicked(AnvilSidebarPanel p,
                                      double mx, double my, int button) {

        for (int i = 0; i < tabHitX.length; i++) {
            if (mx >= tabHitX[i] && mx <= tabHitX[i] + tabHitW[i]
                    && my >= p.getPanelY() + TAB_ITEM_Y
                    && my <= p.getPanelY() + TAB_ITEM_Y + TAB_ITEM_HEIGHT) {
                p.switchTab(i);
                return true;
            }
        }

        var box = p.getSearchBox();
        if (p.getActiveTab().wantsSearchBox() && box.mouseClicked(mx, my, button)) {
            p.setSearchFocused(true);
            return true;
        }

        p.setSearchFocused(false);
        return false;
    }
}