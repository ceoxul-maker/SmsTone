package cn.local.smstone;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

/** User-authorized controls independent of OEM heads-up notifications. */
final class AlarmControls {
    private final Context context;
    private final WindowManager windows;
    private View panel;

    AlarmControls(Context context) {
        this.context = context;
        windows = context.getSystemService(WindowManager.class);
    }

    boolean show() {
        if (!Settings.canDrawOverlays(context)) { remove(); return false; }
        if (panel != null) return true;
        LinearLayout card = Ui.card(context);
        card.setElevation(Ui.dp(context, 8));
        Ui.add(card, Ui.text(context, "短信强制提醒", 18, Ui.INK, true), 0);
        Ui.add(card, Ui.text(context, "循环播放中 · 停止后恢复原音量", 13, Ui.MUTED, false), 6);
        LinearLayout actions = Ui.row(context);
        Ui.weight(actions, Ui.button(context, "停止提醒", true, v -> context.stopService(new Intent(context, AlarmService.class))));
        actions.addView(new View(context), new LinearLayout.LayoutParams(Ui.dp(context, 8), 1));
        Ui.weight(actions, Ui.button(context, "停止并打开短信", false, v -> {
            // Launch directly from a visible, user-tapped overlay, not a notification trampoline.
            context.stopService(new Intent(context, AlarmService.class));
            try { context.startActivity(SmsApps.intent(context)); }
            catch (RuntimeException error) {
                android.widget.Toast.makeText(context, "提醒已停止；请手动打开默认短信应用", android.widget.Toast.LENGTH_LONG).show();
            }
        }));
        Ui.add(card, actions, 14);
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            Math.min(Ui.dp(context, 440), screen - Ui.dp(context, 24)), -2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = Ui.dp(context, 64);
        params.setTitle("SmsTone alarm controls");
        try { windows.addView(card, params); panel = card; return true; }
        catch (RuntimeException denied) { return false; }
    }

    void remove() {
        if (panel == null) return;
        try { windows.removeViewImmediate(panel); } catch (RuntimeException ignored) {}
        panel = null;
    }
}
