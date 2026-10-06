package top.leipishu.anvilssearch;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import top.leipishu.anvilssearch.client.AnvilDataReloadListener;
import top.leipishu.anvilssearch.client.AnvilSidebarPanel;
import top.leipishu.anvilssearch.client.animation.controller.AnvilPanelAnimations;
import top.leipishu.tinkerssearch.client.animation.core.ColorUtil;

import java.util.List;

@Mod(AnvilsSearch.MODID)
public class AnvilsSearch {

    public static final String MODID = "anvilssearch";

    private static AnvilSidebarPanel sidebar;

    private static boolean renderingPanelTooltip = false;

    private boolean hasInitialized = false;
    private long lastToggleTime = 0;
    private static final long TOGGLE_COOLDOWN = 200;

    private static final int TAB_BUTTON_WIDTH  = 14;
    private static final int TAB_BUTTON_HEIGHT = 30;

    private int lastScreenW = -1;
    private int lastScreenH = -1;

    public AnvilsSearch() {
        // ★ 必须在任何 AWT/Swing 组件被创建之前设置，否则 FileDialog 会抛 HeadlessException
        System.setProperty("java.awt.headless", "false");

        System.out.println("[Anvil's Search] Initializing...");
        MinecraftForge.EVENT_BUS.register(this);
        sidebar = new AnvilSidebarPanel();
    }

    public static AnvilSidebarPanel getSidebar() { return sidebar; }

    // ============================================================
    // ===== Screen 判定 ==========================================
    // ============================================================

    private static boolean isAnvilScreen(Screen screen) {
        if (screen == null) return false;
        String cn = screen.getClass().getName();
        if (cn.endsWith("InfoPanelScreen")) return false;
        if (cn.endsWith("SideInventoryScreen")) return false;
        if (cn.startsWith("slimeknights.tconstruct.tables.client.inventory.")
                && cn.endsWith("Screen")) {
            return true;
        }
        String lower = cn.toLowerCase();
        return lower.contains("tinkerstation") || lower.contains("tinkersanvil");
    }

    private static boolean isAnvilScreenNow() {
        return isAnvilScreen(Minecraft.getInstance().screen);
    }

    // ============================================================
    // ===== Screen init ==========================================
    // ============================================================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen == null) return;

        if (isAnvilScreen(screen)) {
            handleAnvilOpen(screen);
        } else {
            handleAnvilLeave();
        }
    }

    private void handleAnvilOpen(Screen screen) {
        if (sidebar == null) return;
        sidebar.updatePanelPosition();

        Minecraft mc = Minecraft.getInstance();
        lastScreenW = mc.getWindow().getGuiScaledWidth();
        lastScreenH = mc.getWindow().getGuiScaledHeight();

        if (!hasInitialized) {
            sidebar.refreshData();
            hasInitialized = true;
        }
    }

    private void handleAnvilLeave() {
        hasInitialized = false;
    }

    // ============================================================
    // ===== 快捷键 ===============================================
    // ============================================================

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        top.leipishu.anvilssearch.data.FavoritesStore.flushIfDirty();

        if (!isAnvilScreenNow()) return;
        if (sidebar == null) return;
        if (AnvilKeyBindings.togglePanelKey == null) return;

        while (AnvilKeyBindings.togglePanelKey.consumeClick()) {
            long now = System.currentTimeMillis();
            if (now - lastToggleTime < TOGGLE_COOLDOWN) continue;
            lastToggleTime = now;
            sidebar.toggleVisibility();
        }
    }

    // ============================================================
    // ===== 键盘 =================================================
    // ============================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        if (sidebar == null || !sidebar.isVisible()) return;
        if (!sidebar.isSearchFocused()) return;

        int keyCode = event.getKeyCode();
        sidebar.handleKeyPressed(keyCode, event.getScanCode(), event.getModifiers());
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (sidebar == null || !sidebar.isVisible()) return;
        if (!sidebar.isSearchFocused()) return;

        sidebar.handleCharTyped(event.getCodePoint(), event.getModifiers());
        event.setCanceled(true);
    }

    // ============================================================
    // ===== 鼠标 =================================================
    // ============================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseClickPre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (sidebar == null || !isAnvilScreenNow()) return;
        double mx = event.getMouseX();
        double my = event.getMouseY();

        if (sidebar.isTabButtonClicked(mx, my)) {
            if (!sidebar.isAnimating()) sidebar.toggleVisibility();
            event.setCanceled(true);
            return;
        }

        boolean inside = sidebar.isPointInsidePanel(mx, my);

        if (sidebar.isSearchFocused() && !inside) {
            sidebar.setSearchFocused(false);
            event.setCanceled(true);
            return;
        }

        if ((sidebar.isVisible() || sidebar.isAnimating()) && inside) {
            if (!sidebar.isAnimating()) {
                sidebar.mouseClicked(mx, my, event.getButton());
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (sidebar == null || !sidebar.isVisible() || !isAnvilScreenNow()) return;
        if (sidebar.isPointInsidePanel(event.getMouseX(), event.getMouseY())) {
            sidebar.mouseScrolled(event.getMouseX(), event.getMouseY(),
                    event.getScrollDelta());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseDragPre(ScreenEvent.MouseDragged.Pre event) {
        if (sidebar == null || !isAnvilScreenNow()) return;
        if (!sidebar.isDraggingScrollBar()) return;
        sidebar.handleMouseDrag(event.getMouseX(), event.getMouseY());
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseReleasedPre(ScreenEvent.MouseButtonReleased.Pre event) {
        if (sidebar == null || !isAnvilScreenNow()) return;
        if (!sidebar.isDraggingScrollBar()) return;
        sidebar.handleMouseRelease();
        event.setCanceled(true);
    }

    // ============================================================
    // ===== Tooltip 屏蔽 =========================================
    // ============================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
        if (renderingPanelTooltip) return;
        if (sidebar == null || !isAnvilScreenNow()) return;
        if (!sidebar.isVisible() && !sidebar.isAnimating()) return;

        Minecraft mc = Minecraft.getInstance();
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()
                / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight()
                / mc.getWindow().getScreenHeight();

        if (sidebar.isPointInsidePanel(mx, my)) {
            event.setCanceled(true);
        }
    }

    // ============================================================
    // ===== 渲染 =================================================
    // ============================================================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onScreenDrawPost(ScreenEvent.Render.Post event) {
        if (sidebar == null) return;

        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null) return;

        syncPanelSizeToWindow(mc);

        if (!isAnvilScreen(screen)) return;

        if (!hasInitialized) {
            sidebar.refreshData();
            hasInitialized = true;
        }

        // ★ 1.19.2：ScreenEvent.Render 仍提供 getPoseStack()
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(0, 0, 500);

        try { GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT); } catch (Exception ignored) {}

        boolean depthWas = false, blendWas = false, textureWas = false, scissorWas = false;
        try {
            depthWas   = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            blendWas   = GL11.glIsEnabled(GL11.GL_BLEND);
            textureWas = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            scissorWas = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        } catch (Exception ignored) {}

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableTexture();
        try { GlStateManager._disableScissorTest(); } catch (Exception ignored) {}

        try {
            sidebar.tickAnimation();

            if (sidebar.isVisible() || sidebar.isAnimating()) {
                double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()
                        / mc.getWindow().getScreenWidth();
                double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight()
                        / mc.getWindow().getScreenHeight();
                sidebar.render(ps, (int) mx, (int) my, event.getPartialTick());
            }

            drawTabButton(ps);

            renderPanelTooltip();

        } finally {
            try {
                if (depthWas)   RenderSystem.enableDepthTest();  else RenderSystem.disableDepthTest();
                if (blendWas)   RenderSystem.enableBlend();      else RenderSystem.disableBlend();
                if (textureWas) RenderSystem.enableTexture();    else RenderSystem.disableTexture();
                if (scissorWas) GlStateManager._enableScissorTest();
                else            GlStateManager._disableScissorTest();
            } catch (Exception ignored) {}
            ps.popPose();
        }
    }

    private void syncPanelSizeToWindow(Minecraft mc) {
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        if (w != lastScreenW || h != lastScreenH) {
            lastScreenW = w;
            lastScreenH = h;
            sidebar.updatePanelPosition();
        }
    }

    private void renderPanelTooltip() {
        if (sidebar == null) return;
        List<Component> tooltip = sidebar.getPendingTooltip();
        if (tooltip == null || tooltip.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        int mx = (int) (mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()
                / mc.getWindow().getScreenWidth());
        int my = (int) (mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight()
                / mc.getWindow().getScreenHeight());

        Screen screen = mc.screen;
        if (screen == null) return;

        PoseStack tooltipPs = new PoseStack();

        renderingPanelTooltip = true;
        try {
            // ★ 1.19.2 里 Screen.renderComponentTooltip 仍接受 PoseStack
            screen.renderComponentTooltip(tooltipPs, tooltip, mx, my);
        } catch (Throwable t) {
            System.err.println("[Anvil's Search] tooltip render failed: " + t);
        } finally {
            renderingPanelTooltip = false;
        }
    }

    // ============================================================
    // ===== 开合按钮 =============================================
    // ============================================================

    private void drawTabButton(PoseStack ps) {
        if (sidebar == null) return;

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int off = sidebar.getAnimationOffset();
        int pw  = sidebar.getPanelWidth();

        int btnX = off + pw - 1;
        if (btnX < 0) btnX = 0;

        int btnY = (screenH - TAB_BUTTON_HEIGHT) / 2;

        if (btnX + TAB_BUTTON_WIDTH < 0 || btnX > screenW) return;

        double mx = mc.mouseHandler.xpos() * screenW / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * screenH / mc.getWindow().getScreenHeight();

        boolean hover = mx >= btnX && mx <= btnX + TAB_BUTTON_WIDTH
                && my >= btnY && my <= btnY + TAB_BUTTON_HEIGHT;

        float hoverT = AnvilPanelAnimations.tabBtnHover(hover);

        int baseBg  = 0xCC1A1A1A;
        int hoverBg = 0xCC444444;
        int bg = ColorUtil.lerpARGB(baseBg, hoverBg, hoverT);
        GuiComponent.fill(ps, btnX, btnY, btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, bg);

        int border = 0x44FFFFFF;
        GuiComponent.fill(ps, btnX, btnY, btnX + TAB_BUTTON_WIDTH, btnY + 1, border);
        GuiComponent.fill(ps, btnX, btnY + TAB_BUTTON_HEIGHT - 1,
                btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, border);

        boolean fullyExpanded = off == 0;
        if (fullyExpanded) {
            GuiComponent.fill(ps, btnX + TAB_BUTTON_WIDTH - 1, btnY,
                    btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, border);
        } else {
            GuiComponent.fill(ps, btnX, btnY, btnX + 1, btnY + TAB_BUTTON_HEIGHT, border);
            GuiComponent.fill(ps, btnX + TAB_BUTTON_WIDTH - 1, btnY,
                    btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, border);
        }

        Font font = mc.font;
        String arrow = sidebar.isExpanded() ? "\u25C0" : "\u25B6";
        int tx = btnX + (TAB_BUTTON_WIDTH - font.width(arrow)) / 2;
        int ty = btnY + (TAB_BUTTON_HEIGHT - font.lineHeight) / 2 + 1;
        int baseTextColor  = 0xCCCCCCCC;
        int hoverTextColor = 0xFFFFFFFF;
        int textColor = ColorUtil.lerpARGB(baseTextColor, hoverTextColor, hoverT);
        font.draw(ps, arrow, tx, ty, textColor);
    }

    // ============================================================
    // ===== 配方重载 =============================================
    // ============================================================

    @SubscribeEvent
    public void onRecipesUpdated(net.minecraftforge.client.event.RecipesUpdatedEvent event) {
        AnvilDataReloadListener.invalidate();
        top.leipishu.tinkerssearch.recipe.CastingRecipeHelper.invalidateCache();
    }
}