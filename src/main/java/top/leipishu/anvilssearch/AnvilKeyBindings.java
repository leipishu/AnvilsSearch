package top.leipishu.anvilssearch;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = AnvilsSearch.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public class AnvilKeyBindings {

    public static final String KEY_CATEGORY  = "key.category.anvilssearch";
    public static final String KEY_TOGGLE    = "key.anvilssearch.toggle_panel";

    public static KeyMapping togglePanelKey;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            togglePanelKey = new KeyMapping(
                    KEY_TOGGLE,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_G,       // 避开本体的 F
                    KEY_CATEGORY
            );
            ClientRegistry.registerKeyBinding(togglePanelKey);
        });
    }
}