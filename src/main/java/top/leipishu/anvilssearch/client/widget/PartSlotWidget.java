package top.leipishu.anvilssearch.client.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import top.leipishu.tinkerssearch.client.gui.components.CardBackground;
import net.minecraft.network.chat.TranslatableComponent;

public class PartSlotWidget {

    public static final int WIDTH  = 100;
    public static final int HEIGHT = 24;

    private int x, y;
    private String label = "?";
    private String materialName = "";

    public void setBounds(int x, int y) { this.x = x; this.y = y; }
    public int getX() { return x; }
    public int getY() { return y; }

    public void setLabel(String s)        { this.label = s == null ? "?" : s; }
    public void setMaterialName(String s) { this.materialName = s == null ? "" : s; }
    public String getLabel()        { return label; }
    public String getMaterialName() { return materialName; }

    public boolean isHovered(double mx, double my) {
        return mx >= x && mx <= x + WIDTH && my >= y && my <= y + HEIGHT;
    }

    public void render(PoseStack ps, Font font, int mouseX, int mouseY) {
        boolean hover = isHovered(mouseX, mouseY);

        int bg = hover ? 0xFF3A3A3A : 0xFF222222;
        int border = materialName.isEmpty()
                ? (hover ? 0xFFAA8844 : 0xFF444444)
                : (hover ? 0xFF88CC88 : 0xFF558855);

        CardBackground.draw(ps, x, y, WIDTH, HEIGHT, bg, border);

        font.draw(ps, "\u00A77" + label, x + 4, y + 3, 0xCCCCCC);

        String second = materialName.isEmpty()
                ? "\u00A78[" + new TranslatableComponent(
                "gui.anvilssearch.sim.click_to_choose").getString() + "]"
                : "\u00A7f" + materialName;
        font.draw(ps, second, x + 4, y + 13, 0xFFFFFF);
    }
}