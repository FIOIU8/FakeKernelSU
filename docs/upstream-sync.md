# 上游同步流程（FakeKernelSU ↔ tiann/KernelSU）

FakeKernelSU 是 [tiann/KernelSU](https://github.com/tiann/KernelSU) 的 `manager/` 模块的 fork。
本仓库的仓库根 **就是** 上游的 `manager/` 子树，路径 1:1 对应：

```
上游   tiann/KernelSU/manager/app/src/main/java/me/weishu/kernelsu/...
本仓库 app/src/main/java/me/weishu/kernelsu/...
```

因此把上游 `manager/` 的新提交并进来，冲突会落在**同名同路径的文件**上。本文件描述如何
把这种冲突压到最小，以及配套工具怎么用。

---

## 1. 核心设计：共享文件只放上游的东西

| 位置 | 内容 | 上游会不会冲突 |
|---|---|---|
| `app/src/main/res/values*/strings.xml` | **只有上游的资源，逐字节一致** | 不会 |
| `app/src/main/res/values*/fake_strings.xml` | fork 自己的资源（`fake_*`）和对上游资源补充的翻译 | 不会 |
| `app/src/main/java/me/weishu/kernelsu/fake/` | fork 的假数据层 | 不会 |
| `app/src/main/java/me/weishu/kernelsu/ui/screen/fakepanel/` | fork 的实验面板 | 不会 |
| 其余上游文件 | 尽量不改；改了必须在 allowlist 里登记 | 会，但可见 |

### 1.1 为什么 `strings.xml` 要和上游逐字节一致

上游 47 个 locale 文件里，有 45 个保持**各自的字符串顺序**，并不跟随默认 locale
（`values/strings.xml`）的顺序。也就是说上游每次同步翻译，都是**追加到各 locale 文件末尾**。

早期 fork 把自己的 88~90 条字符串直接追加在 `strings.xml` 末尾，于是：

> 上游每补一条翻译 → fork 那个文件末尾就撞车 → **45 个 locale 文件同时冲突**。

所以 fork 往 `strings.xml` 里加的任何一行，都是一次定时炸弹。解法是让
`strings.xml` 与上游完全一致，fork 的一切都搬进 `fake_strings.xml`——上游根本没有这个文件，
永远不可能冲突。

代价是：如果上游日后把某条资源翻译进了某个 locale，而 fork 在 `fake_strings.xml` 里也有同名
资源，aapt2 会报 duplicate resource 直接构建失败。这个风险由
`tools/verify-divergence.ps1` 的规则 5 提前拦住（见 §3）。

### 1.2 为什么 fork 的资源名要加 `fake_` 前缀

fork 原本有 6 条资源没带前缀：`launcher_label_{fake,ksu,manager,short}` 和
`home_edit_{summary,hint}`。它们放在 fork 专属文件里，本身不会冲突；但如果上游日后用了
**完全相同的名字**，就会变成同一个 configuration 下的重复资源 → 构建失败（比 git 冲突更难查）。

统一改成 `fake_launcher_label_*` / `fake_home_edit_*` 之后，规则变成机械可校验的：

> `fake_strings.xml` 里只允许两类名字：
>
> 1. **fork 自有**：`fake_` 开头，且上游任何 locale 都没有同名资源；
> 2. **替上游补翻译**：名字与上游默认 locale（`values/strings.xml`）里的某条**完全一致**，
>    且当前 locale 的 `strings.xml` 里**还没有**它。
>
> 第 2 类是允许的，因为 fork 顺手补齐了上游缺失的翻译（如 `script_*`、`module_preinit_*`）；
> 它同时也是定时炸弹——上游哪天把这条翻进同一 locale，这里就重复了。所以守卫规则 5
> 会在上游提供该翻译时立刻报错，要求删掉 fork 那份（见 §3.2）。

除这两类之外的名字（既没 `fake_` 前缀、上游也没有）会被规则 8 直接判红。
**新增 fork 字符串时永远用第 1 类。**

---

## 2. 工具

全部在 `tools/` 下，PowerShell 脚本，Windows / Linux 均可跑（CI 用 `pwsh`）。

| 脚本 | 作用 |
|---|---|
| `tools/verify-divergence.ps1` | 守卫。检查 allowlist 覆盖、资源重复、翻译完整性、与上游的同步漂移 |
| `tools/sync-upstream.ps1` | 同步。把上游没被 fork 动过的文件直接覆盖进来 |
| `tools/split-fake-strings.ps1` | 拆分/重建 `strings.xml` ↔ `fake_strings.xml` |
| `tools/rename-fork-resources.ps1` | 给 fork 自有资源名加 `fake_` 前缀 |
| `tools/apply-translations.ps1` | 把 `<locale>\|<name>\|<text>` 形式的翻译表批量写进各 locale 的 `fake_strings.xml` |
| `tools/report-missing-translations.ps1` | 列出哪些 `fake_*` 在哪些 locale 里还缺翻译（手动排查用） |
| `tools/upstream/allowlist.txt` | fork 允许偏离上游的文件清单（每条都写了原因） |
| `tools/upstream/upstream-ref.txt` | 记录当前对齐的上游基准 |

### 2.1 守卫

```powershell
# 用仓库里记录的基准（fork 的导入提交）检查
./tools/verify-divergence.ps1

# 跟上游最新提交比，看漂移
./tools/verify-divergence.ps1 -UpstreamRef refs/remotes/upstream/main

# 漂移也当作失败（CI 的每周巡检用这个）
./tools/verify-divergence.ps1 -UpstreamRef refs/remotes/upstream/main -SyncRequired
```

规则分两类：

**严格规则（永远失败）**

1. fork 改动/删除的上游文件，必须在 `allowlist.txt` 里登记。
2. `res/values*/strings.xml` 与上游逐字节一致（不一致 = fork 落后了）。
3. 同一个 configuration 里没有重复资源名。
4. `fake_strings.xml` 里 `fake_` 开头的名字，上游不能有。
5. `fake_strings.xml` 里的名字，上游不能在同一 locale 里也提供（否则 aapt2 重复资源）。
6. `values/fake_strings.xml` 里每一条，都必须在每个 `values-<locale>/fake_strings.xml` 里出现，
   且 `%1$s` / `%1$d` 这类占位符保持一致。缺一条就退回英文，45 种语言里没人会发现。
7. `upstream-ref.txt` 里记的 `lineage` 提交必须存在、有两个父、且仍在 `HEAD` 的祖先链上。
   丢了它，`git merge -X subtree=manager` 会悄悄退回文件级覆盖（正是嫁接前的处境）。
   这条不需要网络，纯本地检查。
8. `fake_strings.xml` 里的每个名字，要么是 `fake_` 前缀的 fork 自有资源（上游任何 locale 都不能有），
   要么是「上游默认 locale 有、而本 locale 还没有」的上游翻译补充（见 §1.2）。
9. 有 `strings.xml` 的 locale 必须有 `fake_strings.xml`。`sync-upstream.ps1` 随时可能带进
   一个上游新 locale，缺这个文件就意味着那个语言下 fork 的字符串全部退回英文。

**同步漂移（默认警告，`-SyncRequired` 时失败）**

上游改了但 fork 还没跟上的文件、上游新增/删除的文件。

### 2.2 同步

```powershell
# 先看会发生什么
./tools/sync-upstream.ps1 -UpstreamRef refs/remotes/upstream/main

# 确认后再写入
./tools/sync-upstream.ps1 -UpstreamRef refs/remotes/upstream/main -Apply
```

同步脚本会：

- 上游改了、**fork 没动过**的文件 → 直接覆盖成上游版本；
- 上游新增的文件 → 复制进来；
- 上游删掉的文件 → 删掉；
- **上游和 fork 都改过的文件** → 不动，列成 `MANUAL`，需要人工合并；
- 上游又把 `app/src/main/{cpp,aidl,proto,jniLibs}` 带回来 → 整个目录删掉并打印，
  这三个目录里有 fork 新增文件时也不会漏（整目录删，比逐个文件登记可靠）。

`MANUAL` 列表就是 `allowlist.txt` 里登记过的那批文件（上游若也改了它们）。

**为什么没有 `patches/*.patch`**：`gradle.properties`、`settings.gradle.kts`、
`gradle/libs.versions.toml`、`build.gradle.kts`、`app/build.gradle.kts` 全都在 allowlist 里，
同步脚本根本不会覆盖它们，所以同步后不需要重放任何补丁。万一这些文件在合并时被上游版本
顶掉了，`app/build.gradle.kts` 里的 `externalNativeBuild` 会让脚本直接报错退出，
不会悄悄把原生构建带回来。

---

## 3. 一次完整的上游同步

### 3.0 前提：血缘已经打通（2026-10-22）

导入提交 `1f70e46` 是上游 `manager/` 的 squash 快照，所以它和 `tiann/KernelSU` **没有共同祖先**，
`git merge-base main upstream/main` 曾经为空 —— 没有 3 路合并基线，只能逐个文件覆盖。

现在已经嫁接：

| 角色 | 提交 | 说明 |
|---|---|---|
| 导入源 | `4663610` | 上游 2026-10-20 "manager: remove DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION (#3927)"。它与 `1f70e46` 只差 4 处导入副作用：`gradlew` 权限位、fork 的 `.gitignore`、被丢掉的 `app/src/main/cpp/uapi` 软链、fork 自己的方案文档 |
| 血缘提交 | `07c335d` | 一个树与父提交**完全相同**的合并提交，第二个父是 `4663610`。它只记录血缘，不改任何文件 |

验证过的事实（在临时 worktree 里做过探针）：

- `git merge-base main upstream/main` → `4663610`，不再是空；
- `git merge -X subtree=manager upstream/main` 能把上游 `manager/` 下的改动正确落到**仓库根**
  （探针往上游加 `manager/PROBE.txt`，合并后出现在根的 `PROBE.txt`）；
- 合并当前上游（`6d31a61`，只改 `website/`）**零冲突、零改动**。

### 3.1 首选：git 3 路合并

```bash
# 0. 代理（国内网络必需；只对这一条命令生效，不写进仓库配置）
export https_proxy=http://127.0.0.1:7897 http_proxy=http://127.0.0.1:7897

# 1. 拉上游（完整历史，不要 --depth，否则没有 merge base）
git fetch --no-tags upstream main

# 2. 看上游改了什么
git log --oneline 4663610..upstream/main -- manager

# 3. 合并：-X subtree=manager 把上游的 manager/ 对齐到本仓库根
git merge -X subtree=manager upstream/main

# 4. 冲突处理：allowlist 里登记过的文件（fork 改过的）大概率会冲突，
#    按"上游逻辑 + fork 的模拟实现"合并；没登记的文件应该自动合并
git status --short
git diff --name-only --diff-filter=U

# 5. 收尾：跑守卫 + 构建 + 单测
./tools/verify-divergence.ps1 -UpstreamRef upstream/main
./gradlew assembleDebug test
```

合并失败想放手：`git merge --abort`。整次同步想回退：`git reset --hard <合并前的 HEAD>`。

> 每个上游提交只碰 `manager/` 之外的目录时，第 3 步会是"空合并"（零改动、零冲突），
> 直接 `git commit` 记录即可，这是正常现象。

### 3.2 备选：文件级同步脚本

上游大改（挪目录、批量重命名）时，3 路合并会碎成一堆冲突，这时用脚本更省事：

```bash
# 1. 拉上游
git fetch --no-tags upstream main

# 2. 看漂移
./tools/verify-divergence.ps1 -UpstreamRef upstream/main

# 3. 自动同步 fork 没动过的文件
./tools/sync-upstream.ps1 -UpstreamRef upstream/main -Apply

# 4. 人工合并 MANUAL 列表里的文件
#    这些文件 fork 也改过：把上游的改动并进来，别丢掉 fork 自己的逻辑

# 5. 资源校验（上游可能新加了资源名，或补了某条翻译）
./tools/verify-divergence.ps1 -UpstreamRef upstream/main
```

脚本第 3 步之后会顺手删掉 `app/src/main/{cpp,aidl,proto,jniLibs}`（上游又会把它们带回来），
并在 `app/build.gradle.kts` 出现 `externalNativeBuild` 时直接报错退出。

第 5 步若报：

```
FAIL  values-de: upstream now provides 'script_run' - remove the fork's copy from values-de/fake_strings.xml
```

说明上游已经把 `script_run` 翻译进德语了。fork 在 `values-de/fake_strings.xml` 里的那份
重复了它，aapt2 会直接报 duplicate resource。**删掉 fork 那份**即可（上游的翻译质量不差，
而且以后上游再改也会自动跟上）。

若报：

```
FAIL  values-de: 'fake_xxx' uses the fake_ prefix but upstream also defines it
```

说明上游用了个一模一样的名字。给 fork 的资源换个名字即可。

---

## 4. 给 fork 加新功能时的规矩

1. **新文件放 fork 专属目录**：`me/weishu/kernelsu/fake/`、
   `me/weishu/kernelsu/ui/screen/fakepanel/`。别往上游的包里塞新文件。
2. **新字符串用 `fake_` 前缀**，放进 `res/values*/fake_strings.xml`。
   不要动 `strings.xml`。加完英文后**必须补齐全部 46 个 locale**，否则守卫规则 6 会红：

   ```powershell
   # 1) 写一份翻译表（UTF-8，一行一条，用 | 分隔）
   #    values-ja|fake_panel_foo|ラベルの文字
   # 2) 批量写入各 locale 的 fake_strings.xml（已存在的条目会跳过，可重复跑）
   ./tools/apply-translations.ps1 -DataFile translations.tsv
   # 3) 补齐后清理临时表，别提交进仓库
   ```

   漏了哪几条，用 `./tools/report-missing-translations.ps1` 查。
3. **要改上游文件**，先想想能不能用 fork 专属文件绕开。绕不开就：
   - 改动尽量小；
   - 在 `tools/upstream/allowlist.txt` 里登记，并写清原因；
   - 如果这个改动上游也该接受，提个上游 PR，在原因里写上 PR 号。
4. 改完跑 `./tools/verify-divergence.ps1`，必须绿。

---

## 5. CI 里怎么用

| Job | 触发 | 作用 |
|---|---|---|
| `upstream-divergence` | 每次 push / PR | 跑严格规则 1-9（含 lineage 完整性与 locale 覆盖），改坏了就红 |
| `upstream-sync-check` | 每周一 + 手动 | 拉上游**完整历史**，看漂移，漂移就红；另外用 `git merge --no-commit -X subtree=manager` 预演一次合并，有冲突只打 warning（冲突多半落在 allowlist 文件上，属预期） |
| `build` | 每次 push / PR | 构建 debug/release APK、跑单测、检查 APK 里没有 `.so` |

---

## 6. 已知的残余风险

| 风险 | 影响 | 谁来兜 |
|---|---|---|
| **血缘是嫁接出来的**，不是原始 parent：`07c335d` 的第二个父是手工指定的 `4663610` | 如果上游 rebase / force-push 改写了 `4663610` 之后的历史，这个基线就失效了，需要重新找导入源并重建嫁接提交 | `tools/upstream/upstream-ref.txt` 记了 `baseline` / `upstream` / `lineage` 三条；重建步骤见 §3.0 |
| 上游又把 `app/src/main/{cpp,aidl,proto,jniLibs}` 带回来 | 3 路合并会把原生目录合进仓库，破坏"无 native"承诺 | 守卫不查这个；`sync-upstream.ps1` 会 strip 并检查 `externalNativeBuild`，用 git 合并时要手动看一眼 |
| 上游给某 locale 补了翻译，和 fork 的 `fake_strings.xml` 撞名 | aapt2 duplicate resource，构建失败 | 守卫规则 5 在构建前拦住 |
| 上游新增/改名了资源，fork 代码引用不到 | 编译失败 | 同步脚本把上游文件带进来后由编译器报出 |
| 上游改了 fork 也改过的文件 | 人工合并 | `sync-upstream.ps1` 的 `MANUAL` 列表 |
| fork 新增的文件路径被上游日后占用 | 合并冲突 | 规则：fork 文件一律放 fork 专属目录 |
