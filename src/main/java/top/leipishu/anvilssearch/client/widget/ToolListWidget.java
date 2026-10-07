package top.leipishu.anvilssearch.client.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import top.leipishu.anvilssearch.client.AnvilPanelLayout;
import top.leipishu.anvilssearch.data.tool.ToolDefinitionIndex;
import top.leipishu.tinkerssearch.client.gui.components.ScrollBar;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;
import top.leipishu.anvilssearch.client.animation.controller.AnvilWidgetAnimations;
import top.leipishu.tinkerssearch.client.animation.core.ColorUtil;

import java.util.List;

import static top.leipishu.tinkerssearch.config.PanelConfig.SCROLL_BAR_PADDING;
import static top.leipishu.tinkerssearch.config.PanelConfig.SCROLL_BAR_WIDTH;

public class ToolListWidget {

    public interface OnToolPicked {
        void onPick(ToolDefinitionIndex.Entry entry);
    }

    private int x, y, w, h;

    private final ScrollBar scrollBar = new ScrollBar();
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;

    private OnToolPicked onPicked;

    public ToolListWidget() {
        scrollBar.setOnOffsetChanged(v -> scrollOffset = v);
        scrollBar.setThumbMinHeight(16);
        scrollBar.setHoverExpandX(3);
        scrollBar.setAnimationId("anvil.tools.scroll");
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    public void setOnToolPicked(OnToolPicked cb) { this.onPicked = cb; }

    public int getScrollOffset() { return scrollOffset; }
    public boolean isDragging()  { return scrollBar.isDragging(); }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY,
                       Object selectedDefinition) {

        g.fill(x, y, x + w, y + h, 0xFF181818);
        g.fill(x, y, x + w, y + 1, 0xFF333333);

        List<ToolDefinitionIndex.Entry> list = ToolDefinitionIndex.get();

        if (list.isEmpty()) {
            g.drawString(font, "\u00A77"
                            + Component.translatable(
                            "gui.anvilssearch.tools.empty").getString(),
                    x + 4, y + 4, 0x666666, false);
            return;
        }

        int totalH = list.size() * AnvilPanelLayout.TOOL_ROW_H;
        int areaH = h - 1;
        maxScrollOffset = Math.max(0, totalH - areaH);
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;

        int areaW = w - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;

        boolean scissorOk = ScissorHelper.enableScissor(x, y + 1, areaW, areaH);
        try {
            if (scissorOk) RenderSystem.disableDepthTest();

            int rowY = y + 1 - scrollOffset;
            for (ToolDefinitionIndex.Entry e : list) {
                boolean hover = mouseY >= rowY
                        && mouseY <= rowY + AnvilPanelLayout.TOOL_ROW_H
                        && mouseX >= x && mouseX <= x + areaW;

                boolean selected = (e.definition == selectedDefinition);

                String rowKey = e.id != null ? e.id.toString()
                        : String.valueOf(e.hashCode());
                float hoverT = AnvilWidgetAnimations.rowHover("tool:" + rowKey, hover);
                float selectedT = AnvilWidgetAnimations.rowSelected("tool:" + rowKey, selected);

                int bg = ColorUtil.lerpARGB(0, 0xFF3A3A3A, hoverT);
                bg = ColorUtil.lerpARGB(bg, 0xFF334422, selectedT);
                if ((bg >>> 24) != 0) {
                    g.fill(x, rowY, x + areaW,
                            rowY + AnvilPanelLayout.TOOL_ROW_H, bg);
                }

                int textColor = ColorUtil.lerpARGB(0xFFCCCCCC, 0xFFFFFFFF, hoverT);
                textColor = ColorUtil.lerpARGB(textColor, 0xFFCCFFCC, selectedT);
                g.drawString(font, e.getDisplayName(), x + 4, rowY + 3, textColor, false);

                rowY += AnvilPanelLayout.TOOL_ROW_H;
            }
        } finally {
            if (scissorOk) {
                ScissorHelper.disableScissor();
                RenderSystem.enableDepthTest();
            }
        }

        scrollBar.setBounds(x + w - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING,
                y + 1, SCROLL_BAR_WIDTH, areaH);
        scrollBar.setRange(scrollOffset, maxScrollOffset);
        scrollBar.render(g, mouseX, mouseY);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (scrollBar.tryBeginDrag(mx, my)) return true;
        if (mx < x || mx > x + w || my < y || my > y + h) return false;

        List<ToolDefinitionIndex.Entry> list = ToolDefinitionIndex.get();
        int rowY = y + 1 - scrollOffset;
        for (ToolDefinitionIndex.Entry e : list) {
            if (my >= rowY && my <= rowY + AnvilPanelLayout.TOOL_ROW_H) {
                if (onPicked != null) onPicked.onPick(e);
                return true;
            }
            rowY += AnvilPanelLayout.TOOL_ROW_H;
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < x || mx > x + w || my < y || my > y + h) return false;
        if (maxScrollOffset <= 0) return false;

        int no = scrollOffset - (int) (delta * 16);
        no = Math.max(0, Math.min(no, maxScrollOffset));
        if (no != scrollOffset) {
            scrollOffset = no;
            scrollBar.setRange(scrollOffset, maxScrollOffset);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double my) {
        return scrollBar.updateDrag(my);
    }

    public void mouseReleased() {
        scrollBar.endDrag();
    }
}