package top.leipishu.anvilssearch.client.tab;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import top.leipishu.anvilssearch.client.AnvilPanelAnimation;
import top.leipishu.anvilssearch.client.AnvilSidebarPanel;
import top.leipishu.anvilssearch.client.AnvilTab;
import top.leipishu.anvilssearch.data.AnvilSlotAccess;
import top.leipishu.anvilssearch.data.ModifierIndex;
import top.leipishu.tinkerssearch.client.gui.components.ScrollBar;
import top.leipishu.tinkerssearch.client.gui.components.SearchBox;
import top.leipishu.tinkerssearch.client.gui.components.SearchBoxStyle;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;
import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch;
import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch.PinyinResult;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

public class ModifierSearchTab implements AnvilTab {

    private static final int ROW_H     = 14;
    private static final int SEARCH_H  = 16;
    private static final int SLOT_SIZE = 16;
    private static final int SLOT_GAP  = 2;
    private static final int LINE_H    = 10;

    private final AnvilSidebarPanel panel;

    private List<ModifierIndex.Entry> allEntries = new ArrayList<>();
    private List<ModifierIndex.Entry> filtered = new ArrayList<>();

    private int scrollOffset = 0;
    private int maxScrollOffset = 0;
    private final ScrollBar scrollBar = new ScrollBar();
    private final SearchBox searchBox = new SearchBox(SearchBoxStyle.panel());

    private ModifierIndex.Entry selected = null;

    private int leftListX, leftListTop, leftListW, leftListH;
    private int rightAreaX, rightAreaY, rightAreaW, rightAreaH;
    private int rightScrollOffset = 0;
    private int rightMaxScroll = 0;

    public ModifierSearchTab(AnvilSidebarPanel panel) {
        this.panel = panel;
        this.scrollBar.setOnOffsetChanged(v -> scrollOffset = v);
        this.scrollBar.setThumbMinHeight(16);
        this.scrollBar.setHoverExpandX(3);

        this.searchBox.setHintText(new TranslatableComponent(
                "gui.anvilssearch.modifier.search_hint"));
        this.searchBox.setOnTextChanged(this::applyFilter);
    }

    @Override public Component getLabel() {
        return new TranslatableComponent("gui.anvilssearch.tab.modifier");
    }

    @Override public int getPreferredWidth() {
        return AnvilPanelAnimation.WIDTH_NORMAL;
    }

    @Override public boolean wantsSearchBox() { return false; }

    @Override public void onActivate() { reloadIndex(); }

    @Override public boolean isAnySearchFocused() { return searchBox.isFocused(); }

    @Override public void onExternalSearchFocus() { searchBox.setFocused(false); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (!searchBox.isFocused()) return false;
        if (searchBox.keyPressed(keyCode, scanCode, mods)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            searchBox.setFocused(false);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (!searchBox.isFocused()) return false;
        return searchBox.charTyped(c, mods);
    }

    private void reloadIndex() {
        allEntries = new ArrayList<>(ModifierIndex.get());
        System.out.println("[Anvil's Search] ModifierSearchTab reload: " + allEntries.size());
        applyFilter("");
    }

    private void applyFilter(String kw) {
        String k = kw == null ? "" : kw.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty()) {
            filtered = new ArrayList<>(allEntries);
        } else {
            filtered = new ArrayList<>();
            for (ModifierIndex.Entry e : allEntries) {
                if (matches(e, k)) filtered.add(e);
            }
        }
        scrollOffset = 0;
        rightScrollOffset = 0;
    }

    private static boolean matches(ModifierIndex.Entry e, String k) {
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

    @Override
    public void renderContent(PoseStack ps, Font font,
                              int mouseX, int mouseY, float partialTick,
                              int px, int py, int pw, int ph,
                              int contentTop, int contentBottom) {

        int contentLeft  = px + 5;
        int contentRight = px + pw - 5;
        int areaTop      = py + contentTop;
        int areaH        = contentBottom - contentTop;

        int totalW = contentRight - contentLeft;
        int leftW  = (int) (totalW * 0.58f);
        int rightW = totalW - leftW - 4;
        int leftX  = contentLeft;
        int rightX = leftX + leftW + 4;

        renderLeft(ps, font, leftX, areaTop, leftW, areaH, mouseX, mouseY);
        renderRight(ps, font, rightX, areaTop, rightW, areaH, mouseX, mouseY);
    }

    private void renderLeft(PoseStack ps, Font font,
                            int x, int y, int w, int h,
                            int mouseX, int mouseY) {
        GuiComponent.fill(ps, x, y, x + w, y + h, 0xFF181818);
        GuiComponent.fill(ps, x, y, x + w, y + 1, 0xFF333333);

        searchBox.setBounds(x + 3, y + 3, w - 6, SEARCH_H);
        searchBox.render(ps, mouseX, mouseY, font);

        int listTop = y + 3 + SEARCH_H + 3;
        int listH = h - (listTop - y) - 2;
        int listW = w - 4;

        leftListX = x + 2;
        leftListTop = listTop;
        leftListW = listW;
        leftListH = listH;

        if (filtered.isEmpty()) {
            font.draw(ps, "\u00A77" + new TranslatableComponent(
                            "gui.anvilssearch.modifier.empty").getString(),
                    x + 4, listTop + 4, 0x666666);
            return;
        }

        int totalH = filtered.size() * ROW_H;
        maxScrollOffset = Math.max(0, totalH - listH);
        if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;

        int scrollBarW = maxScrollOffset > 0 ? SCROLL_BAR_WIDTH + SCROLL_BAR_PADDING : 0;
        int clipW = listW - scrollBarW;

        boolean scissorOk = ScissorHelper.enableScissor(x + 2, listTop, clipW, listH);
        try {
            if (scissorOk) RenderSystem.disableDepthTest();

            int rowY = listTop - scrollOffset;
            for (ModifierIndex.Entry e : filtered) {
                if (rowY + ROW_H >= listTop && rowY <= listTop + listH) {
                    boolean hover = mouseX >= x + 2 && mouseX <= x + 2 + clipW
                            && mouseY >= rowY && mouseY <= rowY + ROW_H;
                    boolean sel = e == selected;

                    if (sel || hover) {
                        GuiComponent.fill(ps, x + 2, rowY, x + 2 + clipW, rowY + ROW_H,
                                sel ? 0x66FFAA00 : 0x33FFFFFF);
                    }

                    Component name;
                    try {
                        name = e.getDisplayNameComponent(1);
                    } catch (Throwable t) {
                        name = new TextComponent("\u00A77" + e.getDisplayName());
                    }

                    font.draw(ps, name, x + 5, rowY + 3,
                            sel ? 0xFFFFDD77 : (hover ? 0xFFFFFF : 0xCCCCCC));
                }
                rowY += ROW_H;
            }
        } finally {
            if (scissorOk) {
                ScissorHelper.disableScissor();
                RenderSystem.enableDepthTest();
            }
        }

        if (maxScrollOffset > 0) {
            scrollBar.setBounds(x + w - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING,
                    listTop, SCROLL_BAR_WIDTH, listH);
            scrollBar.setRange(scrollOffset, maxScrollOffset);
            scrollBar.render(ps, mouseX, mouseY);
        }
    }

    // ============================================================
    // ===== 右：5 格配方 + 槽位 + 描述 ==========================
    // ============================================================

    private void renderRight(PoseStack ps, Font font,
                             int x, int y, int w, int h,
                             int mouseX, int mouseY) {
        GuiComponent.fill(ps, x, y, x + w, y + h, 0xFF181818);
        GuiComponent.fill(ps, x, y, x + w, y + 1, 0xFF333333);

        int size = SLOT_SIZE;
        int gap = SLOT_GAP;
        int midX = x + w / 2;

        int row1Y = y + 6;
        int row2Y = row1Y + size + gap + 2;
        int row3Y = row2Y + size + gap + 2;

        // 选中强化时，取第一级配方的材料填到 5 个格子
        ItemStack[] matIcons = new ItemStack[5];
        for (int i = 0; i < 5; i++) matIcons[i] = ItemStack.EMPTY;

        if (selected != null && !selected.levels.isEmpty()) {
            ModifierIndex.LevelInfo li = selected.levels.get(0);
            for (int i = 0; i < Math.min(5, li.materials.size()); i++) {
                matIcons[i] = li.materials.get(i);
            }
        }

        // 顶部居中（用 matIcons[1]）
        drawSlot(ps, font, midX - size / 2, row1Y, matIcons[1], mouseX, mouseY);

        // 第二行左侧（用 matIcons[0]）
        drawSlot(ps, font, midX - size / 2 - size - gap, row2Y, matIcons[0], mouseX, mouseY);

        ItemStack centerItem = AnvilSlotAccess.getCenterItem();
        drawSlot(ps, font, midX - size / 2, row2Y, centerItem, mouseX, mouseY);

        drawSlot(ps, font, midX + size / 2 + gap, row2Y, matIcons[2], mouseX, mouseY);

        // T4 / T5 底行居中
        int bottomTotalW = size * 2 + gap;
        int bottomStartX = midX - bottomTotalW / 2;
        drawSlot(ps, font, bottomStartX, row3Y, matIcons[3], mouseX, mouseY);
        drawSlot(ps, font, bottomStartX + size + gap, row3Y, matIcons[4], mouseX, mouseY);

        int infoTop = row3Y + size + 6;
        int infoH = y + h - infoTop - 2;

        rightAreaX = x + 2;
        rightAreaY = infoTop;
        rightAreaW = w - 4;
        rightAreaH = infoH;

        if (selected == null) {
            font.draw(ps, "\u00A77" + new TranslatableComponent(
                            "gui.anvilssearch.modifier.pick_hint").getString(),
                    x + 4, infoTop, 0x666666);
            return;
        }

        List<Component> lines = new ArrayList<>();

        // 适用性
        if (centerItem.isEmpty()) {
            lines.add(new TextComponent("\u00A78" + new TranslatableComponent(
                    "gui.anvilssearch.modifier.no_item").getString()));
        } else {
            boolean canApply = canApplyToCurrentItem(selected);
            lines.add(new TextComponent((canApply ? "\u00A7a" : "\u00A7c")
                    + new TranslatableComponent(canApply
                    ? "gui.anvilssearch.modifier.applicable"
                    : "gui.anvilssearch.modifier.not_applicable").getString()));
        }
        lines.add(new TextComponent(""));

        int maxTextW = w - 8;
        for (ModifierIndex.LevelInfo li : selected.levels) {
            // ★ 用强化名称本身体现等级（如“锋利+”、“锋利++”）
            lines.add(selected.getDisplayNameComponent(li.level));

            // 槽位标签
            lines.add(new TextComponent("\u00A77" + new TranslatableComponent(
                    "gui.anvilssearch.modifier.slots_label").getString()));

            // 槽位内容
            if (li.slotLines.isEmpty()) {
                lines.add(new TextComponent("\u00A78" + new TranslatableComponent(
                        "gui.anvilssearch.modifier.slots_none").getString()));
            } else {
                for (Component s : li.slotLines) lines.add(s);
            }

            // 材料名
            if (!li.materialLines.isEmpty()) {
                for (Component s : li.materialLines) {
                    lines.addAll(wrapComponent(font, s, maxTextW));
                }
            } else {
                lines.add(new TextComponent("\u00A78" + new TranslatableComponent(
                        "gui.anvilssearch.modifier.no_recipe").getString()));
            }
            lines.add(new TextComponent(""));
        }

        // 描述
        lines.add(new TextComponent("\u00A7b" + new TranslatableComponent(
                "gui.anvilssearch.modifier.description").getString()));
        List<Component> desc = selected.getDescriptionList(1);
        if (desc.isEmpty()) {
            lines.add(new TextComponent("\u00A78" + new TranslatableComponent(
                    "gui.anvilssearch.modifier.no_description").getString()));
        } else {
            for (Component d : desc) {
                lines.addAll(wrapComponent(font, d, maxTextW));
            }
        }

        int totalH = lines.size() * LINE_H;
        rightMaxScroll = Math.max(0, totalH - infoH);
        if (rightScrollOffset > rightMaxScroll) rightScrollOffset = rightMaxScroll;

        boolean sOk = ScissorHelper.enableScissor(x + 2, infoTop, w - 4, infoH);
        try {
            if (sOk) RenderSystem.disableDepthTest();
            int cy = infoTop - rightScrollOffset;
            for (Component line : lines) {
                if (cy + LINE_H >= infoTop && cy <= infoTop + infoH) {
                    font.draw(ps, line, x + 4, cy, 0xCCCCCC);
                }
                cy += LINE_H;
            }
        } finally {
            if (sOk) {
                ScissorHelper.disableScissor();
                RenderSystem.enableDepthTest();
            }
        }
    }

    private static List<Component> wrapComponent(Font font, Component src, int maxW) {
        List<Component> out = new ArrayList<>();
        if (src == null) return out;
        String text = src.getString();
        if (text == null || text.isEmpty()) {
            out.add(new TextComponent(""));
            return out;
        }
        if (font.width(text) <= maxW) {
            out.add(new TextComponent(text));
            return out;
        }
        int start = 0;
        int len = text.length();
        while (start < len) {
            int end = start + 1;
            while (end <= len && font.width(text.substring(start, end)) <= maxW) end++;
            end--;
            if (end <= start) end = start + 1;
            out.add(new TextComponent(text.substring(start, Math.min(end, len))));
            start = Math.min(end, len);
        }
        return out;
    }

    private void drawSlot(PoseStack ps, Font font, int x, int y,
                          ItemStack stack, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + SLOT_SIZE
                && mouseY >= y && mouseY <= y + SLOT_SIZE;
        int bg = hover ? 0xFF3A3A3A : 0xFF222222;
        int border = hover ? 0xFFAA8844 : 0xFF555555;
        GuiComponent.fill(ps, x, y, x + SLOT_SIZE, y + SLOT_SIZE, bg);
        GuiComponent.fill(ps, x, y, x + SLOT_SIZE, y + 1, border);
        GuiComponent.fill(ps, x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, border);
        GuiComponent.fill(ps, x, y, x + 1, y + SLOT_SIZE, border);
        GuiComponent.fill(ps, x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, border);
        if (stack != null && !stack.isEmpty()) {
            try {
                Minecraft.getInstance().getItemRenderer().renderGuiItem(stack, x, y);
            } catch (Throwable ignored) {}
        }
    }

    private boolean canApplyToCurrentItem(ModifierIndex.Entry entry) {
        if (entry == null) return false;
        Object toolStack = AnvilSlotAccess.getCenterToolStack();
        if (toolStack == null) return false;
        Object mod = entry.modifier;
        if (mod == null) return false;
        for (Method m : mod.getClass().getMethods()) {
            if (!m.getName().equals("canApply")) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length != 2) continue;
            if (!params[0].isInstance(toolStack)) continue;
            try {
                Object r = m.invoke(mod, toolStack, 1);
                if (r instanceof Boolean b) return b;
            } catch (Throwable ignored) {}
        }
        return true;
    }

    // ============================================================
    // ===== 交互 =================================================
    // ============================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (scrollBar.tryBeginDrag(mx, my)) return true;
        if (searchBox.mouseClicked(mx, my, button)) {
            searchBox.setFocused(true);
            return true;
        }
        if (searchBox.isFocused()) searchBox.setFocused(false);

        if (mx >= leftListX && mx <= leftListX + leftListW
                && my >= leftListTop && my <= leftListTop + leftListH) {
            int relY = (int) (my - leftListTop + scrollOffset);
            int idx = relY / ROW_H;
            if (idx >= 0 && idx < filtered.size()) {
                selected = filtered.get(idx);
                rightScrollOffset = 0;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= rightAreaX && mx <= rightAreaX + rightAreaW
                && my >= rightAreaY && my <= rightAreaY + rightAreaH) {
            if (rightMaxScroll <= 0) return false;
            int no = rightScrollOffset - (int) (delta * 12);
            no = Math.max(0, Math.min(no, rightMaxScroll));
            if (no != rightScrollOffset) {
                rightScrollOffset = no;
                return true;
            }
            return true;
        }
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

    @Override public boolean mouseDragged(double mx, double my) { return scrollBar.updateDrag(my); }
    @Override public void mouseReleased() { scrollBar.endDrag(); }
    @Override public boolean isDraggingScrollBar() { return scrollBar.isDragging(); }
}