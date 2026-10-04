package top.leipishu.anvilssearch.client.tab;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import top.leipishu.anvilssearch.client.AnvilPanelAnimation;
import top.leipishu.anvilssearch.client.AnvilPanelLayout;
import top.leipishu.anvilssearch.client.AnvilSidebarPanel;
import top.leipishu.anvilssearch.client.AnvilTab;
import top.leipishu.anvilssearch.client.SystemFileDialogs;
import top.leipishu.anvilssearch.client.theme.AnvilTheme;
import top.leipishu.anvilssearch.client.widget.MaterialPickerPopup;
import top.leipishu.anvilssearch.client.widget.PartSlotWidget;
import top.leipishu.anvilssearch.client.widget.ToolListWidget;
import top.leipishu.anvilssearch.client.widget.ToolPresetBrowserPopup;
import top.leipishu.anvilssearch.client.widget.ToolPreviewPanel;
import top.leipishu.anvilssearch.data.material.MaterialDetail;
import top.leipishu.anvilssearch.data.material.MaterialDetailBuilder;
import top.leipishu.anvilssearch.data.material.PartMaterialIndex;
import top.leipishu.anvilssearch.data.tool.ToolDefinitionIndex;
import top.leipishu.anvilssearch.simulation.ToolPreset;
import top.leipishu.anvilssearch.simulation.ToolPresetCodec;
import top.leipishu.anvilssearch.simulation.ToolPresetStore;
import top.leipishu.anvilssearch.simulation.ToolSimulationModel;
import top.leipishu.anvilssearch.simulation.ToolStatsCalculator;
import top.leipishu.tinkerssearch.client.gui.components.ScrollBar;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

public class ToolSimulatorTab implements AnvilTab {

    private static final int COL_HEADER_H = 18;

    private static final int TOOLBAR_H       = 16;
    private static final int TOOLBAR_GAP     = 4;
    private static final int TOOLBAR_BTN_W   = 52;
    private static final int TOOLBAR_BTN_GAP = 4;
    private static final long FEEDBACK_MS    = 2500L;

    private final AnvilSidebarPanel panel;
    private final ToolSimulationModel model = new ToolSimulationModel();

    private final ToolListWidget toolList = new ToolListWidget();
    private final ToolPreviewPanel previewPanel = new ToolPreviewPanel();
    private final List<PartSlotWidget> slotWidgets = new ArrayList<>();

    private MaterialPickerPopup popup = null;
    private ToolPresetBrowserPopup presetPopup = null;

    private final Map<Integer, MaterialDetail> cardDetails = new HashMap<>();

    private int midScrollOffset = 0;
    private int midMaxScrollOffset = 0;
    private final ScrollBar midScrollBar = new ScrollBar();

    private int lastAreaTop, lastAreaH;
    private int lastMidX, lastMidW;

    private int toolbarTopY;
    private int toolbarSaveX, toolbarExportX, toolbarImportX;
    private String feedbackText = null;
    private long   feedbackUntil = 0L;

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
    public void onActivate() { popup = null; presetPopup = null; }

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

    // ============================================================
    // ===== 渲染 =================================================
    // ============================================================

    @Override
    public void renderContent(PoseStack ps, Font font,
                              int mouseX, int mouseY, float partialTick,
                              int px, int py, int pw, int ph,
                              int contentTop, int contentBottom) {

        int contentLeft  = px + 5;
        int contentRight = px + pw - 5;

        int toolbarTop = py + contentTop;
        toolbarTopY = toolbarTop;
        renderToolbar(ps, font, contentLeft, toolbarTop,
                contentRight - contentLeft, mouseX, mouseY);

        int areaTop = toolbarTop + TOOLBAR_H + TOOLBAR_GAP;
        int areaH   = (py + contentBottom) - areaTop;
        if (areaH < 20) {
            if (presetPopup != null) presetPopup.render(ps, font, mouseX, mouseY);
            else if (popup != null) popup.render(ps, font, mouseX, mouseY);
            return;
        }

        lastAreaTop = areaTop;
        lastAreaH   = areaH;

        AnvilPanelLayout.ThreeColumn cols = AnvilPanelLayout.computeThreeColumn(
                contentLeft, contentRight);
        lastMidX = cols.midX;
        lastMidW = cols.midW;

        // 左：工具列表
        drawColSection(ps, font,
                cols.leftX, areaTop, cols.leftW, areaH,
                new TranslatableComponent("gui.anvilssearch.sim.tools_header").getString());
        int lInX = cols.leftX + AnvilTheme.PAD_S;
        int lInY = areaTop + COL_HEADER_H + AnvilTheme.PAD_XS;
        int lInW = cols.leftW - AnvilTheme.PAD_S * 2;
        int lInH = areaH - COL_HEADER_H - AnvilTheme.PAD_XS - AnvilTheme.PAD_S;
        toolList.setBounds(lInX, lInY, lInW, lInH);
        toolList.render(ps, font, mouseX, mouseY, model.getSelectedTool());

        // 中：部件槽
        drawColSection(ps, font,
                cols.midX, areaTop, cols.midW, areaH,
                new TranslatableComponent("gui.anvilssearch.sim.parts_header").getString());
        int mInX = cols.midX + AnvilTheme.PAD_S;
        int mInY = areaTop + COL_HEADER_H + AnvilTheme.PAD_XS;
        int mInW = cols.midW - AnvilTheme.PAD_S * 2;
        int mInH = areaH - COL_HEADER_H - AnvilTheme.PAD_XS - AnvilTheme.PAD_S;
        renderSlots(ps, font, mInX, mInY, mInW, mInH, mouseX, mouseY);

        // 右：预览
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
        if (presetPopup != null) presetPopup.render(ps, font, mouseX, mouseY);
    }

    private static void drawColSection(PoseStack ps, Font font,
                                       int x, int y, int w, int h, String label) {
        AnvilTheme.section(ps, x, y, w, h);
        int titleY = y + (COL_HEADER_H - font.lineHeight) / 2;
        font.draw(ps, label, x + AnvilTheme.PAD_M, titleY, AnvilTheme.ACCENT);
        GuiComponent.fill(ps, x + AnvilTheme.PAD_S, y + COL_HEADER_H - 1,
                x + w - AnvilTheme.PAD_S, y + COL_HEADER_H,
                AnvilTheme.SECTION_BORDER);
    }

    // ============================================================
    // ===== 顶部工具栏 ===========================================
    // ============================================================

    private void renderToolbar(PoseStack ps, Font font,
                               int x, int y, int w,
                               int mouseX, int mouseY) {

        toolbarSaveX   = x;
        toolbarExportX = x + TOOLBAR_BTN_W + TOOLBAR_BTN_GAP;
        toolbarImportX = x + (TOOLBAR_BTN_W + TOOLBAR_BTN_GAP) * 2;

        // ★ 判断是否可保存/导出
        boolean canSave = model.getSelectedTool() != null && model.isComplete();

        boolean h1 = inRect(mouseX, mouseY, toolbarSaveX,   y, TOOLBAR_BTN_W, TOOLBAR_H);
        boolean h2 = inRect(mouseX, mouseY, toolbarExportX, y, TOOLBAR_BTN_W, TOOLBAR_H);
        boolean h3 = inRect(mouseX, mouseY, toolbarImportX, y, TOOLBAR_BTN_W, TOOLBAR_H);

        // 保存按钮：不可用时禁用（灰显），hover 仍显示提示
        AnvilTheme.button(ps, font, toolbarSaveX, y, TOOLBAR_BTN_W, TOOLBAR_H,
                new TranslatableComponent("gui.anvilssearch.sim.save").getString(),
                canSave && h1, false);
        if (h1) {
            tooltip(canSave
                    ? "gui.anvilssearch.sim.save.tip"
                    : "gui.anvilssearch.sim.export.incomplete");
        }

        AnvilTheme.button(ps, font, toolbarExportX, y, TOOLBAR_BTN_W, TOOLBAR_H,
                new TranslatableComponent("gui.anvilssearch.sim.export").getString(),
                canSave && h2, false);
        if (h2) {
            tooltip(canSave
                    ? "gui.anvilssearch.sim.export.tip"
                    : "gui.anvilssearch.sim.export.incomplete");
        }

        AnvilTheme.button(ps, font, toolbarImportX, y, TOOLBAR_BTN_W, TOOLBAR_H,
                new TranslatableComponent("gui.anvilssearch.sim.import").getString(),
                h3, false);
        if (h3) tooltip("gui.anvilssearch.sim.import.tip");

        if (feedbackText != null && System.currentTimeMillis() < feedbackUntil) {
            int fx = toolbarImportX + TOOLBAR_BTN_W + 8;
            int fy = y + (TOOLBAR_H - font.lineHeight) / 2;
            font.draw(ps, feedbackText, fx, fy, AnvilTheme.ACCENT_SOFT);
        }
    }

    private void tooltip(String key) {
        List<Component> tip = new ArrayList<>();
        tip.add(new TranslatableComponent(key));
        panel.setPendingTooltip(tip);
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void setFeedback(Component c) {
        this.feedbackText = (c == null) ? null : c.getString();
        this.feedbackUntil = System.currentTimeMillis() + FEEDBACK_MS;
    }

    // ============================================================
    // ===== 保存 / 导出 / 导入 ===================================
    // ============================================================

    /**
     * 构建当前预设。
     * ★ 若工具未选或部件未填满，返回 null。
     */
    private ToolPreset buildCurrentPreset() {
        ToolDefinition def = model.getSelectedTool();
        if (def == null || def.getId() == null) return null;
        if (!model.isComplete()) return null;   // 部件不全

        Map<Integer, MaterialId> mats = new LinkedHashMap<>();
        for (int i = 0; i < model.getSlotCount(); i++) {
            MaterialId m = model.getSelection(i);
            if (m != null) mats.put(i, m);
        }
        return new ToolPreset(def.getId().toString(), mats, System.currentTimeMillis());
    }

    /** 判断当前是否可以保存/导出。 */
    private boolean canExportOrSave() {
        return model.getSelectedTool() != null && model.isComplete();
    }

    private void doSave() {
        if (!canExportOrSave()) {
            setFeedback(new TranslatableComponent(
                    "gui.anvilssearch.sim.export.incomplete"));
            return;
        }
        ToolPreset preset = buildCurrentPreset();
        if (preset == null) {
            setFeedback(new TranslatableComponent(
                    "gui.anvilssearch.sim.export.incomplete"));
            return;
        }
        Path p = ToolPresetStore.save(preset);
        setFeedback(new TranslatableComponent(p != null
                ? "gui.anvilssearch.sim.save.ok"
                : "gui.anvilssearch.sim.save.fail"));
    }

    private void doExportFile() {
        if (!canExportOrSave()) {
            setFeedback(new TranslatableComponent(
                    "gui.anvilssearch.sim.export.incomplete"));
            return;
        }
        ToolPreset preset = buildCurrentPreset();
        if (preset == null) {
            setFeedback(new TranslatableComponent(
                    "gui.anvilssearch.sim.export.incomplete"));
            return;
        }

        String defaultName = preset.toolId.replace(':', '_') + ".json";
        SystemFileDialogs.save(defaultName, path -> {
            try {
                Files.writeString(path, ToolPresetCodec.encode(preset),
                        StandardCharsets.UTF_8);
                setFeedback(new TranslatableComponent("gui.anvilssearch.sim.export.ok"));
            } catch (Throwable t) {
                setFeedback(new TranslatableComponent("gui.anvilssearch.sim.export.fail"));
            }
        });
    }

    private void openPresetBrowser() {
        List<ToolPreset> list = ToolPresetStore.listAll();
        presetPopup = new ToolPresetBrowserPopup(
                list,
                p -> { applyPreset(p); presetPopup = null; },
                this::doImportClipboard,
                this::doImportFile);
        int pxAbs = panel.getPanelX() + panel.getAnimationOffset();
        int pw = panel.getPanelWidth();
        int popW = Math.min(240, pw - 20);
        int popH = Math.min(260, panel.getPanelHeight() - 100);
        presetPopup.setBounds(pxAbs + (pw - popW) / 2,
                panel.getPanelY() + 50, popW, popH);
    }

    private void doImportClipboard() {
        String json = null;
        try { json = Minecraft.getInstance().keyboardHandler.getClipboard(); }
        catch (Throwable ignored) {}

        ToolPreset p = ToolPresetCodec.decode(json);
        if (p == null) {
            setFeedback(new TranslatableComponent("gui.anvilssearch.sim.import.fail"));
            return;
        }
        applyPreset(p);
    }

    private void doImportFile() {
        SystemFileDialogs.open(path -> {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                ToolPreset p = ToolPresetCodec.decode(json);
                if (p == null) {
                    setFeedback(new TranslatableComponent(
                            "gui.anvilssearch.sim.import.fail"));
                    return;
                }
                applyPreset(p);
            } catch (Throwable t) {
                setFeedback(new TranslatableComponent("gui.anvilssearch.sim.import.fail"));
            }
        });
    }

    private void applyPreset(ToolPreset preset) {
        if (preset == null || preset.toolId == null) return;

        ResourceLocation want;
        try { want = new ResourceLocation(preset.toolId); }
        catch (Throwable t) { return; }

        ToolDefinitionIndex.Entry found = null;
        for (ToolDefinitionIndex.Entry e : ToolDefinitionIndex.get()) {
            if (e.id != null && e.id.equals(want)) { found = e; break; }
        }
        if (found == null) {
            setFeedback(new TranslatableComponent(
                    "gui.anvilssearch.sim.import.unknown_tool", preset.toolId));
            return;
        }

        model.selectTool(found.definition);
        slotWidgets.clear();
        cardDetails.clear();
        previewPanel.clearExpandState();
        midScrollOffset = 0;

        for (Map.Entry<Integer, MaterialId> e : preset.materials.entrySet()) {
            model.setSelection(e.getKey(), e.getValue());
        }

        rebuildSlotsIfNeeded();
        presetPopup = null;
        setFeedback(new TranslatableComponent("gui.anvilssearch.sim.import.ok"));
    }

    // ============================================================
    // ===== 部件槽渲染 ===========================================
    // ============================================================

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

    // ============================================================
    // ===== 交互 =================================================
    // ============================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (presetPopup != null) {
            if (presetPopup.isPointInside(mx, my)) {
                presetPopup.mouseClicked(mx, my, button);
                return true;
            } else {
                presetPopup = null;
            }
        }
        if (popup != null) {
            if (popup.isPointInside(mx, my)) {
                popup.mouseClicked(mx, my, button);
                return true;
            } else {
                popup = null;
            }
        }

        if (my >= toolbarTopY && my <= toolbarTopY + TOOLBAR_H) {
            if (inRect(mx, my, toolbarSaveX,   toolbarTopY, TOOLBAR_BTN_W, TOOLBAR_H)) {
                doSave(); return true;
            }
            if (inRect(mx, my, toolbarExportX, toolbarTopY, TOOLBAR_BTN_W, TOOLBAR_H)) {
                doExportFile(); return true;
            }
            if (inRect(mx, my, toolbarImportX, toolbarTopY, TOOLBAR_BTN_W, TOOLBAR_H)) {
                openPresetBrowser(); return true;
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
        if (presetPopup != null && presetPopup.isPointInside(mx, my)) {
            return presetPopup.mouseScrolled(mx, my, delta);
        }
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
        if (presetPopup != null && presetPopup.isDragging()) return presetPopup.mouseDragged(my);
        if (popup != null) return popup.mouseDragged(my);
        if (midScrollBar.isDragging()) return midScrollBar.updateDrag(my);
        if (previewPanel.isDragging()) return previewPanel.mouseDragged(my);
        return toolList.mouseDragged(my);
    }

    @Override
    public void mouseReleased() {
        if (presetPopup != null) presetPopup.mouseReleased();
        if (popup != null) popup.mouseReleased();
        midScrollBar.endDrag();
        previewPanel.mouseReleased();
        toolList.mouseReleased();
    }

    @Override
    public boolean isDraggingScrollBar() {
        if (presetPopup != null && presetPopup.isDragging()) return true;
        if (popup != null && popup.isDragging()) return true;
        if (midScrollBar.isDragging()) return true;
        if (previewPanel.isDragging()) return true;
        return toolList.isDragging();
    }
}