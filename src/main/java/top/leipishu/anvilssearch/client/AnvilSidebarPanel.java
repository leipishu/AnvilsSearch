package top.leipishu.anvilssearch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.leipishu.anvilssearch.client.tab.PartMaterialTab;
import top.leipishu.anvilssearch.client.tab.ToolSimulatorTab;
import top.leipishu.tinkerssearch.client.gui.components.SearchBox;
import top.leipishu.tinkerssearch.client.gui.components.SearchBoxStyle;

import java.util.ArrayList;
import java.util.List;

public class AnvilSidebarPanel {

    private int panelX, panelY, panelHeight;
    private boolean visible = false;

    private final List<AnvilTab> tabs = new ArrayList<>();
    private int activeIndex = 0;

    private final SearchBox searchBox;
    private final AnvilPanelAnimation animation = new AnvilPanelAnimation();
    private final AnvilPanelRenderer renderer = new AnvilPanelRenderer(this);

    private String  searchKeyword = "";
    private boolean searchFocused = false;

    /** 每帧由 renderer 清空，Tab 渲染时通过 setPendingTooltip 写入。 */
    private List<Component> pendingTooltip = null;

    public AnvilSidebarPanel() {
        this.searchBox = new SearchBox(SearchBoxStyle.panel());
        this.searchBox.setOnTextChanged(this::onSearchTextChanged);
        this.searchBox.setAnimationId("anvil.mainsearch");

        tabs.add(new PartMaterialTab(this));
        tabs.add(new ToolSimulatorTab(this));
        tabs.add(new top.leipishu.anvilssearch.client.tab.ModifierSearchTab(this));
        tabs.get(0).onActivate();
    }

    // ==================== Tooltip ====================

    public void clearPendingTooltip() { pendingTooltip = null; }

    public void setPendingTooltip(List<Component> tip) {
        this.pendingTooltip = tip;
    }

    public List<Component> getPendingTooltip() { return pendingTooltip; }

    // ==================== Tab ====================

    public AnvilTab getActiveTab() { return tabs.get(activeIndex); }
    public List<AnvilTab> getTabs() { return tabs; }
    public int getActiveIndex() { return activeIndex; }

    public void switchTab(int idx) {
        if (idx < 0 || idx >= tabs.size() || idx == activeIndex) return;

        try {
            tabs.get(activeIndex).onDeactivate();
        } catch (Throwable e) {
            System.err.println("[Anvil's Search] onDeactivate failed: " + e);
        }

        activeIndex = idx;
        AnvilTab t = tabs.get(activeIndex);

        try {
            t.onActivate();
        } catch (Throwable e) {
            System.err.println("[Anvil's Search] onActivate failed: " + e);
        }

        try {
            animation.setTargetWidth(t.getPreferredWidth());
        } catch (Throwable e) {
            System.err.println("[Anvil's Search] setTargetWidth failed: " + e);
        }

        searchBox.clear();
        searchKeyword = "";
        searchFocused = false;

        try {
            t.onSearchChanged("");
        } catch (Throwable ignored) {}
    }

    // ==================== 可见性 ====================

    public boolean isVisible()   { return visible; }
    public boolean isAnimating() { return animation.isAnimating(); }
    public boolean isExpanded()  { return animation.isExpanded(); }

    public void setVisible(boolean v) {
        if (visible == v) return;
        visible = v;
        if (v) {
            updatePanelPosition();
            refreshData();
            animation.startShow();
        } else {
            animation.startHide();
            searchFocused = false;
        }
    }

    public void toggleVisibility() { setVisible(!visible); }

    // ==================== 布局 ====================

    public void updatePanelPosition() {
        Minecraft mc = Minecraft.getInstance();
        panelX = 0;
        panelY = 0;
        panelHeight = mc.getWindow().getGuiScaledHeight();
    }

    public int getPanelX() { return panelX; }
    public int getPanelY() { return panelY; }
    public int getPanelHeight() { return panelHeight; }
    public int getPanelWidth()  { return animation.getCurrentWidth(); }
    public int getAnimationOffset() { return animation.getAnimationOffset(); }

    public void tickAnimation() { animation.update(); }

    public AnvilPanelAnimation getAnimation() { return animation; }
    public AnvilPanelRenderer  getRenderer()  { return renderer; }
    public SearchBox           getSearchBox() { return searchBox; }

    public void refreshData() { AnvilDataReloadListener.onPanelOpen(); }

    // ==================== 搜索 ====================

    public String  getSearchKeyword() { return searchKeyword; }
    public boolean isSearchFocused()  {
        if (searchFocused) return true;
        return getActiveTab().isAnySearchFocused();
    }
    public void setSearchFocused(boolean f) {
        searchFocused = f;
        if (f) {
            getActiveTab().onExternalSearchFocus();
        }
    }
    private void onSearchTextChanged(String t) {
        searchKeyword = t == null ? "" : t;
        getActiveTab().onSearchChanged(searchKeyword);
    }

    // ==================== 命中测试 ====================

    public boolean isPointInsidePanel(double mx, double my) {
        int px = panelX + animation.getAnimationOffset();
        int pw = getPanelWidth();
        return mx >= px && mx <= px + pw
                && my >= panelY && my <= panelY + panelHeight;
    }

    public boolean isTabButtonClicked(double mx, double my) {
        int off = animation.getAnimationOffset();
        int pw  = getPanelWidth();
        int btnX = isExpanded() ? off + pw - 1 : 0;
        int btnY = (panelHeight - AnvilPanelRenderer.TAB_BUTTON_HEIGHT) / 2;

        return mx >= btnX && mx <= btnX + AnvilPanelRenderer.TAB_BUTTON_WIDTH
                && my >= btnY && my <= btnY + AnvilPanelRenderer.TAB_BUTTON_HEIGHT;
    }

    // ==================== 渲染 ====================

    public void render(PoseStack ps, int mouseX, int mouseY, float pt) {
        renderer.render(this, ps, mouseX, mouseY, pt);
    }

    // ==================== 交互 ====================

    public void mouseClicked(double mx, double my, int button) {
        if (renderer.handleMouseClicked(this, mx, my, button)) return;

        int px = panelX + animation.getAnimationOffset();
        getActiveTab().mouseClicked(mx - px, my - panelY, button);
    }

    public void mouseScrolled(double mx, double my, double delta) {
        int px = panelX + animation.getAnimationOffset();
        getActiveTab().mouseScrolled(mx - px, my - panelY, delta);
    }

    public void handleMouseDrag(double mx, double my) {
        int px = panelX + animation.getAnimationOffset();
        getActiveTab().mouseDragged(mx - px, my - panelY);
    }

    public void handleMouseRelease() { getActiveTab().mouseReleased(); }

    public boolean isDraggingScrollBar() { return getActiveTab().isDraggingScrollBar(); }

    public boolean handleKeyPressed(int key, int scan, int mods) {
        if (searchFocused) {
            if (searchBox.keyPressed(key, scan, mods)) return true;
            if (key == GLFW.GLFW_KEY_ESCAPE
                    || key == GLFW.GLFW_KEY_ENTER
                    || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
            return true;
        }
        return getActiveTab().keyPressed(key, scan, mods);
    }

    public boolean handleCharTyped(char c, int mods) {
        if (searchFocused) return searchBox.charTyped(c, mods);
        return getActiveTab().charTyped(c, mods);
    }
}