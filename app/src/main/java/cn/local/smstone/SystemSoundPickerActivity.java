package cn.local.smstone;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Our own confirmation UI, independent of vendor ringtone-picker layouts. */
public final class SystemSoundPickerActivity extends Activity {
    private final Handler handler = new Handler();
    private final List<String> names = new ArrayList<>();
    private final List<Uri> uris = new ArrayList<>();
    private Uri selected;
    private String selectedName;
    private Ringtone preview;
    private TextView selection;
    private Button confirm, listen;
    private ListView list;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String initial = state == null ? getIntent().getStringExtra("soundUri") : state.getString("soundUri");
        selected = initial == null ? null : Uri.parse(initial);
        selectedName = state == null ? getIntent().getStringExtra("soundName") : state.getString("soundName");
        LinearLayout root = Ui.column(this); root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(Ui.dp(this, 20) + insets.getSystemWindowInsetLeft(), Ui.dp(this, 12) + insets.getSystemWindowInsetTop(),
                Ui.dp(this, 20) + insets.getSystemWindowInsetRight(), Ui.dp(this, 12) + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        LinearLayout top = Ui.row(this);
        Ui.weight(top, Ui.button(this, "‹ 返回", false, v -> cancel()));
        top.addView(new android.view.View(this), new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
        Ui.weight(top, Ui.button(this, "取消", false, v -> cancel()));
        Ui.add(root, top, 0);
        Ui.add(root, Ui.text(this, "系统铃声", 27, Ui.INK, true), 18);
        selection = Ui.text(this, "正在读取系统铃声…", 14, Ui.MUTED, false); Ui.add(root, selection, 8);
        list = new ListView(this); list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, 0, 1); listParams.topMargin = Ui.dp(this, 12);
        root.addView(list, listParams);
        Ui.add(root, Ui.text(this, "选择后点“确定”才会应用。这里单次试听；完整强制效果请保存后试听。", 12, Ui.MUTED, false), 10);
        LinearLayout actions = Ui.row(this);
        listen = Ui.button(this, "试听所选", false, v -> audition()); listen.setEnabled(false);
        confirm = Ui.button(this, "确定", true, v -> {
            if (selected == null) return;
            stopPreview();
            setResult(RESULT_OK, new Intent().putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, selected).putExtra("soundName", selectedName));
            finish();
        }); confirm.setEnabled(false);
        Ui.weight(actions, listen);
        actions.addView(new android.view.View(this), new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
        Ui.weight(actions, confirm); Ui.add(root, actions, 12);
        setContentView(root); root.requestApplyInsets();
        list.setOnItemClickListener((parent, view, position, id) -> {
            stopPreview(); selected = uris.get(position); selectedName = names.get(position);
            selection.setText("已选：" + selectedName); confirm.setEnabled(true); listen.setEnabled(true);
        });
        new Thread(this::load, "system-ringtones").start();
    }

    private void load() {
        List<String> titles = new ArrayList<>(); List<Uri> values = new ArrayList<>();
        titles.add("系统默认通知音"); values.add(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        titles.add("系统默认来电铃声"); values.add(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
        titles.add("系统默认闹钟音"); values.add(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        Cursor cursor = null;
        boolean failed = false;
        try {
            RingtoneManager manager = new RingtoneManager(getApplicationContext()); manager.setType(RingtoneManager.TYPE_ALL);
            cursor = manager.getCursor();
            if (cursor != null) while (cursor.moveToNext()) {
                Uri uri = manager.getRingtoneUri(cursor.getPosition());
                if (uri == null || values.contains(uri)) continue;
                titles.add(cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)); values.add(uri);
            }
        } catch (RuntimeException unavailable) { failed = true; }
        finally { if (cursor != null) cursor.close(); }
        final boolean partial = failed;
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            names.addAll(titles); uris.addAll(values);
            list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, names));
            int checked = -1;
            for (int i = 0; selected != null && i < uris.size(); i++) {
                if (uris.get(i).buildUpon().clearQuery().build().equals(selected.buildUpon().clearQuery().build())) { checked = i; break; }
            }
            if (checked >= 0) {
                selected = uris.get(checked); selectedName = names.get(checked); list.setItemChecked(checked, true); list.setSelection(checked);
                confirm.setEnabled(true); listen.setEnabled(true); selection.setText("已选：" + selectedName);
            } else selection.setText("请选择一种系统铃声，原声音保持不变");
            if (partial) Toast.makeText(this, "部分铃声无法读取；可选择系统默认声音或返回选择本地音频", Toast.LENGTH_LONG).show();
        });
    }

    private void audition() {
        stopPreview();
        try {
            preview = RingtoneManager.getRingtone(this, selected);
            if (preview == null) throw new IllegalStateException();
            preview.setAudioAttributes(AlertNotifier.audioAttributes());
            if (Build.VERSION.SDK_INT >= 28) preview.setLooping(false);
            preview.play(); handler.postDelayed(this::stopPreview, 10000);
        } catch (RuntimeException unavailable) { Toast.makeText(this, "无法试听此铃声，请检查通知音量或选择其他声音", Toast.LENGTH_LONG).show(); }
    }
    private void stopPreview() { handler.removeCallbacksAndMessages(null); if (preview != null) { preview.stop(); preview = null; } }
    private void cancel() { stopPreview(); setResult(RESULT_CANCELED); finish(); }
    @Override public void onBackPressed() { cancel(); }
    @Override protected void onPause() { stopPreview(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (selected != null) state.putString("soundUri", selected.toString());
        state.putString("soundName", selectedName); super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { stopPreview(); super.onDestroy(); }
}
