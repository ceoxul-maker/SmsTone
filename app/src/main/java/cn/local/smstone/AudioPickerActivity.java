package cn.local.smstone;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Audio-first local catalog; the system document picker is only the explicit fallback. */
public final class AudioPickerActivity extends Activity {
    private static final int AUDIO_PERMISSION = 401, DOCUMENT = 402;
    private final List<AudioItem> all = new ArrayList<>(), shown = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Button> typeButtons = new ArrayList<>(), timeButtons = new ArrayList<>();
    private String selectedType = "全部类型";
    private boolean weekOnly;
    private TextView count, empty;
    private Button permissionButton;
    private AudioAdapter adapter;
    private boolean loading, requested, destroyed;
    private static final class AudioItem {
        Uri uri; String name, mime; long size, added, modified;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { selectedType = state.getString("type", "全部类型"); weekOnly = state.getBoolean("week"); requested = state.getBoolean("requested"); }
        LinearLayout outer = Ui.column(this); outer.setBackgroundColor(Ui.BG);
        outer.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout body = Ui.column(this); int p = Ui.dp(this, 20); body.setPadding(p, Ui.dp(this, 16), p, Ui.dp(this, 12));
        outer.addView(body, new LinearLayout.LayoutParams(-1, -1)); setContentView(outer); outer.requestApplyInsets();
        LinearLayout navigation = Ui.row(this);
        Ui.weight(navigation, Ui.button(this, "‹ 返回", false, v -> cancelSelection()));
        navigation.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
        Ui.weight(navigation, Ui.button(this, "取消", false, v -> cancelSelection()));
        Ui.add(body, navigation, 0);
        Ui.add(body, Ui.text(this, "音频", 29, Ui.INK, true), 18);
        Ui.add(body, Ui.text(this, "先按文件类型，再按时间筛选本地音频。", 13, Ui.MUTED, false), 4);
        addChips(body, new String[]{"全部类型", "MP3", "M4A/AAC", "WAV", "其他"}, true);
        addChips(body, new String[]{"全部时间", "本周"}, false);
        count = Ui.text(this, "正在读取音频…", 12, Ui.MUTED, false); Ui.add(body, count, 12);
        permissionButton = Ui.button(this, "允许读取本地音频", true, v -> requestAudio());
        Ui.add(body, permissionButton, 10);
        LinearLayout listArea = Ui.column(this);
        empty = Ui.text(this, "", 15, Ui.MUTED, false); empty.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 16));
        listArea.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        ListView list = new ListView(this); list.setDividerHeight(Ui.dp(this, 10)); list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.BG));
        adapter = new AudioAdapter(); list.setAdapter(adapter);
        listArea.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(listArea, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((parent, view, position, id) -> select(shown.get(position)));
        Ui.add(body, Ui.button(this, "浏览其他位置…", false, v -> browseDocuments()), 12);
        Ui.add(body, Ui.text(this, "找不到文件？可浏览其他位置。这里只读取音频列表，不会修改或上传文件。", 11, Ui.MUTED, false), 8);
        if (allowed()) loadAudio();
        else {
            applyFilters();
            if (!requested) { requested = true; body.post(this::requestAudio); }
        }
    }
    private String permission() { return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE; }
    private boolean allowed() { return checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED; }
    private void requestAudio() { requestPermissions(new String[]{permission()}, AUDIO_PERMISSION); }
    private void addChips(LinearLayout body, String[] labels, boolean format) {
        HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(this);
        for (String label : labels) {
            Button chip = Ui.button(this, label, false, v -> {
                if (format) selectedType = label; else weekOnly = "本周".equals(label);
                updateChips(); applyFilters();
            });
            chip.setMinHeight(Ui.dp(this, 42)); chip.setMinimumHeight(Ui.dp(this, 42));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2); params.rightMargin = Ui.dp(this, 8);
            row.addView(chip, params); (format ? typeButtons : timeButtons).add(chip);
        }
        scroll.addView(row); Ui.add(body, scroll, 12); updateChips();
    }
    private void updateChips() {
        for (Button button : typeButtons) styleChip(button, button.getText().toString().equals(selectedType));
        for (Button button : timeButtons) styleChip(button, button.getText().toString().equals(weekOnly ? "本周" : "全部时间"));
    }
    private void styleChip(Button button, boolean selected) {
        button.setBackground(Ui.background(selected ? Ui.GREEN : Ui.PALE, 18, this)); button.setTextColor(selected ? Color.WHITE : Ui.GREEN);
        button.setSelected(selected);
    }
    private void loadAudio() {
        if (loading || destroyed) return;
        loading = true; permissionButton.setVisibility(View.GONE); count.setText("正在读取音频…");
        worker.execute(() -> {
            List<AudioItem> result = new ArrayList<>(); String failure = null;
            Uri base = Build.VERSION.SDK_INT >= 29 ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] columns = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATE_MODIFIED};
            try (Cursor cursor = getContentResolver().query(base, columns, null, null, null)) {
                if (cursor != null) while (cursor.moveToNext()) {
                    AudioItem item = new AudioItem(); item.uri = ContentUris.withAppendedId(base, cursor.getLong(0));
                    item.name = cursor.getString(1); item.mime = cursor.getString(2); item.size = cursor.getLong(3);
                    item.added = cursor.getLong(4); item.modified = cursor.getLong(5);
                    if (item.name == null) item.name = "未命名音频";
                    result.add(item);
                }
                Collections.sort(result, (a,b) -> {
                    int time = Long.compare(Math.max(b.modified,b.added), Math.max(a.modified,a.added));
                    return time != 0 ? time : a.name.compareToIgnoreCase(b.name);
                });
            } catch (Exception error) { failure = "暂时无法读取音频，可使用“浏览其他位置”。"; }
            final String message = failure;
            runOnUiThread(() -> {
                if (destroyed) return;
                loading = false; all.clear(); all.addAll(result); applyFilters();
                if (message != null) { empty.setText(message); empty.setVisibility(View.VISIBLE); }
            });
        });
    }
    private void applyFilters() {
        if (adapter == null) return;
        permissionButton.setVisibility(allowed() ? View.GONE : View.VISIBLE);
        shown.clear();
        long start = AudioFilters.mondayStartSeconds(System.currentTimeMillis());
        for (AudioItem item : all) if (AudioFilters.matches(item.name, item.mime, item.added, item.modified, selectedType, weekOnly, start)) shown.add(item);
        count.setText(shown.size() + " 个音频" + (weekOnly ? " · 本周添加或修改（周一起）" : " · 按时间排序"));
        empty.setText(!allowed() ? "允许读取音频后显示手机中的音乐、录音和提示音；也可直接浏览其他位置。"
            : all.isEmpty() ? "手机媒体库中暂未找到音频。可通过下方按钮选择未被媒体库收录的文件。"
            : "没有符合筛选条件的音频，请切换文件类型或时间标签。");
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE); adapter.notifyDataSetChanged();
    }
    private void select(AudioItem item) {
        if (item.size > 20L * 1024 * 1024) { Toast.makeText(this, "请选择小于 20 MB 的音频", Toast.LENGTH_LONG).show(); return; }
        returnUri(item.uri);
    }
    private void returnUri(Uri uri) {
        Intent result = new Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        result.setClipData(ClipData.newRawUri("audio", uri));
        setResult(RESULT_OK, result); finish();
    }
    private void cancelSelection() { setResult(RESULT_CANCELED); finish(); }
    @Override public void onBackPressed() { cancelSelection(); }
    private void browseDocuments() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*");
        // AOSP hint only. Other document providers may choose their own initial location.
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri("com.android.providers.media.documents", "audio_root"));
        try { startActivityForResult(intent, DOCUMENT); }
        catch (Exception error) { Toast.makeText(this, "未找到文件选择器", Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == DOCUMENT && result == RESULT_OK && data != null && data.getData() != null) returnUri(data.getData());
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == AUDIO_PERMISSION) { if (allowed()) loadAudio(); else applyFilters(); }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("type", selectedType); state.putBoolean("week", weekOnly); state.putBoolean("requested", requested);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { destroyed = true; worker.shutdownNow(); super.onDestroy(); }
    private final class AudioAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            AudioItem item = shown.get(position);
            LinearLayout row = Ui.card(AudioPickerActivity.this);
            Ui.add(row, Ui.text(AudioPickerActivity.this, item.name, 16, Ui.INK, true), 0);
            long date = Math.max(item.modified, item.added);
            String detail = AudioFilters.format(item.name, item.mime) + "  ·  " + String.format(Locale.getDefault(), "%.2f MB", item.size / 1048576.0)
                + (date > 0 ? "  ·  " + DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(date * 1000)) : "");
            Ui.add(row, Ui.text(AudioPickerActivity.this, detail, 12, Ui.MUTED, false), 6);
            return row;
        }
    }
}
