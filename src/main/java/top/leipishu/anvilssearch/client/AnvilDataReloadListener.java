package top.leipishu.anvilssearch.client;

import top.leipishu.anvilssearch.data.material.PartMaterialIndex;
import top.leipishu.anvilssearch.data.tool.ToolDefinitionIndex;
import top.leipishu.anvilssearch.data.material.MaterialFluidResolver;

public final class AnvilDataReloadListener {

    private AnvilDataReloadListener() {}

    private static boolean dataReady = false;

    public static void onPanelOpen() {
        // ★ 每次打开面板时强制重建，确保数据包已加载
        ToolDefinitionIndex.invalidate();
        PartMaterialIndex.invalidate();
        MaterialFluidResolver.invalidate();
        dataReady = true;
    }

    public static void invalidate() {
        PartMaterialIndex.invalidate();
        ToolDefinitionIndex.invalidate();
        MaterialFluidResolver.invalidate();
        dataReady = false;
    }

    public static boolean isDataReady() {
        return dataReady;
    }
}