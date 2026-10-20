# FakeKernelSU 改造方案

> 基于官方 `tiann/KernelSU` main 分支（2026-10 拉取）manager 模块的实测代码编写。
> 目标：做一个**不需要内核、不需要 root、纯演示**的 KernelSU 高仿 App。

---

## 一、结论先说

| 问题 | 结论 |
|---|---|
| 整体可行吗？ | **完全可行**。本质是"保留 UI 外壳，把数据层全部换成本地文件/SharedPreferences" |
| 你的设计准确度 | ~90%。有 **4 处需要按真实代码修正**（见第五节），否则会出现"该显示未安装时显示不支持"之类的问题 |
| 工作量最大的部分 | 不是 UI，是 `Natives`（JNI）、`KsuCli`（ksud 调用）、`SuperUserRepository`（RootService AIDL）这三条内核通信链的本地化重写 |
| 最大的意外收获 | 官方构建脚本**原生支持改包名**：`-PKSU_PACKAGE_NAME=xxx` 一个参数即可，不用全局替换 |
| 最大的坑 | main 分支工具链很新（Gradle 9.7 / AGP 9.4 / JDK 21 / compileSdk 37），且**版本号强依赖 git**（非 git 目录直接构建失败） |

---

## 二、基线选择（建议：官方 main 分支）

| 基线 | 优点 | 缺点 | 建议 |
|---|---|---|---|
| **tiann/KernelSU main** | 官方正统；UI 现代（Compose + Material/Miuix 双主题）；包名改造有官方参数 | 工具链新；代码量大（约 250 个 kt 文件） | ✅ **推荐** |
| 旧 tag（如 v1.0.x） | 代码简单，JDK 17 能编 | 获取麻烦（tag tarball 同样要走网络）；UI 老旧 | 备选：嫌工具链重时用 |
| KernelSU-Next | 功能最全、UI 最花哨 | 要改的东西更多 | 不推荐（改造面更大） |

**本机网络提示（实测）**：`git clone github.com` 在本机会报证书吊销错误（`CRYPT_E_NO_REVOCATION_CHECK`），但 `codeload.github.com` 正常。方案：
- 用镜像站 clone，或
- `curl` 下 tarball 后 `git init` + 自己打一个 tag（版本号需要 git，见 4.0）。

---

## 三、真实代码架构（调研结果）

### 3.1 "是否已安装"是怎么判断的

```
Natives.isManager  ──JNI──▶  libkernelsu.so ──ioctl──▶  内核 [ksu_driver]
      │
      ▼
HomeViewModel.buildState()   ksuVersion = if (isManager) Natives.version else null
      │
      ▼
HomeMaterial.StatusCard      ksuVersion != null → "Working（工作中）"
                             ksuVersion == null && isGKI() → "Not installed（未安装）"
                             否则 → "unsupported（不支持）"
```

关键点：**未安装状态在原版里就是"内核里没有 KSU"**，`Natives.version` 返回 0。演示版只需让 `Natives.version` 读本地 flag。

### 3.2 "直接安装"按钮的真实链路

```
InstallScreen（InstallMethod.DirectInstall，仅当 rootAvailable && isGKI 时出现）
   → Route.Flash(FlashIt.FlashBoot(boot=null))
   → FlashScreen（流式显示日志）
   → KsuCli.installBoot() → libksud.so boot-patch -f <partition>   （ksud 是 Rust 编译的 so，随 APK 分发）
   → 成功后提示重启
```

即：原版"直接安装"是**直接刷 boot 分区**，需要已解锁 Bootloader + 真实 root。演示版把这条链换成"写 flag + 演一段合成日志"。

### 3.3 数据都存在哪（原版）

| 数据 | 原版真实位置 | 说明 |
|---|---|---|
| ksud 二进制 | `/data/adb/ksu/` | 开机由内核模块拉起 |
| 模块 | `/data/adb/modules/<id>/` | 注意**不是** `/data/adb/ksu/modules` |
| App root 授权（profiles） | **内核里**（ioctl 读写） | manager 只通过 `Natives.getAppProfile` 读写，`rules` 字段由 ksud 落盘 |
| su 日志 | ksud 管理 | manager 的 Sulog 页只是读 |

### 3.4 三大内核通信链（要拆掉的东西）

| 链路 | 文件 | 干什么用 |
|---|---|---|
| JNI | `Natives.kt` + `cpp/`（libkernelsu.so、libadbroot.so） | 版本查询、profiles 读写、su 开关 |
| ksud | `KsuCli.kt`（调 `libksud.so`，Rust 编的 so） | 模块安装/启停、boot-patch、feature 开关 |
| RootService | `ui/KsuService.kt`（AIDL，libsu 的 RootService） | 以 root 身份列全部进程/包名 |

另有一个 `magica/`（AppZygote 隔离进程 + BootCompletedReceiver），是"越狱式 late-load"的基础设施，演示版**整个删掉**。

---

## 四、改造方案（详细步骤）

### 4.0 工程准备

1. 工作区结构：
```
FakeKernelSU/                  ← 工作区根（改造后的工程 git init 在这里）
├── reference/KernelSU/        ← 官方原始代码（已拉取，只读参考，随时可删）
```
2. 构建环境：JDK 21+、Android SDK（compileSdk 37 / Build Tools 37.0.0）。Gradle 用仓库自带 wrapper（9.7.1）。
3. **必须 git 仓库**：`manager/build.gradle.kts` 里 `getVersionCode()/getVersionName()` 直接 `exec("git rev-list/describe")`，非 git 目录构建直接抛异常。若用 tarball，`git init` 后 `git tag v1.0.0` 再提交一次即可。

### 4.1 改包名（比想象中简单）

```bash
./gradlew assembleDebug -PKSU_PACKAGE_NAME=com.fake.kernelsu -PKSU_NAME=FakeKernelSU
```

- `applicationId`、APK 内 `BuildConfig.APPLICATION_ID` 全部走这个参数；
- Manifest 里的 FileProvider authorities、`taskAffinity`、动态广播权限全是 `${applicationId}` 占位符，自动跟随；
- Kotlin 源码包名（`me.weishu.kernelsu`）**不用动**，只改 applicationId，侵入性为零；
- 建议把这两个参数写进 `gradle.properties` 固化，免得每次敲。

### 4.2 移除内核通信（核心工作量）

| # | 动作 | 细节 |
|---|---|---|
| 1 | **重写 `Natives.kt` 为纯 Kotlin** | 保留全部 API 签名（`version`/`isManager`/`isLkmMode`/`isSafeMode`/`getAppProfile`/`setAppProfile`/`isSuEnabled`/`getSuperuserCount`/UAPI 版本…），数据源换成 SharedPreferences + `filesDir/adb/ksu/profiles.json`。删掉 `System.loadLibrary` |
| 2 | **删除 `cpp/` 目录** | 同时删 `build.gradle.kts` 里的 `externalNativeBuild` 块 → **不再需要 NDK** |
| 3 | **不再需要 Rust** | `libksud.so` 由 `just build_manager` 从 `userspace/ksud`（Rust）编译拷贝。演示版删掉整个 `KsuCli` 的 ksud 调用，Rust 工具链不再需要 |
| 4 | **重写 `KsuCli.kt`** | `createRootShell()` → 不再用 libsu；`execKsud()` → 本地实现；`listModules()` → 读本地模块目录返回**同格式 JSON**（`ModuleRepositoryImpl` 的解析代码可以原样不动）；`installBoot/downloadBoot/restoreBoot/uninstallPermanently` → 合成一段假日志回调，返回成功 |
| 5 | **删 `ui/KsuService.kt`（AIDL）** | `SuperUserRepositoryImpl.getAppList()` 改为直接用 `PackageManager.getInstalledPackages()` 拿包名+uid，profiles 走新的 `Natives` |
| 6 | **删 `magica/` 整个包** | 同步删 Manifest 的 `android:zygotePreloadName`、`MagicaService`、`BootCompletedReceiver` |
| 7 | `MainActivity.onCreate` 里的 `install()` 调用删掉 | 原版启动时确保 ksud 就位，演示版无意义 |
| 8 | `KernelSUApplication.onCreate` 的 `superUserViewModel.loadAppList()` | 改为"仅当 flag 已安装时加载"，避免未安装状态也跑一遍 |

### 4.3 安装状态机（你要的主流程）

```
首次启动：flag=false
  → HomeViewModel: ksuVersion=null
  → 首页大卡片显示「未安装 / 点击安装」（红卡）

点击进入 InstallScreen：
  → 保留「直接安装」按钮（无条件显示，见修正点 2）
  → 点击 → FlashScreen 演一段合成日志：
      "Unpacking ksud..." → "Patching boot image..." → "Installing modules dir..." → "Done"
  → 写 flag=true，初始化 filesDir/adb/{ksu,modules}
  → 返回首页 → 「工作中 v1.0 (UAPI 32513)」（绿卡）

下次启动：flag=true → 直接「工作中」
```

- flag 存放：`SharedPreferences("fake_ksu")` 的 `installed=true` + `installed_at` 时间戳；
- 版本号给个固定假值（如 `version=13000`、`kernelUAPIVersion=managerUAPIVersion`），保证 `isFullFeatured()=true`，底部栏角标、页面滑动限制全部正常；
- **卸载**（设置页）："临时卸载"= 清 flag（重启概念也省了，直接回到未安装 UI）；"永久卸载"= 清 flag + 删 `filesDir/adb/`。

### 4.4 存储重定向（模块/授权全部进应用目录）

新增一个路径常量类，全 App 只认它：

```kotlin
object FakeKsuPaths {
    val root = File(ksuApp.filesDir, "adb")          // /data/data/<pkg>/files/adb
    val ksu = File(root, "ksu")                       // 替代 /data/adb/ksu
    val modules = File(root, "modules")               // 替代 /data/adb/modules
    val profiles = File(ksu, "profiles.json")         // App 授权表（原版在内核里）
    val sulog = File(ksu, "sulog.log")                // su 日志
}
```

- 目录布局刻意保持 `ksu/` + `modules/` 两级，和原版语义一一对应，将来想对照原版逻辑时不用翻译；
- `profiles.json` 结构直接复用 `Natives.Profile` data class（kotlinx.serialization 已有依赖），`packageName → Profile`；
- 模块安装：解压 zip 里的 `module.prop`（id/name/version/versionCode/author/description）→ 在 `modules/<id>/` 写 `module.prop` + `module.zip` + `enabled` 标记文件；启停=改标记；卸载=删目录；
- ModuleRepo（模块仓库，纯 HTTP）**原样保留**，它不依赖 root，演示版里反而能真实浏览下载，装进本地目录。

### 4.5 各页面改造清单

| 页面 | 改造动作 |
|---|---|
| 首页 Home | `Natives.isManager` → 读 flag；`rootAvailable()` → flag 为 true 时返回 true；`notInstalled` 判定去掉 `isGKI()` 条件（见修正点 1） |
| 安装 Install | `installMethodOptions` 只保留「直接安装」+「选择镜像文件(演示无效，可删)」；去掉 KMI 选择、分区选择、越狱相关 UI |
| 超级用户 SuperUser | 应用列表走 PackageManager；授权开关写 `profiles.json`；新增一个「模拟 su 请求」FAB → 弹高仿授权对话框（允许/拒绝/记住）→ 写 profiles + sulog |
| 模块 Module | 列表/启停/卸载全本地化；action/webui 按钮 → Toast「演示版不可用」；保留 ModuleRepo 浏览 |
| 设置 Settings | `KsuIsValid` 改为读 flag；卸载对话框接 4.3 的语义；重启/软重启 → Toast「演示版无法真正重启」；「检查更新」默认关闭或指向空地址 |
| su 日志 Sulog | 读本地 `sulog.log`，模拟授权时追加一行 |
| 关于 About | 加一条声明："FakeKernelSU 演示版，与真实 KernelSU 无关，仅供娱乐/UI 演示" |

### 4.6 构建精简收益

删掉 `cpp/` + ksud 后：**不需要 NDK、不需要 Rust**，构建链回到纯 JDK + Android SDK，出包更快、环境依赖最少。protobuf 插件（script 功能用）可暂时保留（只是代码生成，不影响），也可以随 script 相关页面一起删。

---

## 五、对你设计的修正点（重要）

| # | 你的设计 | 真实代码行为 | 修正 |
|---|---|---|---|
| 1 | 第一次打开显示未安装 | 原版 `未安装 = !ksuActive && kernelVersion.isGKI()`。**内核 < 5.10 的非 GKI 设备会显示"不支持"而不是"未安装"** | 演示版把 `notInstalled` 改为 `!ksuActive`（去掉 GKI 条件），否则在老设备上演示翻车 |
| 2 | 安装界面有"直接安装"按钮 | 原版该按钮只在 `rootAvailable() && isGKI()` 时出现（本版语义是"已有 root 时直接刷"） | 演示版无条件显示；其余安装方式（选镜像/下载/备用槽位）删掉或置灰 |
| 3 | 点击安装即成功 | 原版要刷分区 + **重启才生效** | 演示版点击即成功、不重启。这是 demo 的必然妥协，建议在 FlashScreen 日志里如实演出"重启"字样然后 wink 一下回到工作中 |
| 4 | root 授权保存到软件目录 | 原版授权记录在**内核**里（ioctl），manager 根本不弹授权框（弹窗是 ksud/内核侧行为） | 演示版要**自己造**授权弹窗 + 本地 profiles.json。第三方 App 调 `su` 无法被演示版感知，只能在 App 内做"模拟请求" |
| 5 | 模块存到自身目录而非 `/data/adb/ksu` | 模块实际在 `/data/adb/modules/`，`/data/adb/ksu` 放的是 ksud 和配置 | 按 4.4 的 `filesDir/adb/{ksu,modules}` 布局重定向，语义对齐 |
| 6 | （未提及）卸载/重装 | 原版状态在 `/data/adb/`，卸载 App 不丢；演示版 flag 在私有目录，**卸载或"清除数据"后回到未安装** | 演示项目可接受；若想重装保留，只能写外部共享存储（需 MANAGE_EXTERNAL_STORAGE，用户可见，不推荐） |

---

## 六、缺陷与风险清单

### P0 阻塞级（不处理会崩）

| 缺陷 | 说明 | 处理 |
|---|---|---|
| 版本号依赖 git | `getVersionCode()` 直接 exec git 命令，非 git 目录构建抛异常 | 必须 git 仓库（4.0） |
| `System.loadLibrary` | native 库缺失时碰 `Natives` 就 `UnsatisfiedLinkError` | 整体重写为纯 Kotlin（4.2-1） |
| `libksud.so` 不存在 | `getKsuDaemonPath()` 指向 nativeLibraryDir，所有 `execKsud` 调用点会失败 | 清除全部调用点（4.2-4） |
| magica/RootService 残留 | Manifest 引用了 `zygotePreloadName`、AIDL Service，删代码不删 Manifest 会启动崩 | 同步清理（4.2-6） |

### P1 可控（功能妥协，演示无碍）

| 缺陷 | 影响 | 缓解 |
|---|---|---|
| 无真实 root | 模块的 action 脚本、WebUI 执行不了 | 模拟输出/Toast |
| 无 ksud 开机钩子 | 模块的 `service.sh`/`post-fs-data.sh` 永不执行，"开机自启"只是文案 | 关于页声明 |
| 第三方 App 调 su 无感知 | 只能在 App 内模拟授权请求 | 内置"模拟 su 请求"入口 |
| 重启/软重启按钮 | 真机不会重启（也别真重启） | Toast 提示 |
| "检查更新" | 会检测到真实 KernelSU 新版本，露馅 | 默认关闭/改地址 |
| 卸载语义变化 | 见修正点 6 | 关于页声明 |

### P2 可接受（甚至有趣）

- `allowBackup=true`：adb backup/换机迁移会把"已安装"flag 带到新设备——演示无碍；
- SELinux/seccomp 状态显示的是**真机真实值**（`Os.prctl` 等），反而更逼真；
- 签名用 debug key 自签即可；
- 多用户/分身下 SharedPreferences 天然隔离，符合预期。

---

## 七、里程碑与验收标准

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0 跑起来 | clone/git init、改包名、编译安装 | 桌面出现 FakeKernelSU 图标，能打开 |
| M1 数据层本地化 | Natives/KsuCli/RootService/magica 全拆完 | 编译通过，无 native 依赖；首页显示「未安装」 |
| M2 安装流程 | 直接安装 → 合成日志 → flag | 杀进程重进显示「工作中」 |
| M3 超级用户 | PackageManager 列表 + 本地授权 + 模拟 su 弹窗 | 授权/拒绝/ remembered，重进保留 |
| M4 模块 | zip 安装/启停/卸载 + Repo 浏览 | 装个真模块 zip 能列表显示并启停 |
| M5 设置与细节 | 卸载流程、重启 toast、关于页声明、中文默认 | 设置页无"露馅"入口 |
| M6 打磨 | 图标/名称/关于页、整体走查 | 可以拿去演示 |

---

## 八、待你拍板的决策点

1. **基线**：官方 main（工具链新，推荐）还是旧 tag（JDK17 能编，UI 老）？
2. **安装过程**：要不要在 FlashScreen 演一段假日志（更逼真，工作量 +0.5 天）？还是点按钮直接成功？
3. **模拟 su 请求**：做一个 FAB 触发的高仿授权弹窗（推荐，演示效果最好）？
4. **卸载语义**：临时卸载=清 flag 回到未安装 UI（推荐，可重复演示）；永久卸载=连本地数据一起清？

---

## 九、关键代码位置索引（改造时对照）

| 文件 | 作用 | 动作 |
|---|---|---|
| `manager/app/build.gradle.kts` | applicationId/`KSU_PACKAGE_NAME`/native 构建 | 改参数、删 externalNativeBuild |
| `Natives.kt` | JNI 内核通信 | **重写为纯 Kotlin** |
| `cpp/`（jni.cc/ksu.cc/adbroot.cc） | libkernelsu.so 源码 | 删除 |
| `ui/util/KsuCli.kt` | ksud 调用、模块列表、刷机 | **重写** |
| `ui/KsuService.kt` | AIDL RootService | 删除 |
| `ui/viewmodel/HomeViewModel.kt` | 首页状态 | isManager→flag |
| `ui/screen/home/HomeMaterial.kt` | 未安装/工作中卡片 | 改 notInstalled 判定 |
| `ui/screen/install/InstallScreen.kt` | 安装选项 | 只留直接安装 |
| `ui/screen/flash/FlashUtils.kt` | 刷机执行体 | 合成假日志 |
| `data/repository/SuperUserRepositoryImpl.kt` | 应用列表 | PackageManager |
| `data/repository/ModuleRepositoryImpl.kt` | 模块列表 | 解析本地 JSON（格式不变） |
| `data/registry/*` | 模块仓库（HTTP） | 保留 |
| `magica/` | 越狱基础设施 | 删除 |
| `AndroidManifest.xml` | zygotePreloadName 等 | 同步清理 |
| `manager/build.gradle.kts` | 版本号 git 依赖 | 保持 git 仓库 |
