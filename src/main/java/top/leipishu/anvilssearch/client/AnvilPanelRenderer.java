package top.leipishu.anvilssearch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;

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

    public void render(AnvilSidebarPanel p, PoseStack ps,
                       int mouseX, int mouseY, float pt) {

        p.clearPendingTooltip();     // ★ 每帧清空
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

        GuiComponent.fill(ps, px, py, px + pw, py + ph, 0xFF1A1A1A);
        GuiComponent.fill(ps, px, py, px + 1, py + ph, 0x33FFFFFF);
        GuiComponent.fill(ps, px + pw - 1, py, px + pw, py + ph, 0x22FFFFFF);
        GuiComponent.fill(ps, px, py, px + pw, py + 1, 0x22FFFFFF);
        GuiComponent.fill(ps, px, py + ph - 1, px + pw, py + ph, 0x22FFFFFF);

        renderTitleBar(ps, px, py, pw, font);
        renderTabBar(ps, px, py, pw, mouseX, mouseY, font);

        AnvilTab active = p.getActiveTab();

        int contentTop;
        if (active.wantsSearchBox()) {
            renderSearchBox(ps, px, py, pw, mouseX, mouseY, font);
            contentTop = SEARCH_BOX_Y + SEARCH_BOX_H + 4;
        } else {
            contentTop = TAB_ITEM_Y + TAB_ITEM_HEIGHT + 4;
        }
        int contentBottom = ph - 4;

        active.renderContent(ps, font, mouseX, mouseY, pt,
                px, py, pw, ph, contentTop, contentBottom);

        // ★ depth test 交给外层（AnvilsSearch）控制，这里不恢复
        // 外层在渲染完 tooltip 后再 enableDepthTest
    }

    private void renderTitleBar(PoseStack ps, int px, int py, int pw, Font font) {
        GuiComponent.fill(ps, px + 1, py + 1, px + pw - 2, py + TITLE_BAR_HEIGHT, 0xFF2A2A2A);
        font.draw(ps, "\u00A76Anvil's Search", px + 5, py + 5, 0xFFFFFF);
    }

    private void renderTabBar(PoseStack ps, int px, int py, int pw,
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

        for (int i = 0; i < n; i++) {
            int tx = px + TAB_START_X + i * (eachW + 2);
            int ty = py + TAB_ITEM_Y;
            int tw = eachW;
            int th = TAB_ITEM_HEIGHT;

            tabHitX[i] = tx;
            tabHitW[i] = tw;

            boolean isActive = (i == active);
            boolean isHover  = mouseX >= tx && mouseX <= tx + tw
                    && mouseY >= ty && mouseY <= ty + th;

            int bg;
            if (isActive)      bg = 0xFF6A5030;
            else if (isHover)  bg = 0xFF4E4028;
            else               bg = 0xFF3A3020;

            GuiComponent.fill(ps, tx, ty, tx + tw, ty + th, bg);
            if (isActive) {
                GuiComponent.fill(ps, tx, ty + th - 1, tx + tw, ty + th, 0xFFFFAA00);
            }

            Component label = tabs.get(i).getLabel();
            String s = label.getString();
            int textW = font.width(s);
            int color = isActive ? 0xFFFFDD77 : (isHover ? 0xFFDDBB55 : 0xFFAA8844);
            font.draw(ps, s,
                    tx + (tw - textW) / 2,
                    ty + (th - font.lineHeight) / 2 + 1,
                    color);
        }
    }

    private void renderSearchBox(PoseStack ps, int px, int py, int pw,
                                 int mouseX, int mouseY, Font font) {
        var box = panel.getSearchBox();
        int boxX = px + 5;
        int boxY = py + SEARCH_BOX_Y;
        int boxW = pw - 10;
        int boxH = SEARCH_BOX_H;

        box.setBounds(boxX, boxY, boxW, boxH);
        box.render(ps, mouseX, mouseY, font);

        int lineY = boxY + boxH + 2;
        GuiComponent.fill(ps, px + 5, lineY, px + pw - 5, lineY + 1, 0xFF333333);
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