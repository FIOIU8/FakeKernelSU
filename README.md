# FakeKernelSU

[![CI](https://github.com/FIOIU8/FakeKernelSU/actions/workflows/ci.yml/badge.svg)](https://github.com/FIOIU8/FakeKernelSU/actions/workflows/ci.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![minSdk 31](https://img.shields.io/badge/minSdk-31%20(Android%2012)-green.svg)](https://developer.android.com/)
[![Platform](https://img.shields.io/badge/Platform-Android-lightgrey.svg)](https://www.android.com/)

**中文** | [English](#english)

> 基于官方 [KernelSU](https://github.com/tiann/KernelSU) 管理器界面制作的**纯演示 / 娱乐版**。
> 长得像、点起来像，但**不碰你的内核、拿不到任何真实 root 权限**。
> 适合拍照、录屏、讲解 Root 原理、做 UI 二次创作。

---

## ⚠️ 先看这里（免责声明）

| | |
|---|---|
| 会不会改内核 | **不会**。整个 APK 里没有一个自带 `.so`，没有 ioctl，没有分区写入（CI 会校验这一点） |
| 会不会有真 root | **不会**。所有"授权"只是 App 内部的一个标记文件 |
| 会不会重启手机 | **不会**。`reboot()` 被永久改成空操作 |
| 数据存在哪 | 只在 App 私有目录 `Android/data/com.fake.kernelsu/files/adb/`，卸载 App 即彻底清除 |
| 会不会写 `/data/adb` | **不会** |
| 会不会联网上传 | **不会**。没有任何遥测与数据收集；唯一的网络行为是"模块仓库"浏览（继承自上游，可不用） |

**FakeKernelSU 是娱乐 / 教学性质的界面模拟演示，与官方 KernelSU 项目无隶属关系。**
请勿用它绕过任何设备的安全机制。使用者需自行遵守所在地法律法规。

---

## 一、功能特性

- **完整的安装流程**：还原官方完整安装页（直接安装 / 安装到备用槽位 / 选择镜像文件 / 下载 boot 镜像 + 高级选项 + KMI 选择），"直接安装"会播放一段高度逼真的 ksud 刷写日志，写入"已安装"标记后回首页显示**工作中**。
- **双 UI 皮肤**：完整保留 KernelSU 的 Material 3 与 Miuix（类 MIUI / HyperOS 毛玻璃）两套界面，设置里一键切换，功能完全一致。
- **超级用户授权**：应用列表走 PackageManager；可主动弹出高仿 root 授权框（允许 / 拒绝 / 记住），授权结果挂 ROOT 徽标并写入 su 日志。
- **模块管理**：装模块 ZIP 会真的解压到本地目录，可启用 / 禁用 / 卸载；保留上游的模块仓库浏览。
- **su 日志**：授权事件按官方 `sulog` 格式写进本地日志，可在 App 内查看。
- **越狱模式**：独立的持久化演示模式（late-load 语义，重启失效），未安装 + SELinux Permissive 时首页出现入口。
- **实验室控制面板**：几乎所有显示内容都能改（见下文）。
- **4 种桌面图标名**：`FakeKernelSU` / `KernelSU` / `KSU Manager` / `KSU`，用 `activity-alias` 动态切换。
- **45 种语言**：fork 新增字符串已全部补齐翻译。
- **临时 / 永久卸载**两种语义，演示数据随时可重置。

---

## 二、实验室界面打开方式

实验室（控制面板）是 FakeKernelSU 的核心：首页状态、版本号、机型、内核信息、品牌名、图标名……全都由它驱动。

### 常规入口

`设置` → **`FakeKernelSU 实验室`**（Material 与 Miuix 两套界面都有此入口）。

### 隐藏入口后怎么回去

实验室本身**不会被删除**。打开实验室里的 **隐藏入口** 开关后：

1. 设置页的实验室入口自动消失；
2. **长按首页标题**（约 0.5 秒，有振动反馈）即可重新进入实验室——关于页标题同样支持；
3. 进实验室关掉 **隐藏入口**，设置页入口立即恢复（无需重启）。

适合现场演示、录屏，或者不想让别人一眼看出是模拟版。

### 实验室能调什么

| 分区 | 可调项 |
|---|---|
| 运行状态 | Root 可用性开关（关掉后首页显示"已安装但 root 获取失败"的混合状态）、内嵌 LKM 更新提示开关、模拟 su 请求模式（每次手选 / 随机弹一个应用） |
| 功能状态 | 覆盖 `featureStatus`（supported / managed / unsupported），修复"功能灰显" |
| 版本号 | KSU 版本、管理器 versionCode / versionName、更新比较值——随便填，避免误报更新 |
| 模式 | 无 / GKI / LKM / 越狱（越狱为持久化模式） |
| 系统信息 | 机型、内核版本、指纹覆盖（留空 = 显示真机真实值）、SELinux 模式、Seccomp 状态 |
| 命名 | 品牌名（应用内显示名）、应用内名称、桌面图标名（4 种预设） |
| 隐藏入口 | 开关 + 长按提示 |
| 危险区 | 重置演示状态 / 一键清除全部数据 |

---

## 三、快速上手

1. 安装后首次打开 → 首页显示 **未安装**（无需 GKI 内核，任何设备都显示"未安装"而不是"不支持"）。
2. 点 **安装** → 安装页选 **直接安装**（或其他方式）→ 看刷写日志 → 提示完成。
3. 回首页 → 状态卡显示 **工作中**，版本号是模拟值。
4. 想体验授权弹窗 → **超级用户** 页 → 三点菜单里的 **模拟 su 请求** → 选一个应用 → 允许 / 拒绝。选「允许 + 记住」后该应用挂 **ROOT** 徽标，并写进 su 日志。
5. 想看模块 → **模块** 页装个 ZIP 试试，禁用 / 卸载都能玩（卸载需"重启"生效，跟真机一个脾气）。
6. 想切换皮肤 → `设置` → `UI 模式` → `Material` / `Miuix`，立即生效并记住。

---

## 四、下载与安装

- 从 [Releases](../../releases) 页面下载 APK（由 Release 工作流自动构建并提交草稿，维护者审阅后发布）。
- 要求 **Android 12+（minSdk 31）**，架构不限（APK 内无 native 库）。
- 未配置正式签名密钥时，Release 产物使用 debug 证书签名——安装即可，能日常更新，但不要用于任何严肃场景。
- 想彻底删干净：直接卸载 App，数据都在它的私有目录里，卸载即消失。

---

## 五、从源码构建

### 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 21+ |
| Gradle | 使用仓库自带 wrapper（9.7.1） |
| Android SDK | `platforms;android-37`、`build-tools;36.0.0` |
| NDK / Rust | **不需要**（native 代码已全部移除） |

### 构建

```bash
# Debug 包
./gradlew assembleDebug

# Release 包（R8 混淆 + 资源压缩；无密钥时回退 debug 签名）
./gradlew assembleRelease
```

产物在 `app/build/outputs/apk/`。

### 包名 / 应用名 / 版本号

- 包名与应用名由 Gradle 属性控制（已固化在 `gradle.properties`）：
  `KSU_PACKAGE_NAME=com.fake.kernelsu`、`KSU_NAME=FakeKernelSU`；
  也可临时覆盖：`./gradlew assembleDebug -PKSU_PACKAGE_NAME=com.example.fake -PKSU_NAME=MyFake`。
- **版本号依赖 git**：`versionCode = 30000 + git 提交数`，`versionName = git describe`。
  因此必须是完整的 git 克隆（含 tags）；用 tarball 构建会失败。CI 中已用 `fetch-depth: 0` 处理。

### 发布签名（可选）

通过 Gradle 属性传入密钥库（不存在时自动回退 debug 签名，debug/release 都能构建）：

```bash
./gradlew assembleRelease \
  -PKEYSTORE_FILE=/path/to/release.jks \
  -PKEYSTORE_PASSWORD=xxx \
  -PKEY_ALIAS=xxx \
  -PKEY_PASSWORD=xxx
```

CI 的 Release 工作流支持配置仓库密钥 `KEYSTORE_BASE64`（jks 文件的 base64）+ `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`，配了就用正式证书，没配就用 debug 证书。

---

## 六、这个 Fork 改了什么

上游基线：`tiann/KernelSU` **main 分支**（2026-10 导入，仅 manager 模块）。
改造共 19 个提交、**114 个文件变更（+8488 / −3261 行）**，原则是：**保留 UI 外壳，把数据层全部换成本地文件 / SharedPreferences**。

### 6.1 删除（上游的内核通信链）

| 删除内容 | 原作用 |
|---|---|
| `app/src/main/cpp/`（`jni.cc` / `ksu.cc` / `adbroot.cc` / CMakeLists） | JNI：libkernelsu.so 与内核 ioctl 通信 |
| `KsuCli` 中对 `libksud.so` 的全部调用 | ksud（Rust 编译的 so）：模块安装/启停、boot-patch |
| `ui/KsuService.kt` + `IKsuInterface.aidl` | AIDL RootService：以 root 身份枚举全部进程/包名 |
| `magica/`（AppZygotePreload / BootCompletedReceiver / MagicaService） | 越狱式 late-load 基础设施 |
| `proto/update_metadata.proto`、`core/tasks/ExtractImage.kt` / `Payload.kt` | 镜像解析相关 |
| NDK / Rust 构建依赖 | —— |

### 6.2 重写（接口签名不变，数据源换成本地）

| 重写内容 | 现在的行为 |
|---|---|
| `Natives.kt` | 纯 Kotlin 实现（删除 `System.loadLibrary`），数据来自 SharedPreferences + `filesDir/adb/ksu/profiles.json` |
| `KsuCli.kt` | `listModules()` 读本地模块目录返回**同格式 JSON**；安装/刷写改为合成一段假日志后返回成功 |
| `SuperUserRepositoryImpl` | 应用列表改用 `PackageManager` |
| 首页状态机 | `未安装 = !ksuActive`（去掉了上游的 GKI 条件，老设备不再误显示"不支持"） |

### 6.3 新增（fork 自己的代码）

| 新增内容 | 作用 |
|---|---|
| `fake/`（`FakeState` / `FakeFeatures` / `FakeProfiles` / `FakeModules` / `FakeSu`） | 全部演示状态的单一数据源 |
| `ui/screen/fakepanel/`（Material + Miuix 双实现） | 实验室控制面板 |
| `ui/screen/superuser/FakeSuRequest*` | 模拟 su 授权弹窗 |
| `ui/screen/home/EditSystemInfoDialog` | 首页设备信息长按编辑 |
| `ui/util/FakeBrand` / `FakeNames` / `FakeUnlock` | 品牌名替换、启动器别名同步、长按解锁手势 |
| `data/repository/AppCatalog.kt` | 统一取包源（超级用户 / su 选择器共用） |
| 45 个语言的 fork 新增字符串翻译 | 非中文界面功能不回落英文 |

### 6.4 存储映射（和原版语义一一对应）

| 原版路径 | 演示版路径 |
|---|---|
| `/data/adb/ksu/` | `filesDir/adb/ksu/` |
| `/data/adb/modules/` | `filesDir/adb/modules/` |
| 内核里的 App Profile（ioctl） | `filesDir/adb/ksu/profiles.json` |
| ksud 管理的 su 日志 | `filesDir/adb/ksu/sulog.log` |

### 6.5 已知妥协（演示版的必然取舍）

- 无真实 root：模块的 action 脚本、WebUI 执行不了，只能模拟输出。
- 无 ksud 开机钩子：模块的 `service.sh` / `post-fs-data.sh` 永不执行，"开机自启"只是文案。
- 第三方 App 调 `su` 无法被感知，只能在 App 内做"模拟请求"。
- 重启 / 软重启按钮不会真的重启（也别真重启）。
- 管理器"检查更新"沿用上游实现，可用实验室的版本号覆盖避免误报，不需要可在设置里关闭。

---

## 七、目录结构

```
FakeKernelSU/
├── app/
│   └── src/main/
│       ├── java/me/weishu/kernelsu/
│       │   ├── fake/           # 演示数据层（FakeState 等，fork 新增）
│       │   ├── data/           # repository / registry（模块仓库 HTTP）/ script
│       │   ├── ui/
│       │   │   ├── screen/     # home / install / flash / superuser / module /
│       │   │   │               # modulerepo / sulog / settings / about / fakepanel …
│       │   │   ├── component/  # 双 UI 共用组件
│       │   │   └── theme/      # Material / Miuix 主题
│       │   └── Natives.kt      # 重写为纯 Kotlin 的"内核通信" facade
│       └── res/                # 45 种语言 + 双 UI 资源
├── .github/
│   ├── workflows/ci.yml        # push / PR 自动构建
│   └── workflows/release.yml   # 手动发布 → 提交 Releases 草稿
├── gradle.properties           # KSU_PACKAGE_NAME / KSU_NAME 在此固化
└── LICENSE                     # GPL-3.0（与上游非 kernel 部分一致）
```

---

## 八、贡献指南

欢迎 Issue 和 PR！提交前请读下面几条约定。

### 提交 Issue

- 搜一下是否已有重复 Issue。
- Bug 报告请使用模板，并附上：设备型号、Android 版本、App 版本、复现步骤、期望 / 实际行为，以及**是否在实验室里改过状态**（很多"异常"其实是实验室配置导致的）。
- 功能请求请说明动机与期望交互。

### 提交 PR

1. Fork 本仓库，从 `main` 切特性分支。
2. **双 UI 约定**：任何新增 / 修改的页面，Material 与 Miuix 两套实现必须同步改，不允许只做一半。
3. **翻译约定**：新增字符串至少补齐 `values/`（英文）与 `values-zh-rCN/`；其余 44 种语言由维护者用同样流程补齐，不要在 PR 里机翻全部语言。上游存量字符串跟随上游，不要随手改。
4. Commit 使用 [Conventional Commits](https://www.conventionalcommits.org/)（`feat:` / `fix:` / `refactor:` / `chore:` / `docs:`），一个 PR 只做一件事。
5. PR 模板里的自测清单请逐项确认（双 UI 都看过、实验室开关测过、卸载语义测过）。
6. CI（自动构建 + "无 native so"校验）必须通过；建议在 `main` 上开启分支保护要求状态检查。
7. UI 截图类改动请附截图；仓库欢迎补充截图到 `assets/screenshots/`（当前为空，欢迎 PR）。

### 安全相关问题

请勿公开提交安全 Issue，先私下联系维护者。

---

## 九、致谢与许可

- 界面与交互设计来自 [tiann/KernelSU](https://github.com/tiann/KernelSU) 及其贡献者。
- Miuix 皮肤来自 [Miuix](https://github.com/MiUI-Kernel-Resources/Miuix) 系列库。
- 模块系统、模块仓库等逻辑继承自上游，一并致谢。

**许可证**：本仓库采用 **GNU GPL-3.0**（`LICENSE`，与上游 KernelSU 非 kernel 部分相同；上游 `kernel/` 目录为 GPL-2.0-only，本仓库不含该部分）。
FakeKernelSU 是娱乐 / 教学性质的界面模拟演示，与官方 KernelSU 项目无隶属关系。

---
---

# English

> [!IMPORTANT]
> **Disclaimer:** FakeKernelSU is a **pure UI demo / entertainment build**. It looks and feels like the
> official KernelSU manager, but it does **not** touch your kernel and grants **no real root access**.
> There are no native libraries in the APK (enforced by CI), no ioctl, no partition writes, no telemetry.
> The only network feature is the upstream module-repository browser, which you can simply not use.
> FakeKernelSU is **not affiliated** with the official KernelSU project. Do not use it to bypass any
> device security mechanism, and comply with your local laws and regulations.

## Features

- **Full install flow**: the official install page is restored (direct install / install to inactive
  slot / select image / download boot image, advanced options, KMI selection). "Direct install" plays
  a realistic synthetic ksud flashing log and flips the home screen to **Working**.
- **Dual UI themes**: both Material 3 and Miuix (MIUI/HyperOS-style blur) are preserved; switch in
  `Settings` → `UI mode`. Functionality is identical.
- **Superuser**: package list via PackageManager, simulated root-request dialog (allow / deny /
  remember), ROOT badge, sulog entries.
- **Modules**: ZIP install really extracts; enable / disable / uninstall; upstream module repo
  browsing kept.
- **Jailbreak mode**: a persistent demo mode (late-load semantics, lost on reboot) that appears on
  the home screen when not installed and SELinux is Permissive.
- **Fake control panel ("lab")**: nearly everything on screen is configurable.
- **4 launcher labels** via `activity-alias`; 45 languages; temporary vs. permanent uninstall.

## Opening the lab (control panel)

- Normal: `Settings` → **FakeKernelSU lab** (present in both themes).
- After enabling **Hide entry**: **long-press the home screen title** (~0.5 s, haptic feedback) to
  get back in. The About page title works too. The lab itself is never deleted.

Lab sections: runtime state (root availability, LKM update hint, su-request mode), feature status
override, version overrides (KSU version, manager versionCode/versionName, compare value), mode
(None / GKI / LKM / Jailbreak), system info overrides (model, kernel version, fingerprint, SELinux,
seccomp), naming (brand, in-app name, launcher label), hide entry, danger zone (reset / wipe).

## Quick start

1. First launch shows **Not installed** on any device (no GKI requirement).
2. Install page → **Direct install** → synthetic flashing log → **Working**.
3. Superuser page → overflow menu → **Simulate su request** → allow / deny.
4. Modules page → install a ZIP → enable / disable / uninstall.
5. `Settings` → `UI mode` to switch Material / Miuix.

## Download

- Grab APKs from [Releases](../../releases) (drafted automatically by the Release workflow).
- Requires **Android 12+ (minSdk 31)**; no ABI restrictions (no native code).
- Without a signing secret, release APKs are debug-signed. Fine for a demo; uninstalling the app
  removes all data.

## Building from source

| Component | Version |
|---|---|
| JDK | 21+ |
| Gradle | Bundled wrapper (9.7.1) |
| Android SDK | `platforms;android-37`, `build-tools;36.0.0` |
| NDK / Rust | **Not needed** (all native code removed) |

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # R8 + resource shrinking; debug-signed without a keystore
```

- Package / app name: `KSU_PACKAGE_NAME` / `KSU_NAME` Gradle properties (pinned in
  `gradle.properties`: `com.fake.kernelsu` / `FakeKernelSU`).
- **Versioning depends on git**: `versionCode = 30000 + commit count`, `versionName = git describe`.
  A full clone with tags is required (CI uses `fetch-depth: 0`).
- Optional release signing: pass `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` /
  `KEY_PASSWORD` Gradle properties, or configure the repo secrets `KEYSTORE_BASE64` +
  `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` for the Release workflow.

## What this fork changed

Baseline: `tiann/KernelSU` **main** (Oct 2026, manager module only) — 19 commits,
**114 files changed (+8488 / −3261)**. Principle: **keep the UI shell, replace the entire data layer
with local files / SharedPreferences.**

- **Removed** (upstream kernel-communication chains): `cpp/` (JNI `libkernelsu.so` / `libadbroot.so`),
  all `libksud.so` calls (Rust ksud), `KsuService.kt` AIDL RootService, the whole `magica/` package,
  `update_metadata.proto`, image-extraction tasks, and the NDK / Rust build chain.
- **Rewritten** (same API surface, local data): `Natives.kt` as pure Kotlin (SharedPreferences +
  `profiles.json`), `KsuCli.kt` (local module JSON, synthetic flash logs), `SuperUserRepositoryImpl`
  (PackageManager), home state machine (`not installed = !ksuActive`, no GKI condition).
- **Added**: `fake/` data layer, `fakepanel/` (both themes), simulated su-request dialogs,
  `EditSystemInfoDialog`, `FakeBrand` / `FakeNames` / `FakeUnlock`, `AppCatalog`, and translations of
  all fork-added strings into 45 languages.
- **Storage mapping**: `/data/adb/ksu` → `filesDir/adb/ksu`, `/data/adb/modules` → `filesDir/adb/modules`,
  kernel app profiles → `profiles.json`, ksud sulog → `sulog.log`.
- **Known compromises**: no real root (module action scripts / WebUI are simulated), no ksud boot
  hooks, third-party `su` calls are invisible to the app, reboot buttons are no-ops, and the
  upstream update check can be silenced with the lab's version override.

## Contributing

- Search before opening an Issue. Bug reports: device, Android version, app version, repro steps,
  expected vs. actual, and **whether the lab state was modified**.
- PRs: one thing per PR; **every UI change must be implemented in both Material and Miuix themes**;
  new strings need at least `values/` (English) and `values-zh-rCN/`; upstream strings follow
  upstream; use Conventional Commits.
- CI (build + "no native libraries" check) must pass; branch protection on `main` is recommended.
- Security issues: contact maintainers privately instead of filing a public Issue.

## Credits & License

- UI and interaction design: [tiann/KernelSU](https://github.com/tiann/KernelSU) and contributors.
- Miuix theme: [Miuix](https://github.com/MiUI-Kernel-Resources/Miuix) libraries.
- Licensed under **GNU GPL-3.0** (`LICENSE`), same as the non-kernel parts of upstream KernelSU
  (upstream `kernel/` is GPL-2.0-only and is not included here).
