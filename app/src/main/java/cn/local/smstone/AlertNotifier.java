package cn.local.smstone;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import java.util.UUID;

public final class AlertNotifier {
    private AlertNotifier() {}
    public static AudioAttributes audioAttributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    }
    public static NotificationManager manager(Context context) { return context.getSystemService(NotificationManager.class); }
    public static boolean permissionGranted(Context context) {
        return Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
    public static void prepareChannel(Context context, Rule rule, boolean newSound) {
        if (newSound || rule.channelId.isEmpty()) rule.channelId = "rule_" + UUID.randomUUID();
        NotificationManager manager = manager(context);
        if (manager.getNotificationChannel(rule.channelId) != null) {
            NotificationChannel existing = manager.getNotificationChannel(rule.channelId);
            existing.setName(rule.displayName());
            manager.createNotificationChannel(existing);
            return;
        }
        NotificationChannel channel = new NotificationChannel(rule.channelId, rule.displayName(), NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("号码 " + rule.number + " 的专属短信提醒");
        channel.setSound(Uri.parse(rule.soundUri), audioAttributes());
        channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
    }
    public static String post(Context context, Rule rule, boolean test) {
        NotificationManager manager = manager(context);
        if (!permissionGranted(context) || !manager.areNotificationsEnabled()) return "未发出：请允许本应用发送通知";
        prepareChannel(context, rule, false);
        NotificationChannel channel = manager.getNotificationChannel(rule.channelId);
        if (channel == null || channel.getImportance() == NotificationManager.IMPORTANCE_NONE) return "未发出：此号码的通知类别已关闭";
        Intent open = SmsApps.intent(context);
        PendingIntent pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, rule.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(test ? "专属铃声测试" : rule.displayName() + " 发来短信")
            .setContentText(test ? "此声音与实际短信提醒使用相同设置" : "点按在系统默认短信应用中查看")
            .setContentIntent(pending).setAutoCancel(true).setTimeoutAfter(15000)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_MESSAGE)
            .build();
        try {
            manager.notify(rule.id, 1, notification);
            if (channel.getSound() == null) return "已发出提醒；此号码在系统设置中为静音";
            return "已发出提醒（实际响铃受音量、静音和勿扰设置影响）";
        } catch (SecurityException error) { return "未发出：通知或声音读取权限不可用，请重新选择声音"; }
    }
    public static void deleteChannel(Context context, String id) {
        if (id != null && !id.isEmpty()) manager(context).deleteNotificationChannel(id);
    }
}
