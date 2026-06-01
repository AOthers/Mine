# 我的

一个基于 Android Kotlin 和 Jetpack Compose 开发的工具箱应用。目前第一个完整功能是「备份与恢复」，用于提取手机应用安装包、备份到百度网盘，并在需要时下载恢复。

## 功能特点

- 底部三栏：首页「功能」、常用入口「收藏」、个人页「我的」。
- 功能页目前提供「备份与恢复」工具。
- 长按功能卡片可以收藏或取消收藏，收藏后的工具会显示在收藏页。
- 备份功能可以读取手机已安装应用，选择应用后提取 APK。
- 备份文件会上传到百度网盘，文件名包含应用名、包名和版本号。
- 同一个应用的不同版本会分别保存。
- 相同版本再次备份时，可在设置中选择「覆盖」或「另存一份」。
- 备份和恢复过程支持通知栏进度显示。
- 恢复页面会按应用聚合已备份的安装包，并列出不同版本。
- 恢复支持两种方式：
  - 手动填写下载链接，并跳转浏览器下载。
  - 从百度网盘下载 APK，并调用系统安装器安装。
- 恢复页面支持按应用名、包名或版本号搜索。
- 恢复页面右上角提供「已下载安装包」管理入口，可以查看、安装或删除已下载 APK。
- 已下载安装包会显示应用图标、应用名、包名、版本、大小、时间和路径。
- 恢复安装包默认保存到应用外部文件目录，也可以通过系统文件夹选择器自定义保存位置。
- 百度网盘 API 配置放在「备份与恢复」的设置页，并提供获取 API 密钥的教程。

## 截图

后续可以在这里补充应用截图。

## 构建环境

- Android Studio
- JDK 17
- Android Gradle Plugin
- Kotlin
- Jetpack Compose

项目中的 `gradle.properties` 已指定 JDK 17 路径：

```properties
org.gradle.java.home=C:/Program Files/Java/jdk-17
```

如果你的本机 JDK 路径不同，需要改成自己的路径，或者删除这一行并使用 Android Studio 配置的 JDK。

## 调试包

运行：

```powershell
.\gradlew.bat assembleDebug
```

生成位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 发布包

项目支持 release 签名配置，但签名文件不会提交到仓库。

本地需要准备：

```text
mine-release.jks
keystore.properties
```

`keystore.properties` 示例：

```properties
storeFile=mine-release.jks
storePassword=你的密钥库密码
keyAlias=mine
keyPassword=你的密钥密码
```

然后运行：

```powershell
.\gradlew.bat assembleRelease
```

生成位置：

```text
app/build/outputs/apk/release/app-release.apk
```

## 百度网盘配置

使用百度网盘备份和恢复前，需要先在百度网盘开放平台创建应用，并获取：

- AppKey
- SecretKey

OAuth 回调地址填写：

```text
wode://baidu.oauth
```

在应用中进入「备份与恢复」设置页，填写 AppKey 和 SecretKey 后点击保存并授权。授权成功后会自动回到「备份与恢复」页面。

默认网盘备份目录：

```text
/apps/AppBackup
```

这个路径可以在设置页中自定义。

## 代码结构

- `MainActivity.kt`：页面协调、OAuth 回调、文件夹选择、APK 安装、通知权限和系统返回处理。
- `ToolboxScreens.kt`：工具箱首页、底部导航、收藏逻辑和备份恢复入口。
- `AppListScreen.kt`：备份时的已安装应用列表。
- `BackupScreen.kt`：备份进度页面。
- `RestoreScreen.kt`：恢复列表、搜索、版本选择、手动链接和已下载 APK 管理。
- `SettingsScreen.kt`：百度网盘配置、授权、网盘路径、恢复保存位置、同版本处理策略和教程。
- `BackupViewModel.kt`：备份、上传、恢复、下载、本地 APK 管理和进度通知。
- `BaiduPanService.kt`：百度网盘 OAuth、上传、列表、文件信息和下载接口。
- `TokenStore.kt`：加密保存 Token、API 配置、备份路径、恢复路径和备份策略。
- `FavoriteStore.kt`：本地收藏状态。
- `RestoreLinkStore.kt`：按包名保存手动恢复链接。
- `ProgressNotifier.kt`：通知栏进度。
- `RestoreTarget.kt`：普通文件路径和 SAF 文件夹输出的统一封装。

## 后续计划

- 手动链接恢复支持应用内下载。
- 增加 Wi-Fi 下自动备份或批量备份策略。
- 收藏页支持排序。
- 工具箱继续增加更多实用工具。

## 开源协议

暂未指定协议。发布正式开源版本前，建议补充 `LICENSE` 文件。
