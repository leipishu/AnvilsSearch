package top.leipishu.anvilssearch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;

public interface AnvilTab {

    Component getLabel();

    default int getPreferredWidth() { return AnvilPanelAnimation.WIDTH_NORMAL; }

    default void onActivate()   {}
    default void onDeactivate() {}
    default void onSearchChanged(String keyword) {}
    default boolean wantsSearchBox() { return true; }

    void renderContent(PoseStack ps, Font font,
                       int mouseX, int mouseY, float partialTick,
                       int px, int py, int pw, int ph,
                       int contentTop, int contentBottom);

    default boolean mouseClicked(double mx, double my, int button) { return false; }
    default boolean mouseScrolled(double mx, double my, double delta) { return false; }
    default boolean mouseDragged(double mx, double my) { return false; }
    default void    mouseReleased() {}
    default boolean isDraggingScrollBar() { return false; }
    default boolean keyPressed(int keyCode, int scanCode, int mods) { return false; }
    default boolean charTyped(char c, int mods) { return false; }

    default List<Component> getPendingTooltip() { return null; }
    default boolean isAnySearchFocused() { return false; }
    default void onExternalSearchFocus() {}
}