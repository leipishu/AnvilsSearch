package top.leipishu.anvilssearch.client;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.W32APIOptions;

import net.minecraft.client.Minecraft;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 系统原生文件对话框工具类。
 * Windows 上通过 JNA 调用 IFileOpenDialog / IFileSaveDialog COM 接口，
 * 呈现系统最新资源管理器风格窗口；失败时回退到 AWT FileDialog。
 */
public final class SystemFileDialogs {

    private SystemFileDialogs() {}

    private static volatile boolean dialogOpen = false;
    private static volatile String lastDirectory = null;

    // ============================================================
    // ===== 对外 API =============================================
    // ============================================================

    public static void open(Consumer<Path> onResult) {
        if (dialogOpen) return;
        dialogOpen = true;

        new Thread(() -> {
            try {
                File selected = showDialog(false, null);
                if (selected != null) {
                    if (!selected.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
                        System.err.println("[Anvil's Search] Ignored non-json file: " + selected.getName());
                        return;
                    }
                    rememberDirectory(selected);
                    Path path = selected.toPath();
                    Minecraft.getInstance().execute(() -> onResult.accept(path));
                }
            } catch (Throwable t) {
                System.err.println("[Anvil's Search] FileDialog open failed: " + t);
                t.printStackTrace();
            } finally {
                dialogOpen = false;
            }
        }, "Anvil's Search - File Dialog").start();
    }

    public static void save(String defaultName, Consumer<Path> onResult) {
        if (dialogOpen) return;
        dialogOpen = true;

        new Thread(() -> {
            try {
                File selected = showDialog(true, defaultName);
                if (selected != null) {
                    rememberDirectory(selected);
                    Path path = selected.toPath();
                    if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
                        path = path.resolveSibling(
                                path.getFileName().toString() + ".json");
                    }
                    final Path finalPath = path;
                    Minecraft.getInstance().execute(() -> onResult.accept(finalPath));
                }
            } catch (Throwable t) {
                System.err.println("[Anvil's Search] FileDialog save failed: " + t);
                t.printStackTrace();
            } finally {
                dialogOpen = false;
            }
        }, "Anvil's Search - File Dialog").start();
    }

    // ============================================================
    // ===== 分发 =================================================
    // ============================================================

    private static File showDialog(boolean save, String defaultName) throws Exception {
        System.setProperty("java.awt.headless", "false");
        if (isWindows()) {
            try {
                return showWindowsDialog(save, defaultName);
            } catch (Throwable t) {
                System.err.println("[Anvil's Search] Native COM dialog failed, falling back to AWT: " + t);
            }
        }
        return showAwtDialog(save, defaultName);
    }

    // ============================================================
    // ===== Windows：COM IFileOpenDialog / IFileSaveDialog =======
    // ============================================================

    private interface Ole32Lib extends Library {
        int CoInitializeEx(Pointer pvReserved, int dwCoInit);
        int CoCreateInstance(Pointer rclsid, Pointer pUnkOuter, int dwClsContext,
                             Pointer riid, PointerByReference ppv);
        void CoTaskMemFree(Pointer pv);
        void CoUninitialize();
    }

    private interface Shell32Lib extends Library {
        int SHCreateItemFromParsingName(WString pszPath, Pointer pbc,
                                        Pointer riid, PointerByReference ppv);
    }

    private static final class Ole32Holder {
        static final Ole32Lib INSTANCE =
                Native.load("ole32", Ole32Lib.class, W32APIOptions.UNICODE_OPTIONS);
    }

    private static final class Shell32Holder {
        static final Shell32Lib INSTANCE =
                Native.load("shell32", Shell32Lib.class, W32APIOptions.UNICODE_OPTIONS);
    }

    private static final String CLSID_FILE_OPEN_DIALOG = "DC1C5A9CE88A4DDEA5A160F82A20AEF7";
    private static final String CLSID_FILE_SAVE_DIALOG = "C0B4E2F3BA2147738DBA335EC946EB8B";
    private static final String IID_I_FILE_OPEN_DIALOG = "D57C7288D4AD4768BE029D969532D960";
    private static final String IID_I_FILE_SAVE_DIALOG = "84BCCD235FDE4CDBAEA4AF64B83D78AB";
    private static final String IID_I_SHELL_ITEM       = "43826D1EE71842EEBC55A1E261C37BFE";

    private static final int CLSCTX_INPROC_SERVER     = 0x1;
    private static final int COINIT_APARTMENTTHREADED = 0x2;
    private static final int FOS_OVERWRITEPROMPT      = 0x2;
    private static final int FOS_FORCEFILESYSTEM      = 0x40;
    private static final int FOS_PATHMUSTEXIST        = 0x800;
    private static final int FOS_FILEMUSTEXIST        = 0x1000;
    private static final int SIGDN_FILESYSPATH        = 0x80058000;
    private static final int HR_CANCELLED             = 0x800704C7;

    private static final int VT_RELEASE               = 2;
    private static final int VT_SHOW                  = 3;
    private static final int VT_SET_FILE_TYPES        = 4;
    private static final int VT_SET_OPTIONS           = 9;
    private static final int VT_GET_OPTIONS           = 10;
    private static final int VT_SET_FOLDER            = 12;
    private static final int VT_SET_FILE_NAME         = 15;
    private static final int VT_SET_TITLE             = 17;
    private static final int VT_GET_RESULT            = 20;
    private static final int VT_SET_DEFAULT_EXTENSION = 22;
    private static final int VT_ITEM_GET_DISPLAY_NAME = 5;

    private static File showWindowsDialog(boolean save, String defaultName) {
        Ole32Lib ole32 = Ole32Holder.INSTANCE;
        int hrInit = ole32.CoInitializeEx(Pointer.NULL, COINIT_APARTMENTTHREADED);
        boolean needUninit = (hrInit == 0 || hrInit == 1);
        try {
            // ★ 保持 GUID Memory 引用直到调用结束（防止 GC 提前回收）
            Pointer clsid = guid(save ? CLSID_FILE_SAVE_DIALOG : CLSID_FILE_OPEN_DIALOG);
            Pointer iid   = guid(save ? IID_I_FILE_SAVE_DIALOG : IID_I_FILE_OPEN_DIALOG);

            PointerByReference dialogRef = new PointerByReference();
            check(ole32.CoCreateInstance(clsid, Pointer.NULL, CLSCTX_INPROC_SERVER,
                    iid, dialogRef), "CoCreateInstance");
            Pointer dialog = dialogRef.getValue();
            try {
                // ★★★ 关键修复：GetOptions 返回的是 DWORD（4字节），用 IntByReference
                IntByReference optsRef = new IntByReference();
                check(comCall(dialog, VT_GET_OPTIONS, dialog, optsRef), "GetOptions");
                int options = optsRef.getValue()
                        | FOS_FORCEFILESYSTEM | FOS_PATHMUSTEXIST
                        | (save ? FOS_OVERWRITEPROMPT : FOS_FILEMUSTEXIST);
                check(comCall(dialog, VT_SET_OPTIONS, dialog, options), "SetOptions");

                // 文件类型下拉：JSON (*.json)
                Memory filterName = wideMemory("JSON 文件");
                Memory filterSpec = wideMemory("*.json");
                Memory filter = new Memory(2L * Native.POINTER_SIZE);
                filter.setPointer(0, filterName);
                filter.setPointer(Native.POINTER_SIZE, filterSpec);
                check(comCall(dialog, VT_SET_FILE_TYPES, dialog, 1, filter), "SetFileTypes");

                WString title = new WString(save
                        ? "Anvil's Search - Export"
                        : "Anvil's Search - Import");
                check(comCall(dialog, VT_SET_TITLE, dialog, title), "SetTitle");

                String dir = lastDirectory;
                if (dir != null && !dir.isEmpty()) {
                    setFolder(dialog, dir);
                }

                if (save) {
                    if (defaultName != null && !defaultName.isEmpty()) {
                        WString wname = new WString(defaultName);
                        check(comCall(dialog, VT_SET_FILE_NAME, dialog, wname), "SetFileName");
                    }
                    WString ext = new WString("json");
                    check(comCall(dialog, VT_SET_DEFAULT_EXTENSION, dialog, ext),
                            "SetDefaultExtension");
                }

                int hr = comCall(dialog, VT_SHOW, dialog, Pointer.NULL);
                if (hr == HR_CANCELLED) return null;
                check(hr, "Show");

                PointerByReference resultRef = new PointerByReference();
                check(comCall(dialog, VT_GET_RESULT, dialog, resultRef), "GetResult");
                Pointer item = resultRef.getValue();
                try {
                    PointerByReference nameRef = new PointerByReference();
                    check(comCall(item, VT_ITEM_GET_DISPLAY_NAME, item,
                            SIGDN_FILESYSPATH, nameRef), "GetDisplayName");
                    Pointer namePtr = nameRef.getValue();
                    try {
                        return new File(namePtr.getWideString(0));
                    } finally {
                        ole32.CoTaskMemFree(namePtr);
                    }
                } finally {
                    comCall(item, VT_RELEASE, item);
                }
            } finally {
                comCall(dialog, VT_RELEASE, dialog);
            }
        } finally {
            if (needUninit) ole32.CoUninitialize();
        }
    }

    private static void setFolder(Pointer dialog, String dir) {
        try {
            PointerByReference itemRef = new PointerByReference();
            Pointer iid = guid(IID_I_SHELL_ITEM);
            int hr = Shell32Holder.INSTANCE.SHCreateItemFromParsingName(
                    new WString(dir), Pointer.NULL, iid, itemRef);
            if (hr < 0) return;
            Pointer item = itemRef.getValue();
            try {
                comCall(dialog, VT_SET_FOLDER, dialog, item);
            } finally {
                comCall(item, VT_RELEASE, item);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 按 vtable 序号调用 COM 方法。
     * ★ 修正：使用 Function.getFunction(Pointer) 不带调用约定（x64 上唯一约定）
     */
    private static int comCall(Pointer comObject, int vtableIndex, Object... args) {
        Pointer vtable = comObject.getPointer(0);
        Pointer fn = vtable.getPointer((long) vtableIndex * Native.POINTER_SIZE);
        // x64 上只有一种调用约定；x86 上 JNA 默认 win32 stdcall 与 COM 一致
        Function function = Function.getFunction(fn, Function.ALT_CONVENTION);
        return function.invokeInt(args);
    }

    private static void check(int hr, String what) {
        if (hr < 0) {
            throw new IllegalStateException(what + " failed: 0x"
                    + Integer.toHexString(hr));
        }
    }

    private static Pointer guid(String hex32) {
        ByteBuffer bb = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt((int) Long.parseLong(hex32.substring(0, 8), 16));
        bb.putShort((short) Integer.parseInt(hex32.substring(8, 12), 16));
        bb.putShort((short) Integer.parseInt(hex32.substring(12, 16), 16));
        for (int i = 16; i < 32; i += 2) {
            bb.put((byte) Integer.parseInt(hex32.substring(i, i + 2), 16));
        }
        Memory m = new Memory(16);
        m.write(0, bb.array(), 0, 16);
        return m;
    }

    private static Memory wideMemory(String s) {
        byte[] data = (s + "\u0000").getBytes(StandardCharsets.UTF_16LE);
        Memory m = new Memory(data.length);
        m.write(0, data, 0, data.length);
        return m;
    }

    // ============================================================
    // ===== 兜底 / 非 Windows：AWT FileDialog ====================
    // ============================================================

    private static File showAwtDialog(boolean save, String defaultName) throws Exception {
        final String title = save ? "Anvil's Search - Export" : "Anvil's Search - Import";
        FileDialog dialog = new FileDialog((Frame) null, title,
                save ? FileDialog.SAVE : FileDialog.LOAD);

        if (lastDirectory != null && !lastDirectory.isEmpty()) {
            dialog.setDirectory(lastDirectory);
        }

        if (save) {
            if (defaultName != null && !defaultName.isEmpty()) {
                dialog.setFile(defaultName);
            }
        } else {
            if (isWindows()) {
                dialog.setFile("*.json");
            } else {
                dialog.setFilenameFilter((dir, name) ->
                        name.toLowerCase(Locale.ROOT).endsWith(".json"));
            }
        }

        dialog.setVisible(true);
        String file = dialog.getFile();
        if (file == null) return null;
        String dir = dialog.getDirectory();
        return new File(dir == null ? "" : dir, file);
    }

    private static void rememberDirectory(File selected) {
        File parent = selected.getParentFile();
        if (parent != null) {
            lastDirectory = parent.getAbsolutePath();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}