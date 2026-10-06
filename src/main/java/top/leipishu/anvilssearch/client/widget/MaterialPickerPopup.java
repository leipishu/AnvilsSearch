package top.leipishu.anvilssearch.client.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.TranslatableComponent;
import top.leipishu.anvilssearch.client.animation.controller.AnvilWidgetAnimations;
import top.leipishu.anvilssearch.data.material.PartMaterialIndex;
import top.leipishu.tinkerssearch.client.animation.core.ColorUtil;
import top.leipishu.tinkerssearch.client.gui.components.CardBackground;
import top.leipishu.tinkerssearch.client.gui.components.ScrollBar;
import top.leipishu.tinkerssearch.client.gui.components.SearchBox;
import top.leipishu.tinkerssearch.client.gui.components.SearchBoxStyle;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;
import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch;
import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch.PinyinResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static top.leipishu.tinkerssearch.config.PanelConfig.SCROLL_BAR_PADDING;
import static top.leipishu.tinkerssearch.config.PanelConfig.SCROLL_BAR_WIDTH;

public class MaterialPickerPopup {

    public interface OnPick {
        void onPick(PartMaterialIndex.Entry entry);
    }

    private static final int ROW_H    = 14;
    private static final int PAD      = 4;
    private static final int SEARCH_H = 16;
    private static final int HEADER_H = SEARCH_H + 4;

    /** 动画 key 前缀。 */
    private static final String ANIM_KEY = "picker";

    private int x, y, w, h;

    private final List<PartMaterialIndex.Entry> allEntries;
    private List<PartMaterialIndex.Entry> entries;
    private final OnPick callback;

    private final SearchBox searchBox = new SearchBox(SearchBoxStyle.panel());

    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private final ScrollBar scrollBar = new ScrollBar();

    /** ★ 关闭状态：true = 正在淡出，动画完成后调用方置 null。 */
    private boolean closing = false;

    /** ★ 关闭动画完成回调。 */
    private Runnable onCloseFinished;

    public MaterialPickerPopup(List<PartMaterialIndex.Entry> entries, OnPick callback) {
        this.allEntries = new ArrayList<>(entries != null ? entries : new ArrayList<>());
        this.entries = new ArrayList<>(this.allEntries);
        this.callback = callback;

        this.scrollBar.setOnOffsetChanged(v -> scrollOffset = v);
        this.scrollBar.setThumbMinHeight(12);
        this.scrollBar.setHoverExpandX(2);
        this.scrollBar.setAnimationId("anvil.picker.scroll");

        this.searchBox.setHintText(new TranslatableComponent(
                "gui.anvilssearch.picker.search_hint"));
        this.searchBox.setOnTextChanged(this::applyFilter);
        this.searchBox.setAnimationId("anvil.picker.search");

        // ★ 每次新建实例时重置 fade/scale，保证每次打开都有淡入
        AnvilWidgetAnimations.resetPopupAnim(ANIM_KEY);
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return w; }
    public int getHeight() { return h; }

    public boolean isPointInside(double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    public boolean isSearchFocused() {
        return searchBox.isFocused();
    }

    // ============================================================
    // ===== 关闭动画 ==============================================
    // ============================================================

    /**
     * 请求关闭：启动淡出动画，动画完成后触发 {@code onFinished}。
     */
    public void startClose(Runnable onFinished) {
        if (closing) return;
        closing = true;
        this.onCloseFinished = onFinished;
    }

    /** 是否正在关闭。 */
    public boolean isClosing() {
        return closing;
    }

    /**
     * 每帧检查关闭动画是否完成；完成则触发回调。
     */
    public void checkCloseFinished() {
        if (!closing) return;
        float alpha = AnvilWidgetAnimations.popupFade(ANIM_KEY, false);
        if (alpha <= 0.01f) {
            Runnable cb = onCloseFinished;
            onCloseFinished = null;
            if (cb != null) cb.run();
        }
    }

    // ============================================================
    // ===== 过滤 =================================================
    // ============================================================

    private void applyFilter(String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty()) {
            entries = new ArrayList<>(allEntries);
        } else {
            entries = new ArrayList<>();
            for (PartMaterialIndex.Entry e : allEntries) {
                if (matches(e, k)) entries.add(e);
            }
        }
        scrollOffset = 0;
    }

    private static boolean matches(PartMaterialIndex.Entry e, String k) {
        String name = e.getDisplayName();
        if (name.toLowerCase(Locale.ROOT).contains(k)) return true;
        if (e.registryPath != null && e.registryPath.toLowerCase(Locale.ROOT).contains(k))
            return true;
        try {
            PinyinResult py = PinyinSearch.getPinyin(name);
            if (py.fullPinyin.contains(k) || py.initials.contains(k)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    // ============================================================
    // ===== 渲染 =================================================
    // ============================================================

    public void render(PoseStack ps, Font font, int mouseX, int mouseY) {
        boolean visible = !closing;
        float alpha = AnvilWidgetAnimations.popupFade(ANIM_KEY, visible);
        float scale = AnvilWidgetAnimations.popupScale(ANIM_KEY, visible);

        if (alpha <= 0.01f) return;

        // 以弹窗中心为原点缩放
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        ps.pushPose();
        ps.translate(cx, cy, 0);
        ps.scale(scale, scale, 1f);
        ps.translate(-cx, -cy, 0);

        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        try {
            GuiComponent.fill(ps, x, y, x + w, y + h, 0xF0202020);
            CardBackground.draw(ps, x, y, w, h, 0xF0202020, 0xFF888888);

            int sbX = x + PAD;
            int sbY = y + PAD;
            int sbW = w - PAD * 2;
            searchBox.setBounds(sbX, sbY, sbW, SEARCH_H);
            searchBox.render(ps, mouseX, mouseY, font);

            int areaX = x + PAD;
            int areaY = y + PAD + HEADER_H;
            int areaW = w - PAD * 2 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;
            int areaH = h - PAD * 2 - HEADER_H;
            if (areaH <= 0) return;

            int totalH = entries.size() * ROW_H;
            maxScrollOffset = Math.max(0, totalH - areaH);
            if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;

            boolean scissorOk = ScissorHelper.enableScissor(areaX, areaY, areaW, areaH);
            try {
                if (scissorOk) RenderSystem.disableDepthTest();

                if (entries.isEmpty()) {
                    font.draw(ps, "\u00A77" + new TranslatableComponent(
                                    "gui.anvilssearch.picker.empty").getString(),
                            areaX + 4, areaY + 4, 0x666666);
                } else {
                    int rowY = areaY - scrollOffset;
                    for (PartMaterialIndex.Entry e : entries) {
                        if (rowY + ROW_H >= areaY && rowY <= areaY + areaH) {
                            boolean hover = mouseX >= areaX && mouseX <= areaX + areaW
                                    && mouseY >= rowY && mouseY <= rowY + ROW_H;

                            String rowKey = e.id != null ? e.id.toString()
                                    : String.valueOf(e.hashCode());
                            float hoverT = AnvilWidgetAnimations.rowHover(
                                    "picker:" + rowKey, hover);

                            if (hoverT > 0.01f) {
                                GuiComponent.fill(ps, areaX, rowY, areaX + areaW,
                                        rowY + ROW_H,
                                        ColorUtil.withAlphaFactor(0x33FFFFFF, hoverT));
                            }

                            int textColor = ColorUtil.lerpARGB(0xFFCCCCCC, 0xFFFFFFFF, hoverT);
                            font.draw(ps, e.getDisplayName(), areaX + 4, rowY + 3, textColor);
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

            scrollBar.setBounds(x + w - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING,
                    areaY, SCROLL_BAR_WIDTH, areaH);
            scrollBar.setRange(scrollOffset, maxScrollOffset);
            scrollBar.render(ps, mouseX, mouseY);
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            ps.popPose();
        }
    }

    // ============================================================
    // ===== 交互 =================================================
    // ============================================================

    public boolean mouseClicked(double mx, double my, int button) {
        if (closing) return true;
        if (scrollBar.tryBeginDrag(mx, my)) return true;
        if (!isPointInside(mx, my)) return false;

        if (searchBox.mouseClicked(mx, my, button)) return true;

        int areaY = y + PAD + HEADER_H;
        int rowY = areaY - scrollOffset;
        for (PartMaterialIndex.Entry e : entries) {
            if (my >= rowY && my <= rowY + ROW_H) {
                if (callback != null) callback.onPick(e);
                return true;
            }
            rowY += ROW_H;
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return searchBox.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char c, int modifiers) {
        return searchBox.charTyped(c, modifiers);
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (closing) return false;
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

    public boolean mouseDragged(double my) {
        return scrollBar.updateDrag(my);
    }

    public void mouseReleased() {
        scrollBar.endDrag();
    }

    public boolean isDragging() {
        return scrollBar.isDragging();
    }
}