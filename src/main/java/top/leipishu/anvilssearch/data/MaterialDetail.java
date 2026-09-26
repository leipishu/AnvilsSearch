package top.leipishu.anvilssearch.data;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class MaterialDetail {

    public static final int LINE_H    = 11;
    public static final int TAG_H     = 14;
    public static final int TAG_GAP   = 3;
    public static final int HEADER_H  = 32;
    public static final int PAD       = 4;

    public static final class TraitTag {
        public final Component name;
        public final int color;
        public final List<Component> description;

        public TraitTag(Component name, int color, List<Component> description) {
            this.name = name;
            this.color = color;
            this.description = description != null ? description : new ArrayList<>();
        }
    }

    public final ItemStack icon;
    public final Component materialName;
    public final Component partName;
    public final List<Component> statLines;
    public final List<TraitTag> traits;
    public final int cost;

    public MaterialDetail(ItemStack icon, Component materialName, Component partName,
                          List<Component> statLines, List<TraitTag> traits, int cost) {
        this.icon = icon;
        this.materialName = materialName != null ? materialName : new TranslatableComponent("?");
        this.partName = partName != null ? partName : new TranslatableComponent("?");
        this.statLines = statLines != null ? statLines : new ArrayList<>();
        this.traits = traits != null ? traits : new ArrayList<>();
        this.cost = cost;
    }

    public boolean hasTraits() { return !traits.isEmpty(); }

    // ============================================================
    // ===== 翻译兜底工具 =========================================
    // ============================================================

    public static Component resolveTranslation(Component c) {
        if (c == null) return new TextComponent("");
        try {
            if (c instanceof TranslatableComponent tc) {
                String s = c.getString();
                if (s != null && s.equals(tc.getKey())) {
                    return new TextComponent(prettifyKey(tc.getKey()));
                }
            }
        } catch (Throwable ignored) {}
        return c;
    }

    public static String resolveTranslationText(Component c) {
        if (c == null) return "";
        try {
            if (c instanceof TranslatableComponent tc) {
                String s = c.getString();
                if (s != null && s.equals(tc.getKey())) {
                    return prettifyKey(tc.getKey());
                }
            }
            return c.getString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String prettifyKey(String key) {
        if (key == null || key.isEmpty()) return "";

        String[] parts = key.split("\\.");
        String suffix = null;

        if (parts.length >= 2) {
            String last = parts[parts.length - 1];
            if ("flavor".equals(last) || "description".equals(last)) {
                suffix = Character.toUpperCase(last.charAt(0)) + last.substring(1);
                String[] n = new String[parts.length - 1];
                System.arraycopy(parts, 0, n, 0, parts.length - 1);
                parts = n;
            }
        }

        String core = parts[parts.length - 1];
        StringBuilder sb = new StringBuilder();
        for (String w : core.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1));
        }
        if (suffix != null) sb.append(" (").append(suffix).append(")");
        return sb.toString();
    }

    // ============================================================
    // ===== 高度 =================================================
    // ============================================================

    public int measureHeight(Font font, int w, boolean expanded) {
        if (!expanded) return HEADER_H;

        int maxW = w - PAD * 2 - 2;
        int h = HEADER_H;
        h += statLines.size() * LINE_H;
        if (hasTraits()) {
            h += LINE_H;
            h += measureTraitRows(font, maxW) * (TAG_H + TAG_GAP);
        }
        h += PAD;
        return h;
    }

    private int measureTraitRows(Font font, int maxW) {
        int rows = 1;
        int curW = 0;
        for (TraitTag t : traits) {
            int w = font.width(t.name) + 8;
            if (curW > 0 && curW + w > maxW) {
                rows++;
                curW = 0;
            }
            curW += w + TAG_GAP;
        }
        return rows;
    }

    // ============================================================
    // ===== 渲染 =================================================
    // ============================================================

    public List<Component> render(PoseStack ps, Font font, int x, int y, int w,
                                  int mouseX, int mouseY, boolean expanded,
                                  boolean showArrow) {
        int h = measureHeight(font, w, expanded);
        List<Component> hovered = null;

        GuiComponent.fill(ps, x, y, x + w, y + h, 0xFF141414);
        GuiComponent.fill(ps, x, y, x + 2, y + h, 0xFF555555);

        // 头部
        int iconY = y + (HEADER_H - 16) / 2;
        if (icon != null && !icon.isEmpty()) {
            try {
                Minecraft.getInstance().getItemRenderer().renderGuiItem(icon, x + PAD, iconY);
            } catch (Throwable ignored) {}
        }

        int textX = x + PAD + 20;
        int maxTextW = w - 20 - PAD - PAD;

        String partStr = resolveTranslationText(partName);
        String partDisplay = font.width(partStr) > maxTextW
                ? font.plainSubstrByWidth(partStr, maxTextW - 6) + "..."
                : partStr;
        font.draw(ps, partDisplay, textX, y + 5, 0xFFFFFF);

        String matStr = resolveTranslationText(materialName);
        String matDisplay = font.width(matStr) > maxTextW
                ? font.plainSubstrByWidth(matStr, maxTextW - 6) + "..."
                : matStr;
        font.draw(ps, "\u00A7e" + matDisplay, textX, y + 5 + LINE_H + 1, 0xFFDD77);

        // ★ 箭头由调用方决定是否画
        if (showArrow) {
            String arrow = expanded ? "\u25BC" : "\u25B6";
            font.draw(ps, "\u00A78" + arrow, x + w - 10 - PAD,
                    y + (HEADER_H - font.lineHeight) / 2 + 1, 0x888888);
        }

        if (!expanded) return null;

        // 展开内容
        int cy = y + HEADER_H;
        int statX = x + PAD + 2;
        int maxW = w - PAD * 2 - 2;

        for (Component line : statLines) {
            Component r = resolveTranslation(line);
            font.draw(ps, r, statX, cy, 0xCCCCCC);
            cy += LINE_H;
        }

        if (hasTraits()) {
            String label = new TranslatableComponent("gui.anvilssearch.detail.traits").getString();
            font.draw(ps, "\u00A7b" + label, statX, cy, 0x55FFFF);
            cy += LINE_H;

            int tagX = statX;
            for (TraitTag t : traits) {
                int tw = font.width(t.name) + 8;
                if (tagX > statX && tagX + tw > statX + maxW) {
                    tagX = statX;
                    cy += TAG_H + TAG_GAP;
                }

                int bg = 0xFF000000 | (t.color & 0x303030);
                int border = 0xFF000000 | t.color;

                GuiComponent.fill(ps, tagX, cy, tagX + tw, cy + TAG_H, bg);
                GuiComponent.fill(ps, tagX, cy, tagX + tw, cy + 1, border);
                GuiComponent.fill(ps, tagX, cy + TAG_H - 1, tagX + tw, cy + TAG_H, border);
                GuiComponent.fill(ps, tagX, cy, tagX + 1, cy + TAG_H, border);
                GuiComponent.fill(ps, tagX + tw - 1, cy, tagX + tw, cy + TAG_H, border);

                font.draw(ps, t.name, tagX + 4, cy + 3, t.color);

                if (mouseX >= tagX && mouseX <= tagX + tw
                        && mouseY >= cy && mouseY <= cy + TAG_H) {
                    List<Component> tip = new ArrayList<>();
                    tip.add(resolveTranslation(t.name));
                    if (t.description.isEmpty()) {
                        tip.add(new TranslatableComponent("gui.anvilssearch.detail.no_desc"));
                    } else {
                        for (Component d : t.description) {
                            tip.add(resolveTranslation(d));
                        }
                    }
                    hovered = tip;
                }

                tagX += tw + TAG_GAP;
            }
        }

        return hovered;
    }
}