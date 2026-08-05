# 家庭图书馆

单机单用户的家庭图书管理 Android 应用，数据全部存储在本地，核心功能无需联网。

**当前版本**：1.1.1（`versionCode` 3）

## 安装 / 更新 APK

| 情况 | 做法 |
|------|------|
| 设置页显示 **1.0.0** 或 build **1** | 说明仍是旧包 → 请安装最新 CI 产物 |
| 提示「已安装相同版本」/ 版本冲突 | ① 确认装的是最新 APK；② **先卸载**旧版再装（仅首次换签名时需要） |
| 以后更新 | 仓库已固定 debug 签名，**versionCode 递增后即可直接覆盖安装** |
| debug 包名 | `com.familylibrary.app.debug` |

每次发新版会递增 `app/build.gradle.kts` 里的 `versionCode`；设置页会显示 `v1.1.1 (build 3)` 便于核对。

## 功能

| 模块 | 说明 |
|------|------|
| 书架管理 | 多书架多排；**默认书脊模式**（竖排书名），可切换封面网格 |
| 图书录入 | 单本/批量/扫码；**书名同步拉取**，作者与封面后台补全 |
| 扫码整理 | 扫 ISBN **仅查本地库**并立即移动，无需网络 |
| 图书查找 | 搜索 + 相似推荐 + 移动/归档 |
| 分类浏览 | 作者、系列、年龄、分类、蓝思值 |
| 阅读记录 | 家庭成员阅读统计 |
| 待购书单 | 手动添加 + **ISBN 扫码**（书店/图书馆场景） |
| 数据备份 | ZIP（含封面图片）导出/导入 |
| 权限控制 | 管理员 PIN 解锁写操作 |

详细需求说明见 [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md)。

## 技术栈

- Kotlin + Jetpack Compose + Material 3
- Room v4 + DataStore
- CameraX + ML Kit（ISBN 扫码）
- Coil 封面加载
- 网络仅用于：书名/元数据/封面拉取

## 构建与测试

### 本地（需 JDK 17 + Android SDK）

```bash
cd family-library-app
./gradlew assembleDebug
./gradlew test
```

### GitHub Actions（无需本地 Java）

1. 在 [github.com/new](https://github.com/new) 创建空仓库 `family-library-app`（Public，不要勾选 README）
2. 将 GitHub Token 存到 `C:\Users\shujiewe\.github_token`（classic PAT，勾选 `repo`）
3. 在项目目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\github-upload-and-build.ps1
```

脚本会：上传代码 → 跑单元测试 → 构建 debug APK → 下载到 `family-library-debug.apk`。

也可在仓库 **Actions → Android CI → Run workflow** 手动触发；完成后在 Artifacts 下载 `family-library-debug-apk`。

## 首次使用

1. 设置 → 管理员 → PIN **1234**
2. 书架选好排 → 扫码录入（需联网查书名）
3. 修改 PIN 并导出备份

## 快捷操作

### 扫码录入（添加新书）
1. 选好目标书架/排 → 点「扫码录入」
2. 连续扫 ISBN；**每本同步查询书名**（可继续扫下一本）
3. 未查到书名的项需手动编辑
4. 「完成录入」— 仅保存有书名的图书；作者/封面后台补全

### 扫码整理（移动已有书）
1. 选好**目标**书架/排 → 点「扫码整理」
2. 扫 ISBN → **本地查找** → 立即移动（不访问网络）

### 待购扫码
1. 待购页 → 扫码 FAB
2. 书店看到想买的书，扫 ISBN 即加入待购

### 书架显示
- 默认**书脊模式**（竖排书名，横向排列）；可切换封面网格
- **记住上次书架/排**，打开即定位
- 空排显示**扫码录入**主按钮；扫码录入会检测**已在库**重复

### 扫码整理
- 返回时弹出**本次整理汇总**（移入 / 已在目标位 / 未入库）

## 封面异常

| 场景 | 处理 |
|------|------|
| 封面拉取失败 | 红色占位；详情页可重试/相册/拍照 |
| 无 ISBN | 不自动拉取；管理员可上传 |
| 自定义封面 | 不被 ISBN 变更覆盖 |

## 数据备份

ZIP 包含 `manifest.json`、`data.json`、`covers/*.jpg`。导入时校验数据库版本。

## 最低要求

- Android 7.0 (API 24)+
- 竖屏
