# 🔨 Anvil's Search

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.18.2-3B8C4A?style=flat-square)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-LGPL--3.0-blue?style=flat-square)](LICENSE.txt)

---

## 📖 中文

### 📦 关于

**Anvil's Search** 是 **[Tinkers' Search](https://github.com/leipishu/TinkersSearch)** 的配套扩展，把搜索体验延伸到**工匠砧 (Tinker's Anvil)** 界面。

### 😊 支持版本

**Anvil's Search** 与 **Tinkers' Search** 同步支持 **匠魂 (Tinkers' Construct)** 的所有版本（当前为 1.18.2）。

### ✨ 功能特性

- 🔧 **侧边栏面板** - 在工匠砧界面左侧添加可收起的侧边栏，不离开界面即可规划工具
- 📑 **三 Tab 页面** - 部件 / 模拟 / 强化，一键切换
- 🔎 **部件浏览** - 列出每一个可制作部件及其全部可产出材料
- 🈶 **拼音搜索** - 复用 Tinkers' Search 的拼音搜索（全拼 / 首字母）
- 🧪 **工具模拟** - 选择工具 → 填入部件材料 → 实时预览属性、词条与图标
- 🔒 **完全只读** - 模拟过程不会消耗材料，也不会修改工匠砧
- 💾 **保存预设** - 保存到 `config/anvilssearch-presets/`，文件名带时间戳，不覆盖旧方案
- 📤 **导出 / 导入** - 通过系统原生文件对话框导出；支持从剪贴板或文件导入
- 🖼️ **方案浏览器** - 内置预设浏览窗口，每个方案以**实际材料渲染的工具图标**展示
- 🗑️ **方案删除** - 一键删除不需要的预设
- 🧬 **强化查询** - 搜索所有匠魂强化配方，按槽位类型（升级 / 能力 / 防御 / 灵魂）过滤
- ✅ **前置校验** - 实时判断强化是否适用于当前物品
- ⭐ **收藏系统** - 强化可收藏，独立展示

### 🎮 操作指南

| 操作 | 功能 |
|---|---|
| **按 `G` 键（可重绑）** | 开关侧边栏面板 |
| **点击顶部 Tab** | 切换 部件 / 模拟 / 强化 页面 |
| **搜索框输入** | 按名称或拼音过滤（支持全拼、首字母） |
| **左键点击部件槽** | 打开材料选择器 |
| **右键点击部件槽** | 展开 / 收起材料详情卡片 |
| **点击「保存」** | 保存到 `config/anvilssearch-presets/` |
| **点击「导出」** | 通过系统原生对话框选择导出位置 |
| **点击「导入」** | 打开方案浏览器 |
| **点击方案行** | 应用该预设到模拟器 |
| **点击方案行右侧 ✕** | 删除该预设 |

> 💡 保存与导出需要**填满所有部件槽位**。未填满的工具会被拒绝并给出提示。

### ⚙️ 前置要求

| 模组 | 必需 | 说明 |
|---|---|---|
| **Tinkers' Search** | ✅ 必需 | 本模组的搜索与 UI 基础 |
| **Tinkers' Construct** | ✅ 必需 | 工匠砧与强化系统 |
| **Mantle** | ✅ 必需 | Tinkers' Construct 前置 |
| **JEI (Just Enough Items)** | ❌ 可选 | 提供配方查询联动 |

> 💡 本模组**强依赖 Tinkers' Search**，请务必先安装它。搜索、拼音、UI 组件均来自 Tinkers' Search。

### 📥 下载

- **[Modrinth](https://modrinth.com/mod/anvils-search)**
- **[CurseForge](https://www.curseforge.com/minecraft/mc-mods/anvils-search)**
- GitHub Releases

### 🛠️ 开发构建

```bash
git clone https://github.com/leipishu/AnvilsSearch.git
cd AnvilsSearch
./gradlew build
```

构建产物位于 `build/libs/` 目录。

开发环境运行：

```bash
./gradlew runClient
```

### 📄 许可证

本项目采用 **GNU Lesser General Public License v3.0 only**（LGPL-3.0-only）开源协议。

详见 [LICENSE.txt](LICENSE.txt)。

### 🙏 致谢

- **Tinkers' Search** - 提供 UI 基础、拼音搜索与大量复用组件
- **Tinkers' Construct** - 提供工匠砧 API 与强化系统
- **Mantle** - Tinkers' Construct 前置库
- **pinyin4j** - 提供汉字转拼音支持（由 Tinkers' Search 捆绑）
- 所有反馈问题和提出建议的用户

### 📞 联系方式

- 作者: [Leipishu](https://github.com/leipishu)
- Issue Tracker: [GitHub Issues](https://github.com/leipishu/AnvilsSearch/issues)

---

## 📖 English

### 📦 About

**Anvil's Search** is a companion addon for **[Tinkers' Search](https://github.com/leipishu/TinkersSearch)**, extending its search experience to the **Tinker's Anvil** GUI.

### 😊 Supported Versions

**Anvil's Search** mirrors **Tinkers' Search** and supports all versions of **Tinkers' Construct** (currently 1.18.2).

### ✨ Features

- 🔧 **Side Panel** - Adds a collapsible sidebar to the Tinker's Anvil GUI, letting you plan tools without leaving the screen
- 📑 **Three Tabs** - Parts / Simulator / Modifiers, switch with one click
- 🔎 **Parts Browser** - Lists every craftable part and all materials that can produce it
- 🈶 **Pinyin Search** - Reuses Tinkers' Search's pinyin search (full pinyin / initials)
- 🧪 **Tool Simulator** - Pick a tool → fill part slots with materials → preview stats, traits, and icon in real time
- 🔒 **Read-only** - Simulation never consumes materials nor modifies the Anvil
- 💾 **Save Presets** - Save to `config/anvilssearch-presets/`, file name includes a timestamp so old presets are never overwritten
- 📤 **Export / Import** - Export via system-native file dialog; import from clipboard or file
- 🖼️ **Preset Browser** - Built-in preset browser showing each preset's **actual material-rendered tool icon**
- 🗑️ **Delete Presets** - One-click delete for unwanted presets
- 🧬 **Modifier Query** - Search all Tinker's Construct modifier recipes, filter by slot type (Upgrade / Ability / Defense / Soul)
- ✅ **Requirement Check** - Real-time validation of whether a modifier applies to the current item
- ⭐ **Favorites** - Favorite modifiers, displayed in a dedicated section

### 🎮 Controls

| Action | Function |
|---|---|
| **Press `G` (rebindable)** | Toggle the sidebar panel |
| **Click top Tab** | Switch between Parts / Simulator / Modifiers |
| **Search box input** | Filter by name or pinyin (full pinyin, initials) |
| **Left click a part slot** | Open the material picker |
| **Right click a part slot** | Expand / collapse the material detail card |
| **Click "Save"** | Save to `config/anvilssearch-presets/` |
| **Click "Export"** | Choose an export location via system-native dialog |
| **Click "Import"** | Open the preset browser |
| **Click a preset row** | Apply that preset to the simulator |
| **Click the ✕ on a preset row** | Delete that preset |

> 💡 Save and Export require **all part slots to be filled**. Incomplete tools are rejected with a hint.

### ⚙️ Requirements

| Mod | Required | Note |
|---|---|---|
| **Tinkers' Search** | ✅ Required | Search & UI foundation for this mod |
| **Tinkers' Construct** | ✅ Required | Tinker's Anvil and modifier systems |
| **Mantle** | ✅ Required | Tinkers' Construct dependency |
| **JEI (Just Enough Items)** | ❌ Optional | Provides recipe lookup integration |

> 💡 This mod **hard-depends on Tinkers' Search**. Please install it first. Search, pinyin, and UI components all come from Tinkers' Search.

### 📥 Download

- **[Modrinth](https://modrinth.com/mod/anvils-search)**
- **[CurseForge](https://www.curseforge.com/minecraft/mc-mods/anvils-search)**
- GitHub Releases

### 🛠️ Development Build

```bash
git clone https://github.com/leipishu/AnvilsSearch.git
cd AnvilsSearch
./gradlew build
```

Build artifacts are located in `build/libs/`.

To run in a development environment:

```bash
./gradlew runClient
```

### 📄 License

This project is licensed under the **GNU Lesser General Public License v3.0 only** (LGPL-3.0-only).

See [LICENSE.txt](LICENSE.txt) for details.

### 🙏 Credits

- **Tinkers' Search** - UI foundation, pinyin search, and heavily reused components
- **Tinkers' Construct** - Tinker's Anvil API and modifier systems
- **Mantle** - Tinkers' Construct library
- **pinyin4j** - Chinese pinyin conversion support (bundled by Tinkers' Search)
- All users who reported issues and suggested features

### 📞 Contact

- Author: [Leipishu](https://github.com/leipishu)
- Issue Tracker: [GitHub Issues](https://github.com/leipishu/AnvilsSearch/issues)

---

> ⚠️ **Note**: Anvil's Search is a companion addon for Tinkers' Search. You must install Tinkers' Search for it to work.
>
> ⚠️ **注意**: Anvil's Search 是 Tinkers' Search 的配套插件，必须先安装 Tinkers' Search 才能使用。

---

<p align="center">Made with ❤️ by Leipishu</p>