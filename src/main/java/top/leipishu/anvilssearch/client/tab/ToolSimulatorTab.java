package top.leipishu.anvilssearch.client.tab;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import top.leipishu.anvilssearch.client.AnvilPanelAnimation;
import top.leipishu.anvilssearch.client.AnvilPanelLayout;
import top.leipishu.anvilssearch.client.AnvilSidebarPanel;
import top.leipishu.anvilssearch.client.AnvilTab;
import top.leipishu.anvilssearch.client.theme.AnvilTheme;
import top.leipishu.anvilssearch.client.widget.MaterialPickerPopup;
import top.leipishu.anvilssearch.client.widget.PartSlotWidget;
import top.leipishu.anvilssearch.client.widget.ToolListWidget;
import top.leipishu.anvilssearch.client.widget.ToolPreviewPanel;
import top.leipishu.anvilssearch.data.material.MaterialDetail;
import top.leipishu.anvilssearch.data.material.MaterialDetailBuilder;
import top.leipishu.anvilssearch.data.material.PartMaterialIndex;
import top.leipishu.anvilssearch.simulation.ToolSimulationModel;
import top.leipishu.anvilssearch.simulation.ToolStatsCalculator;
import top.leipishu.tinkerssearch.client.gui.components.ScrollBar;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;

import java.util.*;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

public class ToolSimulatorTab implements AnvilTab {

    private static final int COL_HEADER_H = AnvilTheme.HEADER_H;

    private final AnvilSidebarPanel panel;
    private final ToolSimulationModel model = new ToolSimulationModel();

    private final ToolListWidget toolList = new ToolListWidget();
    private final ToolPreviewPanel previewPanel = new ToolPreviewPanel();
    private final List<PartSlotWidget> slotWidgets = new ArrayList<>();

    private MaterialPickerPopup popup = null;

    private final Map<Integer, MaterialDetail> cardDetails = new HashMap<>();

    private int midScrollOffset = 0;
    private int midMaxScrollOffset = 0;
    private final ScrollBar midScrollBar = new ScrollBar();

    private int lastAreaTop, lastAreaH;
    private int lastMidX, lastMidW;

    public ToolSimulatorTab(AnvilSidebarPanel panel) {
        this.panel = panel;
        this.toolList.setOnToolPicked(e -> {
            model.selectTool(e.definition);
            slotWidgets.clear();
            cardDetails.clear();
            previewPanel.clearExpandState();
            midScrollOffset = 0;
        });
        this.midScrollBar.setOnOffsetChanged(v -> midScrollOffset = v);
        this.midScrollBar.setThumbMinHeight(16);
        this.midScrollBar.setHoverExpandX(3);
    }

    @Override public Component getLabel() {
        return new TranslatableComponent("gui.anvilssearch.tab.simulator");
    }
    @Override public int getPreferredWidth() { return AnvilPanelAnimation.WIDTH_WIDE; }
    @Override public boolean wantsSearchBox() { return false; }

    @Override
    public void onActivate() { popup = null; }

    @Override
    public List<Component> getPendingTooltip() {
        return previewPanel.getPendingTooltip();
    }

    @Override
    public boolean isAnySearchFocused() {
        return popup != null && popup.isSearchFocused();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (popup != null && popup.isSearchFocused()) {
            return popup.keyPressed(keyCode, scanCode, mods);
        }
        return false;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (popup != null && popup.isSearchFocused()) {
            return popup.charTyped(c, mods);
        }
        return false;
    }

    @Override
    public void renderContent(PoseStack ps, Font font,
                              int mouseX, int mouseY, float partialTick,
                              int px, int py, int pw, int ph,
                              int contentTop, int contentBottom) {

        int contentLeft  = px + 5;
        int contentRight = px + pw - 5;
        int areaTop      = py + contentTop;
        int areaH        = contentBottom - contentTop;

        lastAreaTop = areaTop;
        lastAreaH = areaH;

        AnvilPanelLayout.ThreeColumn cols = AnvilPanelLayout.computeThreeColumn(
                contentLeft, contentRight);

        lastMidX = cols.midX;
        lastMidW = cols.midW;

        // ===== 左：工具列表 section =====
        drawColSection(ps, font,
                cols.leftX, areaTop, cols.leftW, areaH,
                new TranslatableComponent("gui.anvilssearch.sim.tools_header").getString());
        int lInX = cols.leftX + AnvilTheme.PAD_S;
        int lInY = areaTop + COL_HEADER_H + AnvilTheme.PAD_XS;
        int lInW = cols.leftW - AnvilTheme.PAD_S * 2;
        int lInH = areaH - COL_HEADER_H - AnvilTheme.PAD_XS - AnvilTheme.PAD_S;
        toolList.setBounds(lInX, lInY, lInW, lInH);
        toolList.render(ps, font, mouseX, mouseY, model.getSelectedTool());

        // ===== 中：部件槽 section =====
        drawColSection(ps, font,
                cols.midX, areaTop, cols.midW, areaH,
                new TranslatableComponent("gui.anvilssearch.sim.parts_header").getString());
        int mInX = cols.midX + AnvilTheme.PAD_S;
        int mInY = areaTop + COL_HEADER_H + AnvilTheme.PAD_XS;
        int mInW = cols.midW - AnvilTheme.PAD_S * 2;
        int mInH = areaH - COL_HEADER_H - AnvilTheme.PAD_XS - AnvilTheme.PAD_S;
        renderSlots(ps, font, mInX, mInY, mInW, mInH, mouseX, mouseY);

        // ===== 右：预览 section =====
        drawColSection(ps, font,
                cols.rightX, areaTop, cols.rightW, areaH,
                new TranslatableComponent("gui.anvilssearch.sim.preview_header").getString());
        int rInX = cols.rightX + AnvilTheme.PAD_S;
        int rInY = areaTop + COL_HEADER_H + AnvilTheme.PAD_XS;
        int rInW = cols.rightW - AnvilTheme.PAD_S * 2;
        int rInH = areaH - COL_HEADER_H - AnvilTheme.PAD_XS - AnvilTheme.PAD_S;
        previewPanel.setBounds(rInX, rInY, rInW, rInH);
        previewPanel.render(ps, font, model, cardDetails, mouseX, mouseY);

        List<Component> tip = previewPanel.getPendingTooltip();
        if (tip != null && !tip.isEmpty()) {
            panel.setPendingTooltip(tip);
        }

        if (popup != null) popup.render(ps, font, mouseX, mouseY);
    }

    /** 统一栏：section 底 + 顶部 header 条。 */
    private static void drawColSection(PoseStack ps, Font font,
                                       int x, int y, int w, int h, String label) {
        AnvilTheme.section(ps, x, y, w, h);
        GuiComponent.fill(ps, x + 1, y + 1, x + w - 1, y + COL_HEADER_H,
                AnvilTheme.SECTION_HEADER_BG);
        GuiComponent.fill(ps, x + 1, y + COL_HEADER_H,
                x + w - 1, y + COL_HEADER_H + 1, AnvilTheme.SECTION_BORDER);
        font.draw(ps, "\u00A76" + label, x + AnvilTheme.PAD_M,
                AnvilTheme.centeredTextY(y, COL_HEADER_H, font), 0xFFFFFF);
    }

    private void renderSlots(PoseStack ps, Font font,
                             int x, int y, int w, int h,
                             int mouseX, int mouseY) {

        if (model.getSelectedTool() == null) {
            font.draw(ps, new TranslatableComponent(
                            "gui.anvilssearch.sim.pick_tool").getString(),
                    x + 4, y + 4, AnvilTheme.TEXT_DIM);
            return;
        }

        rebuildSlotsIfNeeded();

        int totalH = 0;
        for (int i = 0; i < slotWidgets.size(); i++) {
            totalH += PartSlotWidget.HEIGHT + AnvilTheme.CARD_GAP;
        }

        int areaH = h - AnvilTheme.PAD_XS;
        midMaxScrollOffset = Math.max(0, totalH - areaH + 12);
        if (midScrollOffset > midMaxScrollOffset) midScrollOffset = midMaxScrollOffset;

        int areaW = w - (midMaxScrollOffset > 0
                ? SCROLL_BAR_WIDTH + SCROLL_BAR_PADDING : 0);

        boolean scissorOk = ScissorHelper.enableScissor(x, y, areaW, areaH);
        try {
            if (scissorOk) RenderSystem.disableDepthTest();

            int sy = y - midScrollOffset;
            for (int i = 0; i < slotWidgets.size(); i++) {
                PartSlotWidget slot = slotWidgets.get(i);
                slot.setBounds(x, sy);
                slot.render(ps, font, mouseX, mouseY);
                sy += PartSlotWidget.HEIGHT + AnvilTheme.CARD_GAP;
            }
        } finally {
            if (scissorOk) {
                ScissorHelper.disableScissor();
                RenderSystem.enableDepthTest();
            }
        }

        if (midMaxScrollOffset > 0) {
            midScrollBar.setBounds(x + w - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING,
                    y, SCROLL_BAR_WIDTH, areaH);
            midScrollBar.setRange(midScrollOffset, midMaxScrollOffset);
            midScrollBar.render(ps, mouseX, mouseY);
        }
    }

    private void rebuildSlotsIfNeeded() {
        int n = model.getSlotCount();
        if (slotWidgets.size() == n) {
            for (int i = 0; i < n; i++) {
                MaterialId sel = model.getSelection(i);
                if (sel != null) {
                    slotWidgets.get(i).setMaterialName(
                            MaterialDetailBuilder.materialNameComponent(sel).getString());
                }
            }
            return;
        }

        slotWidgets.clear();
        for (int i = 0; i < n; i++) {
            PartSlotWidget w = new PartSlotWidget();
            w.setLabel(ToolStatsCalculator.getSlotDisplayName(model.getSlot(i)));
            MaterialId sel = model.getSelection(i);
            if (sel != null) {
                w.setMaterialName(MaterialDetailBuilder.materialNameComponent(sel).getString());
            }
            slotWidgets.add(w);
        }
        cardDetails.clear();
        previewPanel.clearExpandState();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (popup != null) {
            if (popup.isPointInside(mx, my)) {
                popup.mouseClicked(mx, my, button);
                return true;
            } else {
                popup = null;
            }
        }

        if (midScrollBar.tryBeginDrag(mx, my)) return true;
        if (previewPanel.tryBeginDrag(mx, my)) return true;
        if (previewPanel.mouseClicked(mx, my, button)) return true;
        if (toolList.mouseClicked(mx, my, button)) return true;

        if (mx >= lastMidX && mx <= lastMidX + lastMidW) {
            int sy = lastAreaTop + COL_HEADER_H + AnvilTheme.PAD_XS
                    + AnvilTheme.PAD_S - midScrollOffset;
            for (int i = 0; i < slotWidgets.size(); i++) {
                if (my >= sy && my <= sy + PartSlotWidget.HEIGHT) {
                    if (button == 1) toggleCard(i);
                    else             openPickerFor(slotWidgets.get(i), i);
                    return true;
                }
                sy += PartSlotWidget.HEIGHT + AnvilTheme.CARD_GAP;
            }
        }
        return false;
    }

    private void toggleCard(int index) {
        if (cardDetails.containsKey(index)) {
            cardDetails.remove(index);
            previewPanel.setSlotExpanded(index, false);
            return;
        }
        if (model.getSelection(index) != null) {
            generateCard(index);
        }
    }

    private void generateCard(int index) {
        MaterialId mat = model.getSelection(index);
        if (mat == null) return;

        ResourceLocation partId = ToolStatsCalculator.getPartItemId(model.getSlot(index));
        Item partItem = partId == null ? null : ForgeRegistries.ITEMS.getValue(partId);

        MaterialStatsId st = null;
        if (partItem instanceof slimeknights.tconstruct.library.tools.part.IMaterialItem mi) {
            try {
                st = top.leipishu.tinkerssearch.recipe.MaterialCompatibility.inferStatType(mi);
            } catch (Throwable ignored) {}
        }

        MaterialDetail d = MaterialDetailBuilder.build(mat, st, partItem);
        if (d != null) {
            cardDetails.put(index, d);
            previewPanel.setSlotExpanded(index, true);
        }
    }

    private void openPickerFor(PartSlotWidget widget, int index) {
        String stPath = "";
        try {
            Object st = model.getSlot(index) != null
                    ? model.getSlot(index).getStatType() : null;
            if (st != null) {
                try {
                    java.lang.reflect.Method m = st.getClass().getMethod("getLocation");
                    Object loc = m.invoke(st);
                    if (loc instanceof ResourceLocation rl) stPath = rl.getPath();
                } catch (Throwable ignored) {}
                if (stPath.isEmpty()) {
                    String s = String.valueOf(st);
                    int colon = s.indexOf(':');
                    stPath = (colon >= 0) ? s.substring(colon + 1) : s;
                }
            }
        } catch (Throwable ignored) {}

        List<PartMaterialIndex.Entry> entries = PartMaterialIndex.forStatType(stPath);
        if (entries.isEmpty()) {
            entries = new ArrayList<>();
            for (PartMaterialIndex.PartEntry pe : PartMaterialIndex.get()) {
                entries.addAll(pe.materials);
            }
        }

        final int idx = index;
        popup = new MaterialPickerPopup(entries, e -> {
            widget.setMaterialName(e.getDisplayName());
            model.setSelection(idx, e.id);
            popup = null;
            generateCard(idx);
        });

        int pxAbs = panel.getPanelX() + panel.getAnimationOffset();
        int pw = panel.getPanelWidth();
        int pPopW = Math.min(180, pw - 20);
        int pPopH = 200;
        popup.setBounds(pxAbs + (pw - pPopW) / 2, panel.getPanelY() + 60, pPopW, pPopH);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (popup != null && popup.isPointInside(mx, my)) {
            return popup.mouseScrolled(mx, my, delta);
        }
        if (previewPanel.isPointInside(mx, my)) {
            return previewPanel.mouseScrolled(mx, my, delta);
        }
        if (mx >= lastMidX && mx <= lastMidX + lastMidW
                && my >= lastAreaTop && my <= lastAreaTop + lastAreaH) {
            if (midMaxScrollOffset <= 0) return false;
            int no = midScrollOffset - (int) (delta * 12);
            no = Math.max(0, Math.min(no, midMaxScrollOffset));
            if (no != midScrollOffset) {
                midScrollOffset = no;
                midScrollBar.setRange(midScrollOffset, midMaxScrollOffset);
                return true;
            }
            return true;
        }
        return toolList.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseDragged(double mx, double my) {
        if (popup != null) return popup.mouseDragged(my);
        if (midScrollBar.isDragging()) return midScrollBar.updateDrag(my);
        if (previewPanel.isDragging()) return previewPanel.mouseDragged(my);
        return toolList.mouseDragged(my);
    }

    @Override
    public void mouseReleased() {
        if (popup != null) popup.mouseReleased();
        midScrollBar.endDrag();
        previewPanel.mouseReleased();
        toolList.mouseReleased();
    }

    @Override
    public boolean isDraggingScrollBar() {
        if (popup != null && popup.isDragging()) return true;
        if (midScrollBar.isDragging()) return true;
        if (previewPanel.isDragging()) return true;
        return toolList.isDragging();
    }
}