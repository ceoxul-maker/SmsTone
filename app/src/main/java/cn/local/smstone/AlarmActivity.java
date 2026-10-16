package cn.local.smstone;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Can appear above the lock screen without unlocking the phone or exposing SMS content. */
public final class AlarmActivity extends Activity {
    private final Handler handler = new Handler();
    private TextView title, details;
    private boolean seenRunning;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (AlarmService.running) {
                seenRunning = true;
                title.setText(AlarmService.activeTitle);
                details.setText(AlarmService.status + "\n\n关闭声音、振动和闪光后，会恢复原闹钟音量。");
            } else if (seenRunning) { finish(); return; }
            else details.setText("正在等待强制提醒启动。若没有声音，可返回查看最近触发记录和后台权限。\n\n点下方按钮可停止全部提醒。");
            handler.postDelayed(this, 600);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (openSmsIfRequested(getIntent())) return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = Ui.screen(this);
        Ui.add(root, Ui.text(this, "SMS TONE  /  强制提醒", 12, Ui.GREEN, true), 18);
        title = Ui.text(this, "重要短信提醒", 32, Ui.INK, true); Ui.add(root, title, 46);
        details = Ui.text(this, "", 17, Ui.MUTED, false); Ui.add(root, details, 26);
        Ui.add(root, Ui.button(this, "停止全部提醒", true, v -> stopAndClose()), 48);
        Ui.add(root, Ui.button(this, "停止提醒并打开短信", false, v -> { SmsApps.openDefault(this); finish(); }), 12);
        Ui.add(root, Ui.text(this, "停止只结束当前提醒；新的匹配短信仍会再次触发。", 13, Ui.MUTED, false), 16);
        manualStartIfRequested(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (!openSmsIfRequested(intent)) manualStartIfRequested(intent);
    }
    private boolean openSmsIfRequested(Intent intent) {
        if (intent == null || !intent.getBooleanExtra("stopAndOpenSms", false)) return false;
        intent.removeExtra("stopAndOpenSms");
        SmsApps.openDefault(this); finish(); return true;
    }
    private void manualStartIfRequested(Intent intent) {
        if (intent != null && intent.getBooleanExtra("manualStart", false)) {
            intent.putExtra("manualStart", false);
            Rule rule = new RuleStore(this).find(intent.getStringExtra("id"));
            if (rule != null) AlarmService.start(this, rule, false);
        }
    }
    private void stopAndClose() {
        stopService(new Intent(this, AlarmService.class));
        AlarmService.restorePendingVolume(this);
        finish();
    }
    @Override public void onResume() { super.onResume(); if (!isFinishing()) handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override public void onBackPressed() { stopAndClose(); }
}
