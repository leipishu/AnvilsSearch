package top.leipishu.anvilssearch;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = AnvilsSearch.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public class AnvilKeyBindings {

    public static final String KEY_CATEGORY  = "key.category.anvilssearch";
    public static final String KEY_TOGGLE    = "key.anvilssearch.toggle_panel";

    public static KeyMapping togglePanelKey;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        togglePanelKey = new KeyMapping(
                KEY_TOGGLE,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                KEY_CATEGORY
        );
        event.register(togglePanelKey);
    }
}