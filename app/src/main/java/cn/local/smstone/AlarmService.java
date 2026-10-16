package cn.local.smstone;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** One audible alarm at a time. A new group replaces the active sound; one stop ends all effects. */
public final class AlarmService extends Service {
    public static final String CHANNEL = "forced_alarm_v1";
    public static final int NOTIFICATION_ID = 602;
    public static volatile boolean running;
    public static volatile String activeTitle = "", activeId = "", status = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Rule active;
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private CameraManager camera;
    private String torchId;
    private boolean torchOn;
    private boolean triedFallback;
    private boolean stopping;
    private String warnings = "";
    private AlarmControls controls;
    private int focusAttempts;
    private final Runnable startPlayback = new Runnable() {
        @Override public void run() {
            if (stopping || active == null || focus == null) return;
            int result;
            try { result = audio.requestAudioFocus(focus); }
            catch (RuntimeException error) { result = AudioManager.AUDIOFOCUS_REQUEST_FAILED; }
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED && ++focusAttempts < 4) { handler.postDelayed(this, 350); return; }
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                warning("系统未授予音频焦点：声音未启动，请打开提醒或停止后重试"); return;
            }
            play(Uri.parse(active.soundUri));
        }
    };

    public static AudioAttributes attributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    }
    public static void ensureChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "强制提醒（持续响铃）", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("铃声由闹钟通道循环播放；通知上可随时停止");
        channel.setSound(null, null); channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        AlertNotifier.manager(context).createNotificationChannel(channel);
    }
    public static boolean notificationReady(Context context) {
        ensureChannel(context);
        NotificationChannel channel = AlertNotifier.manager(context).getNotificationChannel(CHANNEL);
        return AlertNotifier.permissionGranted(context) && AlertNotifier.manager(context).areNotificationsEnabled()
            && channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    public static String start(Context context, Rule rule, boolean test) {
        if (!notificationReady(context)) return "未启动强提醒：请先允许通知和“强制提醒”通知类别，以便手动停止";
        try {
            context.startForegroundService(new Intent(context, AlarmService.class).putExtra("id", rule.id).putExtra("test", test).putExtra("restart", test));
            return "已请求强制提醒；运行状态请看提醒通知";
        } catch (RuntimeException error) {
            try { fallbackNotification(context, rule); }
            catch (RuntimeException unavailable) { new RuleStore(context).recordResult("强制提醒未启动：后台和通知均被系统限制"); }
            return "系统阻止后台启动：请点提醒通知手动启动，并允许后台运行";
        }
    }
    private static PendingIntent openIntent(Context context, Rule rule, boolean manual) {
        Intent open = new Intent(context, AlarmActivity.class).putExtra("id", rule.id).putExtra("manualStart", manual)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 60, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static void fallbackNotification(Context context, Rule rule) {
        AlertNotifier.manager(context).notify(NOTIFICATION_ID, new Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(rule.displayName() + "：需要启动强提醒")
            .setContentText("系统限制后台启动，点按后开始循环提醒")
            .setContentIntent(openIntent(context, rule, true)).setAutoCancel(true).setCategory(Notification.CATEGORY_ALARM).build());
    }
    private Notification notification(Rule rule) {
        PendingIntent open = openIntent(this, rule, false);
        PendingIntent stop = PendingIntent.getBroadcast(this, 61,
            new Intent(this, AlarmActionReceiver.class).setAction("cn.local.smstone.STOP_ALARM"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent openSms = PendingIntent.getActivity(this, 62,
            new Intent(this, AlarmActivity.class).putExtra("stopAndOpenSms", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action stopAction = new Notification.Action.Builder(null, "停止提醒", stop).build();
        Notification.Action smsAction = new Notification.Action.Builder(null, "停止并打开短信", openSms).build();
        Notification publicVersion = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("短信强制提醒").setContentText("循环播放中；展开可停止或打开短信")
            .setContentIntent(open).setOngoing(true).setCategory(Notification.CATEGORY_ALARM)
            .addAction(stopAction).addAction(smsAction).build();
        Notification.Builder builder = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(rule.displayName() + " · 强制提醒")
            .setContentText(status.isEmpty() ? "最大闹钟音量循环播放，点“停止全部提醒”关闭" : status)
            .setContentIntent(open).setOngoing(true).setCategory(Notification.CATEGORY_ALARM)
            .setStyle(new Notification.BigTextStyle().bigText("循环提醒中，请手动停止。展开通知可选“停止提醒”或“停止并打开短信”。\n" + status))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).setOnlyAlertOnce(true)
            .addAction(stopAction).addAction(smsAction)
            // Keep the intent when FSI is denied: Android then supplies its heads-up fallback.
            .setFullScreenIntent(open, true);
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return builder.build();
    }
    @Override public void onCreate() {
        super.onCreate();
        audio = getSystemService(AudioManager.class);
        vibrator = getSystemService(Vibrator.class);
        camera = getSystemService(CameraManager.class);
        controls = new AlarmControls(this);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        Rule rule = new RuleStore(this).find(intent.getStringExtra("id"));
        if (rule == null || !rule.forceAlarm || (!intent.getBooleanExtra("test", false) && (!rule.enabled || !new RuleStore(this).enabled()))) {
            new RuleStore(this).recordResult("强制提醒已结束：号码组已关闭、删除或不再使用强提醒"); stopSelf(); return START_NOT_STICKY;
        }
        if (!notificationReady(this)) { new RuleStore(this).recordResult("强制提醒未启动：需要允许通知及停止按钮"); stopSelf(); return START_NOT_STICKY; }
        boolean same = running && active != null && active.id.equals(rule.id) && !intent.getBooleanExtra("restart", false);
        activeTitle = rule.displayName();
        activeId = rule.id;
        active = rule;
        status = same ? status : "正在启动闹钟循环提醒…";
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification(rule), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTIFICATION_ID, notification(rule));
            running = true;
            if (!same) beginAlarm(rule);
            if (!controls.show()) warning("悬浮停止按钮未显示；可下拉展开通知，或打开本应用停止。请在强制提醒设置中允许悬浮控制");
        } catch (RuntimeException error) {
            new RuleStore(this).recordResult("强制提醒启动失败，请检查后台和通知权限");
            stopSelf();
        }
        return START_NOT_STICKY;
    }
    private void beginAlarm(Rule rule) {
        stopEffects();
        warnings = "";
        triedFallback = false; stopping = false;
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmsTone:activeAlarm");
            wakeLock.setReferenceCounted(false);
        }
        wakeLock.acquire();
        RuleStore store = new RuleStore(this);
        if (!store.journalAlarmVolume(audio.getStreamVolume(AudioManager.STREAM_ALARM))) {
            status = "无法记录原闹钟音量，已停止；请重新测试"; store.recordResult(status); stopSelf(); return;
        }
        try { audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0); }
        catch (RuntimeException error) { warning("系统不允许调整闹钟音量，使用当前音量"); }
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes()).setOnAudioFocusChangeListener(change -> {
                if (change < 0) updateStatus("音频焦点被其他应用占用；可点停止结束提醒");
            }).build();
        // Allow the foreground promotion to settle before requesting audio focus.
        focusAttempts = 0;
        handler.postDelayed(startPlayback, 300);
        if (rule.vibrate && vibrator != null && vibrator.hasVibrator()) {
            try { vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,700,350}, new int[]{0,255,0}, 0), attributes()); }
            catch (RuntimeException error) { warning("系统限制振动"); }
        }
        else if (rule.vibrate) warning("手机未提供可用振动器");
        if (rule.flash) startFlash();
    }
    private void play(Uri uri) {
        if (player != null) { player.release(); player = null; }
        try {
            MediaPlayer next = new MediaPlayer(); player = next;
            next.setAudioAttributes(attributes()); next.setDataSource(this, uri); next.setLooping(true); next.setVolume(1f, 1f);
            next.setOnPreparedListener(prepared -> {
                if (stopping || player != prepared) return;
                try { prepared.start(); updateStatus((triedFallback ? "正在使用备用系统闹钟音 · " : "") + "循环提醒中 · 请手动停止"); }
                catch (RuntimeException error) { audioFailed(); }
            });
            next.setOnErrorListener((failed, what, extra) -> { if (failed == player) audioFailed(); return true; });
            next.prepareAsync();
        } catch (Exception error) { audioFailed(); }
    }
    private void audioFailed() {
        if (!triedFallback) {
            triedFallback = true;
            updateStatus("所选音频不可用，改用系统闹钟音");
            Uri fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (fallback == null) updateStatus("系统未提供默认闹钟音，请停止后重新选择声音");
            else play(fallback);
        } else updateStatus("音频无法播放，请停止后重新选择声音；已选的振动/闪光仍可工作");
    }
    private void startFlash() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { warning("闪光未开启：请允许相机权限"); return; }
        try {
            for (String id : camera.getCameraIdList()) {
                CameraCharacteristics info = camera.getCameraCharacteristics(id);
                if (Boolean.TRUE.equals(info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))
                    && Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(info.get(CameraCharacteristics.LENS_FACING))) { torchId = id; break; }
            }
            if (torchId == null) { warning("没有可用闪光灯"); return; }
            // Torch API does not open a camera device. Do not start a background camera-type FGS.
            handler.post(flashTick);
        } catch (Exception error) { warning("闪光不可用或相机被占用"); }
    }
    private final Runnable flashTick = new Runnable() {
        @Override public void run() {
            if (stopping || torchId == null) return;
            try { torchOn = !torchOn; camera.setTorchMode(torchId, torchOn); handler.postDelayed(this, 600); }
            catch (Exception error) { turnOffTorch(); warning("系统限制闪光或相机被占用"); }
        }
    };
    private void turnOffTorch() {
        handler.removeCallbacks(flashTick);
        if (torchId != null && torchOn) { try { camera.setTorchMode(torchId, false); } catch (Exception ignored) {} }
        torchId = null; torchOn = false;
    }
    private void updateStatus(String text) {
        status = text + (warnings.isEmpty() ? "" : "\n" + warnings);
        if (active != null && running) {
            new RuleStore(this).recordResult(active.displayName() + "：" + status);
            try { AlertNotifier.manager(this).notify(NOTIFICATION_ID, notification(active)); }
            catch (RuntimeException error) { new RuleStore(this).recordResult("通知权限失效，已停止强制提醒"); stopSelf(); }
        }
    }
    private void warning(String text) {
        if (!warnings.contains(text)) warnings += (warnings.isEmpty() ? "" : "；") + text;
        updateStatus("强制提醒中");
    }
    private void stopEffects() {
        stopping = true;
        handler.removeCallbacks(startPlayback);
        if (player != null) { player.release(); player = null; }
        if (vibrator != null) vibrator.cancel();
        turnOffTorch();
        if (focus != null) { audio.abandonAudioFocusRequest(focus); focus = null; }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }
    public static void restorePendingVolume(Context context) {
        if (running) return;
        RuleStore store = new RuleStore(context);
        int previous = store.previousAlarmVolume();
        if (previous < 0) return;
        try {
            AudioManager audio = context.getSystemService(AudioManager.class);
            audio.setStreamVolume(AudioManager.STREAM_ALARM, Math.min(previous, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)), 0);
            store.clearAlarmVolume();
        } catch (RuntimeException ignored) { /* Retry on next launch rather than discarding the journal. */ }
    }
    @Override public void onDestroy() {
        if (controls != null) controls.remove();
        stopEffects(); running = false; activeTitle = ""; activeId = "";
        restorePendingVolume(this);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
