package cn.local.smstone;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Foreground, user-initiated preview. Forced groups use the exact SMS alarm service. */
public final class EffectPreviewActivity extends Activity {
    private final Handler handler = new Handler();
    private Rule rule;
    private TextView statusView;
    private MediaPlayer normalPlayer;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean permissionsAsked, permissionPending, firstResume = true, visible;
    private boolean waiting, observed, wanted, normalWanted;
    private long requestedAt;
    private final Runnable normalStart = this::startNormalWhenReady;
    private final Runnable normalTimeout = () -> { stopNormal(); showStatus("普通试听已结束。强制提醒关闭时，不会最大音量循环播放。"); };
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!visible || rule == null) return;
            if (rule.forceAlarm && wanted) {
                if (AlarmService.running && rule.id.equals(AlarmService.activeId)) {
                    observed = true; waiting = false;
                    showStatus(AlarmService.status);
                } else if (observed) {
                    wanted = false;
                    showStatus(new RuleStore(EffectPreviewActivity.this).previousAlarmVolume() < 0
                        ? "试听已停止，原闹钟音量已恢复。" : "试听已停止，音量恢复记录仍待处理，请重新打开应用。");
                } else if (waiting && SystemClock.elapsedRealtime() - requestedAt > 5000) {
                    waiting = false;
                    showStatus("未检测到提醒启动。\n" + new RuleStore(EffectPreviewActivity.this).lastResult()
                        + "\n请检查通知权限，或停止后重新试听。");
                }
            }
            handler.postDelayed(this, 400);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        rule = new RuleStore(this).find(getIntent().getStringExtra("id"));
        if (rule == null) { finish(); return; }
        audio = getSystemService(AudioManager.class);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = Ui.screen(this);
        Ui.add(root, Ui.text(this, "SMS TONE  /  试听保存的效果", 12, Ui.GREEN, true), 0);
        Ui.add(root, Ui.text(this, rule.displayName(), 29, Ui.INK, true), 26);
        LinearLayout settings = Ui.card(this);
        Ui.add(settings, Ui.text(this, rule.soundName, 18, Ui.INK, true), 0);
        String description = rule.forceAlarm
            ? "强制提醒 · 最大闹钟音量 · 循环播放\n振动：" + (rule.vibrate ? "最强" : "关闭") + "   闪光：" + (rule.flash ? "开启" : "关闭")
            : "普通提醒 · 使用系统通知音量 · 单次播放\n强制提醒已关闭，不会调高闹钟音量。";
        Ui.add(settings, Ui.text(this, description, 15, Ui.MUTED, false), 12);
        Ui.add(root, settings, 24);
        statusView = Ui.text(this, "正在准备试听…", 17, Ui.INK, true); Ui.add(root, statusView, 26);
        Ui.add(root, Ui.button(this, "停止试听", true, v -> stopPreview()), 28);
        Ui.add(root, Ui.button(this, "重新试听", false, v -> requestPreview()), 12);
        Ui.add(root, Ui.button(this, "停止试听并打开短信", false, v -> { stopPreview(); SmsApps.openDefault(this); finish(); }), 12);
        Ui.add(root, Ui.button(this, "通知与权限设置", false, v -> {
            stopPreview();
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }), 12);
        Ui.add(root, Ui.text(this, "手动试听可以测试已停用的组，不会开启该组或总开关。强制提醒遵守勿扰对闹钟的限制；闪光与振动取决于权限和硬件。试听不能代替真实短信接收测试。", 12, Ui.MUTED, false), 24);
        if (state != null) {
            permissionsAsked = state.getBoolean("asked");
            if (rule.forceAlarm && AlarmService.running && rule.id.equals(AlarmService.activeId)) {
                firstResume = false; wanted = true; observed = true;
            }
        }
    }
    @Override public void onResume() {
        super.onResume(); visible = true;
        if (rule == null) return;
        if (firstResume && !permissionPending) { firstResume = false; handler.post(this::requestPreview); }
        handler.post(refresh);
    }
    private void requestPreview() {
        if (!visible || rule == null || permissionPending) return;
        Rule saved = new RuleStore(this).find(rule.id);
        if (saved == null) { showStatus("此号码组已被删除。"); return; }
        rule = saved;
        if (rule.forceAlarm && !permissionsAsked) {
            List<String> needed = new ArrayList<>();
            if (Build.VERSION.SDK_INT >= 33 && !AlertNotifier.permissionGranted(this)) needed.add(Manifest.permission.POST_NOTIFICATIONS);
            if (rule.flash && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.CAMERA);
            permissionsAsked = true;
            if (!needed.isEmpty()) {
                permissionPending = true;
                requestPermissions(needed.toArray(new String[0]), 301); return;
            }
        }
        if (rule.forceAlarm) {
            String result = AlarmService.start(this, rule, true);
            if (!result.startsWith("已请求")) { wanted = false; showStatus(result); return; }
            requestedAt = SystemClock.elapsedRealtime(); wanted = true; waiting = true; observed = false;
            showStatus("正在按保存的强制提醒设置启动…");
        } else {
            normalWanted = true;
            handler.removeCallbacks(normalStart);
            // Never overlap a leftover forced alarm with a normal preview.
            if (AlarmService.running) {
                stopService(new Intent(this, AlarmService.class));
                handler.postDelayed(normalStart, 200);
            } else startNormalWhenReady();
        }
    }
    private void startNormalWhenReady() {
        if (!visible || !normalWanted) return;
        if (AlarmService.running) { handler.postDelayed(normalStart, 200); return; }
        stopNormal();
        if (audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL || audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0) {
            showStatus("普通提醒当前为静音。请调高通知音量，或返回编辑并开启“强制提醒”后保存。 "); return;
        }
        try {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AlertNotifier.audioAttributes()).setOnAudioFocusChangeListener(change -> {}).build();
            if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                showStatus("系统未允许音频播放，请稍后重新试听。"); stopNormal(); return;
            }
            MediaPlayer player = new MediaPlayer(); normalPlayer = player;
            player.setAudioAttributes(AlertNotifier.audioAttributes()); player.setDataSource(this, Uri.parse(rule.soundUri));
            player.setLooping(false);
            player.setOnPreparedListener(ready -> {
                if (!visible || ready != normalPlayer) return;
                try { ready.start(); showStatus("正在试听普通提醒 · 系统通知音量"); handler.postDelayed(normalTimeout, 15000); }
                catch (RuntimeException error) { stopNormal(); showStatus("音频无法播放，请重新选择声音。"); }
            });
            player.setOnCompletionListener(done -> { if (done == normalPlayer) { stopNormal(); showStatus("普通提醒试听结束。"); } });
            player.setOnErrorListener((failed, what, extra) -> { if (failed == normalPlayer) { stopNormal(); showStatus("音频无法播放，请重新选择声音。"); } return true; });
            player.prepareAsync();
        } catch (Exception error) { stopNormal(); showStatus("声音文件无法读取，请重新选择音频。"); }
    }
    private void stopNormal() {
        handler.removeCallbacks(normalTimeout);
        if (normalPlayer != null) { normalPlayer.release(); normalPlayer = null; }
        if (focus != null) { audio.abandonAudioFocusRequest(focus); focus = null; }
    }
    private void stopPreview() {
        wanted = false; waiting = false; observed = false; normalWanted = false;
        handler.removeCallbacks(normalStart);
        stopNormal();
        stopService(new Intent(this, AlarmService.class));
        AlarmService.restorePendingVolume(this);
        showStatus("已停止试听。强制提醒结束后会恢复原闹钟音量。");
    }
    private void showStatus(String value) {
        if (statusView != null && !value.contentEquals(statusView.getText())) statusView.setText(value);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 301) {
            permissionPending = false; firstResume = true;
            if (visible) { firstResume = false; handler.post(this::requestPreview); }
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("asked", permissionsAsked); super.onSaveInstanceState(state);
    }
    @Override public void onPause() {
        visible = false; normalWanted = false; handler.removeCallbacks(refresh);
        handler.removeCallbacks(normalStart);
        if (normalPlayer != null) { stopNormal(); showStatus("普通试听已停止。"); }
        super.onPause();
    }
    @Override public void onBackPressed() { stopPreview(); finish(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); stopNormal(); super.onDestroy(); }
}
