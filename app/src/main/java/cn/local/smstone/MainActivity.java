package cn.local.smstone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler();
    private boolean lastRunning;
    private final Runnable alarmRefresh = new Runnable() {
        @Override public void run() { if (lastRunning != AlarmService.running) render(); handler.postDelayed(this, 800); }
    };
    @Override public void onCreate(Bundle state) { super.onCreate(state); }
    @Override public void onResume() {
        super.onResume(); AlarmService.restorePendingVolume(this); render(); handler.post(alarmRefresh);
        if (getIntent().getBooleanExtra("forceSetup", false)) { getIntent().removeExtra("forceSetup"); forceSettings(); }
    }
    @Override public void onPause() { handler.removeCallbacks(alarmRefresh); super.onPause(); }
    private boolean smsAllowed() { return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED; }
    private void render() {
        RuleStore store = new RuleStore(this);
        lastRunning = AlarmService.running;
        LinearLayout root = Ui.screen(this);
        Ui.add(root, Ui.text(this, "SMS TONE  /  本地工具", 11, Ui.GREEN, true), 0);
        Ui.add(root, Ui.text(this, "短信专属铃声", 29, Ui.INK, true), 12);
        Ui.add(root, Ui.text(this, "熟悉的短信应用，听得出的重要消息。", 14, Ui.MUTED, false), 6);

        LinearLayout status = Ui.card(this);
        LinearLayout statusRow = Ui.row(this);
        Ui.weight(statusRow, Ui.text(this, "启用专属提醒", 17, Ui.INK, true));
        Switch master = new Switch(this); master.setContentDescription("启用专属提醒"); master.setChecked(store.enabled()); statusRow.addView(master);
        master.setOnCheckedChangeListener((button, checked) -> {
            store.setEnabled(checked);
            if (!checked) stopService(new Intent(this, AlarmService.class));
        });
        Ui.add(status, statusRow, 0);
        boolean ready = smsAllowed() && AlertNotifier.permissionGranted(this) && AlertNotifier.manager(this).areNotificationsEnabled();
        Ui.add(status, Ui.text(this, ready ? "权限已就绪 · 界面关闭后仍可接收短信广播" : "还差一步：允许接收短信与发送通知", 13, ready ? Ui.GREEN : Ui.MUTED, false), 8);
        if (!ready) Ui.add(status, Ui.button(this, "设置必要权限", true, v -> requestNeeded()), 14);
        Ui.add(root, status, 24);
        if (AlarmService.running) {
            Ui.add(root, Ui.text(this, "正在强制提醒：" + AlarmService.activeTitle, 18, Ui.GREEN, true), 18);
            Ui.add(root, Ui.button(this, "停止全部提醒", true, v -> { stopService(new Intent(this, AlarmService.class)); }), 12);
            Ui.add(root, Ui.button(this, "停止并打开短信", false, v -> SmsApps.openDefault(this)), 10);
        }

        List<Rule> rules = store.all();
        boolean hasForce = false;
        for (Rule rule : rules) if (rule.forceAlarm) { hasForce = true; break; }
        if (hasForce && !Settings.canDrawOverlays(this)) {
            LinearLayout controls = Ui.card(this);
            Ui.add(controls, Ui.text(this, "强制提醒：请允许悬浮停止按钮", 16, Ui.INK, true), 0);
            Ui.add(controls, Ui.text(this, "亮屏时系统可能不弹出提醒页。授权后，响铃时会显示“停止提醒”和“停止并打开短信”；只在强制提醒期间显示。", 13, Ui.MUTED, false), 8);
            Ui.add(controls, Ui.button(this, "设置悬浮停止按钮", true, v -> requestOverlay()), 12);
            Ui.add(root, controls, 16);
        }
        LinearLayout heading = Ui.row(this);
        Ui.weight(heading, Ui.text(this, "号码分组", 19, Ui.INK, true));
        heading.addView(Ui.text(this, rules.size() + " / 6 组", 13, Ui.MUTED, false));
        Ui.add(root, heading, 26);
        if (rules.isEmpty()) {
            LinearLayout empty = Ui.card(this);
            Ui.add(empty, Ui.text(this, "让重要的号码，拥有自己的声音", 17, Ui.INK, true), 0);
            Ui.add(empty, Ui.text(this, "最多 6 组，每组可填多个号码，并选择自己的声音。重要的组可开启闹钟式强制提醒。", 14, Ui.MUTED, false), 10);
            Ui.add(root, empty, 14);
        }
        for (Rule rule : rules) {
            LinearLayout card = Ui.card(this);
            LinearLayout row = Ui.row(this);
            LinearLayout info = Ui.column(this);
            Ui.add(info, Ui.text(this, rule.displayName(), 18, Ui.INK, true), 0);
            java.util.List<String> numbers = GroupNumbers.parse(rule.number);
            String summary = numbers.size() + " 个号码";
            if (!numbers.isEmpty()) summary += " · " + numbers.get(0) + (numbers.size() > 1 ? " 等" : "");
            Ui.add(info, Ui.text(this, summary, 13, Ui.MUTED, false), 3);
            Ui.weight(row, info);
            Switch enabled = new Switch(this); enabled.setContentDescription(rule.displayName() + "启用状态"); enabled.setChecked(rule.enabled); row.addView(enabled);
            enabled.setOnCheckedChangeListener((button, checked) -> {
                rule.enabled = checked;
                if (!store.save(rule)) Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_LONG).show();
                if (!checked && rule.id.equals(AlarmService.activeId)) stopService(new Intent(this, AlarmService.class));
            });
            Ui.add(card, row, 0);
            Ui.add(card, Ui.text(this, "提示音  ·  " + rule.soundName, 13, Ui.MUTED, false), 12);
            if (rule.forceAlarm) Ui.add(card, Ui.text(this, "强制提醒 · 循环响铃" + (rule.vibrate ? " · 最强振动" : "") + (rule.flash ? " · 闪光" : ""), 12, Ui.GREEN, true), 6);
            LinearLayout actions = Ui.row(this);
            Button editButton = Ui.button(this, "编辑", false, v -> edit(rule.id));
            editButton.setContentDescription("编辑：" + rule.displayName());
            Button previewButton = Ui.button(this, "试听", true, v -> PreviewController.play(this, rule.id));
            previewButton.setContentDescription("试听：" + rule.displayName());
            Ui.weight(actions, editButton);
            android.view.View gap = new android.view.View(this);
            actions.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
            Ui.weight(actions, previewButton);
            Ui.add(card, actions, 14);
            Ui.add(root, card, 12);
        }
        Ui.add(root, Ui.button(this, rules.size() >= GroupNumbers.MAX_GROUPS ? "已设置 6 组 · 可编辑现有组" : "＋ 添加号码组", true, v -> {
            if (rules.size() >= GroupNumbers.MAX_GROUPS) Toast.makeText(this, "最多设置 6 组，请编辑或删除现有组", Toast.LENGTH_LONG).show();
            else edit(null);
        }), 16);
        Ui.add(root, Ui.button(this, "强制提醒权限与后台设置", false, v -> forceSettings()), 12);
        LinearLayout guide = Ui.card(this);
        Ui.add(guide, Ui.text(this, "首次使用，避免响两次", 16, Ui.INK, true), 0);
        Ui.add(guide, Ui.text(this, "在原短信应用中，将这些号码对应会话的提示音设为静音。本工具负责播放专属声音。", 13, Ui.MUTED, false), 8);
        Ui.add(guide, Ui.button(this, "查看设置与使用说明", false, v -> showHelp()), 14);
        Ui.add(root, guide, 24);
        if (store.lastTime() > 0) {
            String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(store.lastTime()));
            Ui.add(root, Ui.text(this, "最近触发 · " + time + "\n" + store.lastResult(), 12, Ui.MUTED, false), 20);
        }
        Ui.add(root, Ui.text(this, "0.3.1 测试版 · 无联网权限 · 不保存短信正文", 11, Ui.MUTED, false), 24);
    }
    private void edit(String id) {
        Intent intent = new Intent(this, RuleEditorActivity.class);
        if (id != null) intent.putExtra("id", id);
        startActivity(intent);
    }
    private void requestNeeded() {
        List<String> needed = new ArrayList<>();
        if (!smsAllowed()) needed.add(Manifest.permission.RECEIVE_SMS);
        if (Build.VERSION.SDK_INT >= 33 && !AlertNotifier.permissionGranted(this)) needed.add(Manifest.permission.POST_NOTIFICATIONS);
        if (needed.isEmpty()) { openAppSettings(); return; }
        new AlertDialog.Builder(this).setTitle("两项权限，用于专属提醒")
            .setMessage("接收短信：只在新短信到来时读取发件号码进行匹配，不读取历史短信，不保存正文。\n\n发送通知：用于普通提醒，以及强制提醒的持续状态和停止按钮。\n\n若系统拒绝授予短信权限，请打开本应用的权限设置检查；某些手机或安装渠道可能不支持。")
            .setPositiveButton("继续", (dialog, which) -> requestPermissions(needed.toArray(new String[0]), 100))
            .setNegativeButton("暂不", null).show();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        render();
        if (!smsAllowed() || !AlertNotifier.permissionGranted(this)) {
            new AlertDialog.Builder(this).setTitle("权限尚未就绪")
                .setMessage("未获授权时，无法完成后台短信提醒。你可以在系统应用设置中检查“短信”和“通知”权限。")
                .setPositiveButton("打开应用设置", (d, w) -> openAppSettings()).setNegativeButton("稍后", null).show();
        }
    }
    private void openAppSettings() {
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        catch (Exception error) { Toast.makeText(this, "请手动打开系统设置 → 应用 → 短信专属铃声", Toast.LENGTH_LONG).show(); }
    }
    private void showHelp() {
        new AlertDialog.Builder(this).setTitle("让重要短信更容易被听见")
            .setMessage("你是否也曾因错过或忽略一条重要短信而懊恼？也许是一张本可避免的罚单，也许是某个重要的人留给你的消息。\n\n这款由 AI 编写、源码开放的小工具，帮助你为重要号码设置专属声音，并按需开启强制提醒，让重要短信更容易被注意到。\n\n隐私与短信内容\n工具只用发件号码匹配规则，不提取、展示、保存或上传短信正文，也不读取历史短信。短信内容仍由手机默认短信应用打开。工具没有联网权限，规则与导入音频只保存在本机。\n\n首次使用需要授权\n接收短信和通知权限用于触发提醒及显示停止按钮；列出本地音频需要音频访问权限，可不授权而改用文件浏览；闪光灯需要相机权限。你可在系统设置中随时撤销。\n\n1. 最多 6 组，每组每行一个完整号码，或用逗号分隔；组内外不要重复。\n\n2. 每组分别选择系统铃声或本地音频。本地音频页默认显示音频库，可按文件类型和本周筛选；系统铃声页顶部有返回和取消，底部有确定，只有点确定才会应用。取消或返回不改变原声音。\n\n3. 保存后，首页每组的“编辑”和“试听”是两个按钮。编辑页底部“保存并试听”会先保存，再测试整套效果。\n\n4. 强制提醒使用最大闹钟音量循环播放，执行本组勾选的振动和闪光。点“停止试听”或“停止全部提醒”结束，恢复原闹钟音量；普通提醒跟随通知音量和静音设置。\n\n5. 提醒页、展开通知与悬浮控制可停止提醒或打开默认短信应用。请在“强制提醒权限与后台设置”中允许“悬浮停止按钮”，亮屏时也能操作；结束后悬浮按钮会消失。手动试听可测试已停用的组，不会改变组开关或总开关。\n\n6. 通用后台设置：在手机设置中搜索“自启动”“应用启动管理”“后台活动”或“电池优化”，允许本工具自启动和后台运行，按需取消电池优化。各品牌及系统版本菜单不同，以手机实际界面为准。锁屏全屏提醒与闪光权限可在“强制提醒权限与后台设置”中检查。\n\n7. 普通静音下可使用闹钟式强制提醒；若勿扰禁止闹钟、系统限制后台或应用被强行停止，仍可能无法提醒。最大振动和闪光取决于硬件、权限与相机占用。原短信应用若支持会话单独静音，可关闭目标会话原声音，避免双响；不要关闭整个短信应用的通知。\n\n仅处理普通 SMS，不处理 RCS、彩信或聊天软件消息。部分受系统保护的验证码可能无法触发。请用真实短信验证后台表现。异常强杀后重新打开工具，可恢复之前记录的闹钟音量。")
            .setPositiveButton("知道了", null).setNeutralButton("本应用系统设置", (d, w) -> openAppSettings()).show();
    }
    private void forceSettings() {
        PowerManager power = getSystemService(PowerManager.class);
        String battery = power.isIgnoringBatteryOptimizations(getPackageName()) ? "已允许" : "待设置";
        String screen = Build.VERSION.SDK_INT < 34 || AlertNotifier.manager(this).canUseFullScreenIntent() ? "已允许" : "待设置";
        String overlay = Settings.canDrawOverlays(this) ? "已允许" : "待设置";
        String[] options = {"后台启动 / 电池优化（" + battery + "）", "锁屏全屏提醒（" + screen + "）", "检查声音与勿扰：允许闹钟", "自启动、后台运行与应用权限", "相机权限：用于可选闪光灯", "悬浮停止按钮（" + overlay + "）", "强制提醒通知：横幅、锁屏与展开按钮"};
        new AlertDialog.Builder(this).setTitle("强制提醒设置")
            .setItems(options, (dialog, which) -> {
                try {
                    if (which == 0) startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
                    else if (which == 1 && Build.VERSION.SDK_INT >= 34) startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName())));
                    else if (which == 2) startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
                    else if (which == 4) requestPermissions(new String[]{Manifest.permission.CAMERA}, 201);
                    else if (which == 5) requestOverlay();
                    else if (which == 6) {
                        AlarmService.ensureChannel(this);
                        startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID, AlarmService.CHANNEL));
                    }
                    else openAppSettings();
                } catch (Exception error) { openAppSettings(); }
            }).setPositiveButton("完成", null).show();
    }
    private void requestOverlay() {
        new AlertDialog.Builder(this).setTitle("允许悬浮停止按钮")
            .setMessage("为解决亮屏响铃却没有关闭入口的问题，强制提醒期间可在其他应用上方显示“停止提醒”和“停止并打开短信”。结束提醒后立即移除；不读取屏幕或短信正文。\n\n请在系统设置中允许“显示在其他应用上层”或“悬浮窗”。锁屏全屏提醒仍需单独授权，部分系统页面也可能隐藏悬浮窗。")
            .setPositiveButton("去授权", (d, w) -> {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
                catch (RuntimeException error) { openAppSettings(); }
            }).setNegativeButton("暂不", null).show();
    }
}


