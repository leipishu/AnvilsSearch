package top.leipishu.anvilssearch.client;

import net.minecraft.client.Minecraft;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 用 JDK 自带的 JFileChooser 打开系统文件对话框。
 * 无任何外部依赖。
 *
 * 关键点：
 *   1. 主类已设置 java.awt.headless=false
 *   2. 使用系统 Look and Feel，在 Windows 上最接近原生观感
 *   3. 对话框在独立线程弹出，避免阻塞 MC 渲染线程
 *   4. 结果通过 Minecraft.execute() 切回主线程
 */
public final class SystemFileDialogs {

    private SystemFileDialogs() {}

    private static volatile boolean dialogOpen = false;

    // ============================================================
    // ===== 打开文件选择 =========================================
    // ============================================================

    public static void open(Consumer<Path> onResult) {
        if (dialogOpen) return;
        dialogOpen = true;

        new Thread(() -> {
            try {
                File selected = showDialog(false, null);
                if (selected != null) {
                    Path path = selected.toPath();
                    Minecraft.getInstance().execute(() -> onResult.accept(path));
                }
            } catch (Throwable t) {
                System.err.println("[Anvil's Search] FileChooser open failed: " + t);
                t.printStackTrace();
            } finally {
                dialogOpen = false;
            }
        }, "Anvil's Search - File Dialog").start();
    }

    // ============================================================
    // ===== 保存文件选择 =========================================
    // ============================================================

    public static void save(String defaultName, Consumer<Path> onResult) {
        if (dialogOpen) return;
        dialogOpen = true;

        new Thread(() -> {
            try {
                File selected = showDialog(true, defaultName);
                if (selected != null) {
                    Path path = selected.toPath();
                    if (!path.getFileName().toString().toLowerCase().endsWith(".json")) {
                        path = path.resolveSibling(
                                path.getFileName().toString() + ".json");
                    }
                    final Path finalPath = path;
                    Minecraft.getInstance().execute(() -> onResult.accept(finalPath));
                }
            } catch (Throwable t) {
                System.err.println("[Anvil's Search] FileChooser save failed: " + t);
                t.printStackTrace();
            } finally {
                dialogOpen = false;
            }
        }, "Anvil's Search - File Dialog").start();
    }

    // ============================================================
    // ===== 内部：显示对话框 =====================================
    // ============================================================

    private static File showDialog(boolean save, String defaultName) throws Exception {
        System.setProperty("java.awt.headless", "false");

        final File[] result = new File[1];

        // 在 EDT 上创建并显示对话框
        SwingUtilities.invokeAndWait(() -> {
            try {
                // ★ 用系统 LAF：Windows 上最接近原生观感
                try {
                    UIManager.setLookAndFeel(
                            UIManager.getSystemLookAndFeelClassName());
                } catch (Throwable ignored) {}

                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle(save
                        ? "Anvil's Search - Export"
                        : "Anvil's Search - Import");
                chooser.setAcceptAllFileFilterUsed(false);
                chooser.setFileFilter(new FileNameExtensionFilter(
                        "JSON (*.json)", "json"));

                if (save && defaultName != null && !defaultName.isEmpty()) {
                    chooser.setSelectedFile(new File(defaultName));
                }

                int r = save
                        ? chooser.showSaveDialog(null)
                        : chooser.showOpenDialog(null);

                if (r == JFileChooser.APPROVE_OPTION) {
                    result[0] = chooser.getSelectedFile();
                }
            } catch (Throwable t) {
                System.err.println("[Anvil's Search] JFileChooser error: " + t);
                t.printStackTrace();
            }
        });

        return result[0];
    }
}