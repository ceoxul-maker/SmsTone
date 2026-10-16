# 隐私与权限说明

适用版本：SmsTone 0.3.1。

## 数据处理

- 应用收到 Android 新短信广播后，只调用来源号码接口用于本地规则匹配。代码不调用正文读取接口，不提取、展示、保存或上传短信正文，不读取历史短信。
- 接收短信的系统权限本身会让应用收到包含短信数据的新短信事件；这里描述的是本应用代码的实际使用边界，而非声称系统只向应用提供号码。
- 本地保存号码组、声音选择、导入音频、提醒选项、总开关状态、音量恢复记录，以及最近一次匹配时间与结果。最近匹配信息不包含短信正文。
- 自选音频复制到应用内部，移动原文件不影响提醒。音频列表来自系统媒体库；未列出的文件可通过系统文件选择器导入。
- 不申请联网权限，没有广告、分析、联系人读取、录音、通知读取、历史短信读取、发送短信或全盘文件管理功能。禁用应用云备份。
- 查看短信时打开系统默认短信应用；后者的数据处理由其自身负责。

## 权限说明

| 权限 | 实际用途 |
| --- | --- |
| RECEIVE_SMS | 接收新普通 SMS 事件，按来源号码匹配 |
| POST_NOTIFICATIONS | 展示普通提醒及持续强提醒的停止入口 |
| READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE（最高 Android 12） | 展示系统音频库；可拒绝，改用单文件选择 |
| SYSTEM_ALERT_WINDOW（可选） | 强制提醒期间显示可直接点击的停止/打开短信悬浮按钮，结束后移除；不读取屏幕内容 |
| CAMERA | 可选闪光，仅调用 torch 接口，不启动拍照或录像 |
| FOREGROUND_SERVICE / FOREGROUND_SERVICE_MEDIA_PLAYBACK | 持续运行闹钟式音频提醒 |
| MODIFY_AUDIO_SETTINGS | 强提醒时提高闹钟音量，结束后恢复 |
| VIBRATE / WAKE_LOCK | 可选持续振动及提醒期间维持运行 |
| USE_FULL_SCREEN_INTENT | 用户授权后的锁屏全屏提醒 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 由用户选择允许后台运行 |
| RECEIVE_BOOT_COMPLETED | 重启解锁后恢复之前因异常退出未恢复的音量，不重新响铃 |

可在手机系统设置中撤销权限。短信权限撤销后无法匹配新短信；通知被关闭时本工具不启动强提醒，避免缺少可见停止入口；音频库权限拒绝后仍可取消或选取单个文件；相机权限拒绝后无法闪光。未允许悬浮窗时无法显示悬浮控制，可展开通知或打开应用首页停止；全屏提醒权限与悬浮权限相互独立。

## 删除数据

删除规则会清除对应规则，并清理不再被其他规则使用的导入音频。卸载应用或清除应用数据，会删除本工具保存的规则和音频；不会删除默认短信应用中的短信。卸载前请先停止正在播放的强提醒，以便恢复原闹钟音量。

## 可核查位置

- 权限：`app/src/main/AndroidManifest.xml`
- 短信来源匹配：`SmsReceiver.java`、`GroupNumbers.java`、`NumberMatcher.java`
- 本地规则：`RuleStore.java`
- 音频导入与提供：`SoundFiles.java`、`SoundProvider.java`
- 悬浮控制：`AlarmControls.java`
- 提醒与音量恢复：`AlarmService.java`、`AlarmActionReceiver.java`
- 默认短信跳转：`SmsApps.java`

上述 Java 文件位于 `app/src/main/java/cn/local/smstone/`。
