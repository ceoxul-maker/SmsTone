# 构建与验证

## 环境

- Windows PowerShell 7、JDK 17。
- 官方 Android SDK platform API 35 与 build-tools 35.0.0。
- 构建脚本不需要 Android Studio、Gradle 或第三方依赖。

脚本会在以下目录下递归查找工具，`ToolRoot` 内应保留这两个目录名：

```text
Android-build-tools/
  platforms-android-35/ ... android.jar
  build-tools-35.0.0/ ... aapt2.exe, zipalign.exe, lib/d8.jar, lib/apksigner.jar
```

在仓库根目录执行，按自己的实际安装位置替换路径：

```powershell
.\build.ps1 -ToolRoot 'C:\Android-build-tools' -JavaHome 'C:\Java\jdk-17'
```

不传参数时使用原开发机的默认路径。原生 Android 工具不能可靠处理中文参数路径，因此脚本把构建过程暂存到 ASCII 临时目录，`TEMP` 也需是 ASCII 路径。产物复制回 `dist/`。

## 产物和签名

- `dist/SmsTone-0.3.0.apk`
- `dist/SHA256-0.3.0.txt`

首次构建会在本机 `signing/` 生成随机密码和 PKCS12 签名密钥，后续构建复用。签名密钥、密码、构建缓存和测试日志均被 Git 排除，不属于源码发布内容。请自行妥善备份密钥，勿上传 GitHub。

自己构建的签名与本仓库 Release 安装包不同，不能覆盖安装官方发布包。需要同一签名才能保留数据覆盖升级。

包名 `cn.local.smstone`，versionName `0.3.0`，versionCode `3`，最低 API 26，compile API 35，target API 34。

当前侧载测试包使用 target 34。在本次 Android 15 测试镜像中，target 35 的相同 mediaPlayback 前台服务实现即使已经运行，音频焦点请求仍遭拒绝；target 34 已验证成功取得临时焦点并循环播放。这不是普遍兼容性结论，也不代表满足应用商店上架政策。手机端实现未使用隐藏 API、root 或系统签名权限。

## 纯 Java 测试

在仓库根目录执行，用自己的 JDK 17 路径替换示例：

```powershell
$javaBin = 'C:\Java\jdk-17\bin'
New-Item -ItemType Directory -Path build\test-classes -Force | Out-Null
$sourceRoot = 'app\src\main\java\cn\local\smstone'
& "$javaBin\javac.exe" -encoding UTF-8 --release 8 -d build\test-classes "$sourceRoot\NumberMatcher.java" "$sourceRoot\GroupNumbers.java" "$sourceRoot\AudioFilters.java" tests\NumberMatcherTest.java tests\GroupNumbersTest.java tests\AudioFiltersTest.java
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
foreach ($test in 'NumberMatcherTest','GroupNumbersTest','AudioFiltersTest') {
    & "$javaBin\java.exe" -cp build\test-classes "cn.local.smstone.$test"
    if ($LASTEXITCODE -ne 0) { throw "$test failed" }
}
```

共 40 条断言：完整号码 14 条，多号码分组 12 条，音频类型及本周时间边界 14 条。测试号码为样例，不是用户短信或联系人数据。

`tests/AndroidSmokeTools.ps1` 是原开发机的模拟器辅助函数，运行前需按自己的环境调整 ADB 路径和设备序列号；它不是无人值守的完整端到端测试套件。

## 0.3.0 已验证范围

Android 15 AOSP x86_64 模拟器：

- 同签名覆盖升级保留 6 组规则、音频与提醒选项，第 7 组无法保存。
- 首页独立编辑/试听，编辑页固定保存操作；保存后试听重新应用完整设置。
- 系统静音时强提醒将闹钟音量从 2 提升至最大 7，播放器以 ALARM 用途运行且未静音；振动服务记录循环波形，手电筒按约 600 ms 开关。
- 同组正在提醒时再次试听，会重启并应用修改后的振动、闪光设置；停止后恢复原音量。
- 后台 SMS 触发、不同强提醒组替换、持续通知停止、锁屏全屏提醒、异常退出后重新打开恢复音量。
- 音频库拒绝权限也可取消；授权后列表、MP3 与本周交集筛选、MP3 导入成功；系统文件浏览可返回而不更改原音频。
- 普通试听跟随静音和通知音量，不改变闹钟音量。
- 点击普通提醒通知，以及停止试听后打开短信，都进入默认短信应用。
- APK v2/v3 签名及 ZIP 对齐检查通过。

尚未完成各品牌真机的后台保活、真实振幅、手电筒限制及运营商短信/验证码兼容验证。安装后应先试听，再用真实短信测试。
