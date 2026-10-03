package top.leipishu.anvilssearch.client.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;
import top.leipishu.anvilssearch.client.theme.AnvilTheme;
import top.leipishu.anvilssearch.data.tool.ToolDefinitionIndex;
import top.leipishu.anvilssearch.simulation.ToolPreset;
import top.leipishu.tinkerssearch.client.gui.components.CardBackground;
import top.leipishu.tinkerssearch.client.gui.components.ScrollBar;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static top.leipishu.tinkerssearch.config.PanelConfig.SCROLL_BAR_PADDING;
import static top.leipishu.tinkerssearch.config.PanelConfig.SCROLL_BAR_WIDTH;

public class ToolPresetBrowserPopup {

    public interface OnPickPreset      { void onPick(ToolPreset preset); }
    public interface OnClipboardImport { void onImport(); }
    public interface OnFileImport      { void onImport(); }

    private static final int ROW_H     = 28;
    private static final int PAD       = 6;
    private static final int HEADER_H  = 42;
    private static final int ICON_SIZE = 16;
    private static final int BTN_H     = 18;
    private static final int BTN_W     = 76;
    private static final int BTN_GAP   = 6;

    private int x, y, w, h;

    private final List<ToolPreset>  presets;
    private final OnPickPreset      onPick;
    private final OnClipboardImport onClipboard;
    private final OnFileImport      onFile;

    private final ScrollBar scrollBar = new ScrollBar();
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;

    private int clipboardBtnX, fileBtnX, btnY;

    public ToolPresetBrowserPopup(List<ToolPreset> presets,
                                  OnPickPreset onPick,
                                  OnClipboardImport onClipboard,
                                  OnFileImport onFile) {
        this.presets = presets;
        this.onPick = onPick;
        this.onClipboard = onClipboard;
        this.onFile = onFile;

        scrollBar.setOnOffsetChanged(v -> scrollOffset = v);
        scrollBar.setThumbMinHeight(12);
        scrollBar.setHoverExpandX(2);
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    public boolean isPointInside(double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public void render(PoseStack ps, Font font, int mouseX, int mouseY) {
        GuiComponent.fill(ps, x, y, x + w, y + h, 0xF01A1A1A);
        CardBackground.draw(ps, x, y, w, h, 0xF01A1A1A, AnvilTheme.SECTION_BORDER);

        font.draw(ps, new TranslatableComponent(
                        "gui.anvilssearch.sim.browser.title").getString(),
                x + PAD, y + PAD + 1, AnvilTheme.ACCENT);

        btnY = y + PAD + 15;
        clipboardBtnX = x + PAD;
        fileBtnX = clipboardBtnX + BTN_W + BTN_GAP;

        boolean h1 = mouseX >= clipboardBtnX && mouseX <= clipboardBtnX + BTN_W
                && mouseY >= btnY && mouseY <= btnY + BTN_H;
        boolean h2 = mouseX >= fileBtnX && mouseX <= fileBtnX + BTN_W
                && mouseY >= btnY && mouseY <= btnY + BTN_H;

        AnvilTheme.button(ps, font, clipboardBtnX, btnY, BTN_W, BTN_H,
                new TranslatableComponent(
                        "gui.anvilssearch.sim.browser.from_clipboard").getString(),
                h1, false);
        AnvilTheme.button(ps, font, fileBtnX, btnY, BTN_W, BTN_H,
                new TranslatableComponent(
                        "gui.anvilssearch.sim.browser.from_file").getString(),
                h2, false);

        GuiComponent.fill(ps, x + PAD, btnY + BTN_H + 4,
                x + w - PAD, btnY + BTN_H + 5, AnvilTheme.SECTION_BORDER);

        int listX = x + PAD;
        int listY = y + HEADER_H + BTN_H + 2;
        int listW = w - PAD * 2 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;
        int listH = h - (listY - y) - PAD;
        if (listH <= 0 || listW <= 0) return;

        int totalH = presets.size() * ROW_H;
        maxScrollOffset = Math.max(0, totalH - listH);
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;

        boolean scissorOk = ScissorHelper.enableScissor(listX, listY, listW, listH);
        try {
            if (scissorOk) RenderSystem.disableDepthTest();

            if (presets.isEmpty()) {
                font.draw(ps, new TranslatableComponent(
                                "gui.anvilssearch.sim.browser.empty").getString(),
                        listX + 4, listY + 4, AnvilTheme.TEXT_DIM);
            } else {
                int rowY = listY - scrollOffset;
                for (ToolPreset p : presets) {
                    if (rowY + ROW_H >= listY && rowY <= listY + listH) {
                        drawRow(ps, font, listX, rowY, listW, p, mouseX, mouseY);
                    }
                    rowY += ROW_H;
                }
            }
        } finally {
            if (scissorOk) {
                ScissorHelper.disableScissor();
                RenderSystem.enableDepthTest();
            }
        }

        if (maxScrollOffset > 0) {
            scrollBar.setBounds(x + w - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING,
                    listY, SCROLL_BAR_WIDTH, listH);
            scrollBar.setRange(scrollOffset, maxScrollOffset);
            scrollBar.render(ps, mouseX, mouseY);
        }
    }

    private void drawRow(PoseStack ps, Font font, int x, int y, int w,
                         ToolPreset p, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + w
                && mouseY >= y && mouseY <= y + ROW_H;

        AnvilTheme.row(ps, x, y, w, ROW_H, hover, false);

        ToolDefinitionIndex.Entry def = findDef(p.toolId);

        if (def != null && def.item != null) {
            try {
                Minecraft.getInstance().getItemRenderer()
                        .renderGuiItem(new ItemStack(def.item), x + 6, y + 6);
            } catch (Throwable ignored) {}
        }

        String name = def != null ? def.getDisplayName() : p.toolId;
        String sub = p.materials.size()
                + " " + new TranslatableComponent(
                "gui.anvilssearch.sim.browser.parts_suffix").getString();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm", Locale.ROOT);
            sub += " · " + sdf.format(new Date(p.savedAt));
        } catch (Throwable ignored) {}

        int tx = x + 6 + ICON_SIZE + 8;
        font.draw(ps, name, tx, y + 4,
                hover ? AnvilTheme.TEXT_PRIMARY : AnvilTheme.TEXT_SECONDARY);
        font.draw(ps, "\u00A78" + sub, tx, y + 4 + font.lineHeight + 2,
                AnvilTheme.TEXT_DIM);
    }

    private static ToolDefinitionIndex.Entry findDef(String toolId) {
        if (toolId == null) return null;
        for (ToolDefinitionIndex.Entry e : ToolDefinitionIndex.get()) {
            if (e.id != null && e.id.toString().equals(toolId)) return e;
        }
        return null;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (scrollBar.tryBeginDrag(mx, my)) return true;
        if (!isPointInside(mx, my)) return false;

        if (my >= btnY && my <= btnY + BTN_H) {
            if (mx >= clipboardBtnX && mx <= clipboardBtnX + BTN_W) {
                if (onClipboard != null) onClipboard.onImport();
                return true;
            }
            if (mx >= fileBtnX && mx <= fileBtnX + BTN_W) {
                if (onFile != null) onFile.onImport();
                return true;
            }
        }

        int listX = x + PAD;
        int listY = y + HEADER_H + BTN_H + 2;
        int listW = w - PAD * 2 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;
        if (mx < listX || mx > listX + listW) return true;

        int rowY = listY - scrollOffset;
        for (ToolPreset p : presets) {
            if (my >= rowY && my <= rowY + ROW_H) {
                if (onPick != null) onPick.onPick(p);
                return true;
            }
            rowY += ROW_H;
        }
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maxScrollOffset <= 0) return false;
        int no = scrollOffset - (int) (delta * 12);
        no = Math.max(0, Math.min(no, maxScrollOffset));
        if (no != scrollOffset) {
            scrollOffset = no;
            scrollBar.setRange(scrollOffset, maxScrollOffset);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double my) { return scrollBar.updateDrag(my); }
    public void mouseReleased() { scrollBar.endDrag(); }
    public boolean isDragging() { return scrollBar.isDragging(); }
}