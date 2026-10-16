package cn.local.smstone;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;

public final class RuleEditorActivity extends Activity {
    private Rule rule;
    private EditText labelInput, numberInput;
    private TextView soundLabel;
    private ProgressDialog importProgress;
    private CheckBox forceOption, flashOption, vibrationOption;
    private static final int SYSTEM_SOUND = 10, FILE_SOUND = 11;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null && state.containsKey("draft")) {
            try { rule = Rule.fromJson(new JSONObject(state.getString("draft"))); }
            catch (JSONException ignored) {}
        }
        if (rule == null) rule = new RuleStore(this).find(getIntent().getStringExtra("id"));
        if (rule == null) rule = new Rule();
        render();
    }
    private void render() {
        LinearLayout actions = Ui.row(this);
        Ui.weight(actions, Ui.button(this, "保存规则", false, v -> saveAndClose()));
        actions.addView(new android.view.View(this), new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
        Ui.weight(actions, Ui.button(this, "保存并试听", true, v -> previewSavedSettings()));
        LinearLayout root = Ui.screen(this, actions);
        Ui.add(root, Ui.button(this, "‹ 返回", false, v -> onBackPressed()), 0);
        Ui.add(root, Ui.text(this, "一组号码，一种声音", 26, Ui.INK, true), 24);
        Ui.add(root, Ui.text(this, "原短信应用照常使用。", 14, Ui.MUTED, false), 6);
        LinearLayout card = Ui.card(this);
        Ui.add(card, Ui.text(this, "本组来源号码", 15, Ui.INK, true), 0);
        numberInput = new EditText(this); numberInput.setMinLines(2); numberInput.setMaxLines(5); numberInput.setText(rule.number);
        numberInput.setHint("每行一个完整号码，也可用逗号分隔"); numberInput.setTextSize(17); numberInput.setTextColor(Ui.INK);
        numberInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); Ui.add(card, numberInput, 8);
        Ui.add(card, Ui.text(this, "本组任一号码来短信，使用同一声音。最多 6 组；完整号码精确匹配。", 12, Ui.MUTED, false), 6);
        Ui.add(card, Ui.text(this, "组名称（可不填）", 15, Ui.INK, true), 22);
        labelInput = new EditText(this); labelInput.setSingleLine(true); labelInput.setText(rule.label);
        labelInput.setHint("例如 家人 / 工作通知"); labelInput.setTextSize(16); labelInput.setTextColor(Ui.INK); Ui.add(card, labelInput, 8);
        Ui.add(root, card, 24);

        LinearLayout soundCard = Ui.card(this);
        Ui.add(soundCard, Ui.text(this, "提示音", 17, Ui.INK, true), 0);
        soundLabel = Ui.text(this, rule.soundName, 14, Ui.MUTED, false); Ui.add(soundCard, soundLabel, 10);
        Ui.add(soundCard, Ui.button(this, "选择系统铃声", false, v -> chooseSystemSound()), 18);
        Ui.add(soundCard, Ui.button(this, "从手机选择音频", false, v -> chooseFile()), 10);
        Ui.add(soundCard, Ui.text(this, "支持 MP3、M4A、WAV 等系统可解码音频，最大 20 MB。建议使用短提示音。", 12, Ui.MUTED, false), 12);
        Ui.add(root, soundCard, 16);
        LinearLayout forceCard = Ui.card(this);
        forceOption = new CheckBox(this); forceOption.setText("强制提醒 · 类似闹钟"); forceOption.setTextColor(Ui.INK); forceOption.setTextSize(17); forceOption.setChecked(rule.forceAlarm);
        Ui.add(forceCard, forceOption, 0);
        Ui.add(forceCard, Ui.text(this, "使用最大闹钟音量循环播放，直到手动停止。普通静音模式下可响铃；勿扰需允许闹钟。停止后恢复原闹钟音量。", 13, Ui.MUTED, false), 8);
        vibrationOption = new CheckBox(this); vibrationOption.setText("同步最强振动"); vibrationOption.setTextColor(Ui.INK); vibrationOption.setChecked(rule.vibrate);
        flashOption = new CheckBox(this); flashOption.setText("同步闪光灯"); flashOption.setTextColor(Ui.INK); flashOption.setChecked(rule.flash);
        Ui.add(forceCard, vibrationOption, 12); Ui.add(forceCard, flashOption, 4);
        vibrationOption.setEnabled(rule.forceAlarm); flashOption.setEnabled(rule.forceAlarm);
        forceOption.setOnCheckedChangeListener((b, checked) -> { vibrationOption.setEnabled(checked); flashOption.setEnabled(checked); });
        flashOption.setOnCheckedChangeListener((b, checked) -> {
            if (checked && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 201);
        });
        Ui.add(forceCard, Ui.text(this, "振动强度以手机硬件支持为限。闪光需要相机权限，相机占用或系统限制时可能不可用。", 12, Ui.MUTED, false), 10);
        Ui.add(forceCard, Ui.button(this, "强制提醒权限与后台设置", false, v -> startActivity(new Intent(this, MainActivity.class).putExtra("forceSetup", true))), 16);
        Ui.add(root, forceCard, 16);
        Ui.add(root, Ui.text(this, "底部“保存并试听”会保存本页设置，并执行完整效果：强制提醒最大音量循环，按勾选同步振动与闪光。", 12, Ui.MUTED, false), 16);
        if (new RuleStore(this).find(rule.id) != null) {
            Ui.add(root, Ui.button(this, "打开此号码的系统通知设置", false, v -> channelSettings()), 20);
            Ui.add(root, Ui.button(this, "删除这条规则", false, v -> deleteRule()), 12);
        }
    }
    private void capture() {
        if (numberInput != null) rule.number = numberInput.getText().toString().trim();
        if (labelInput != null) rule.label = labelInput.getText().toString().trim();
        if (forceOption != null) rule.forceAlarm = forceOption.isChecked();
        if (flashOption != null) rule.flash = flashOption.isChecked();
        if (vibrationOption != null) rule.vibrate = vibrationOption.isChecked();
    }
    private void chooseSystemSound() {
        capture();
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择短信专属提示音")
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(rule.soundUri));
        try { startActivityForResult(intent, SYSTEM_SOUND); }
        catch (Exception error) { toast("这台手机未提供系统铃声选择器，请从手机选择音频"); }
    }
    private void chooseFile() {
        capture();
        Intent intent = new Intent(this, AudioPickerActivity.class);
        try { startActivityForResult(intent, FILE_SOUND); }
        catch (Exception error) { toast("未找到文件选择器"); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        if (request == SYSTEM_SOUND) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri == null) return;
            String previous = rule.soundUri;
            rule.soundUri = uri.toString();
            Ringtone sound = RingtoneManager.getRingtone(this, uri);
            rule.soundName = sound == null ? "系统铃声" : sound.getTitle(this);
            soundLabel.setText(rule.soundName);
            SoundFiles.deleteIfUnused(this, previous);
        } else if (request == FILE_SOUND && data.getData() != null) {
            Uri source = data.getData();
            importProgress = ProgressDialog.show(this, "", "正在导入音频…", true, false);
            new Thread(() -> {
                try {
                    String name = SoundFiles.displayName(this, source);
                    Uri imported = SoundFiles.importSound(this, source);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) { SoundFiles.deleteIfUnused(this, imported.toString()); return; }
                        dismissImportProgress();
                        String previous = rule.soundUri;
                        rule.soundUri = imported.toString(); rule.soundName = name;
                        soundLabel.setText(name); SoundFiles.deleteIfUnused(this, previous);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) { dismissImportProgress(); toast(error.getMessage() == null ? "音频导入失败" : error.getMessage()); } });
                }
            }, "sound-import").start();
        }
    }
    private boolean saveRule() {
        capture();
        if (!GroupNumbers.valid(rule.number)) { numberInput.setError("每行一个完整号码，或用逗号分隔；请去除无效或重复号码"); numberInput.requestFocus(); return false; }
        rule.number = GroupNumbers.join(rule.number);
        RuleStore store = new RuleStore(this);
        if (store.find(rule.id) == null && store.all().size() >= GroupNumbers.MAX_GROUPS) { toast("最多设置 6 组，请编辑或删除现有组"); return false; }
        for (Rule other : store.all()) {
            if (!other.id.equals(rule.id) && GroupNumbers.overlaps(other.number, rule.number)) { numberInput.setError("其中一个号码已在其他组，请移除重复号码"); return false; }
        }
        Rule old = store.find(rule.id);
        String oldChannel = old == null ? "" : old.channelId;
        try { AlertNotifier.prepareChannel(this, rule, old == null || !old.soundUri.equals(rule.soundUri)); }
        catch (Exception error) { toast("声音设置失败，请重新选择音频"); return false; }
        if (!store.save(rule)) {
            if (!oldChannel.equals(rule.channelId)) AlertNotifier.deleteChannel(this, rule.channelId);
            rule.channelId = oldChannel;
            toast("保存失败，请重试"); return false;
        }
        if (!oldChannel.equals(rule.channelId)) AlertNotifier.deleteChannel(this, oldChannel);
        if (old != null && !old.soundUri.equals(rule.soundUri)) SoundFiles.deleteIfUnused(this, old.soundUri);
        return true;
    }
    private void saveAndClose() { if (saveRule()) { toast("规则已保存"); finish(); } }
    private void previewSavedSettings() {
        if (!saveRule()) return;
        PreviewController.play(this, rule.id);
    }
    private void channelSettings() {
        Rule existing = new RuleStore(this).find(rule.id);
        if (existing == null || existing.channelId.isEmpty()) return;
        try { startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID, existing.channelId)); }
        catch (Exception error) { toast("请在系统设置中打开本应用的通知设置"); }
    }
    private void deleteRule() {
        new AlertDialog.Builder(this).setTitle("删除这条号码规则？").setMessage("原短信应用和短信内容不受影响。")
            .setNegativeButton("取消", null).setPositiveButton("删除", (dialog, which) -> {
                RuleStore store = new RuleStore(this);
                Rule existing = store.find(rule.id);
                if (rule.id.equals(AlarmService.activeId)) stopService(new Intent(this, AlarmService.class));
                if (!store.delete(rule.id)) { toast("删除失败，请重试"); return; }
                if (existing != null) { AlertNotifier.deleteChannel(this, existing.channelId); SoundFiles.deleteIfUnused(this, existing.soundUri); }
                SoundFiles.deleteIfUnused(this, rule.soundUri); finish();
            }).show();
    }
    @Override public void onBackPressed() {
        capture();
        Rule existing = new RuleStore(this).find(rule.id);
        boolean changed = existing == null ? !rule.number.isEmpty() || !rule.label.isEmpty() || !rule.soundUri.equals("content://settings/system/notification_sound") || rule.forceAlarm || rule.flash || rule.vibrate
            : !rule.number.equals(existing.number) || !rule.label.equals(existing.label) || !rule.soundUri.equals(existing.soundUri)
              || rule.forceAlarm != existing.forceAlarm || rule.flash != existing.flash || rule.vibrate != existing.vibrate;
        if (!changed) { finish(); return; }
        new AlertDialog.Builder(this).setTitle("保存这次修改？").setPositiveButton("保存", (d,w) -> saveAndClose())
            .setNegativeButton("放弃", (d,w) -> { SoundFiles.deleteIfUnused(this, rule.soundUri); finish(); }).setNeutralButton("继续编辑", null).show();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        capture();
        try { state.putString("draft", rule.toJson().toString()); } catch (JSONException ignored) {}
        super.onSaveInstanceState(state);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 201 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            flashOption.setChecked(false); toast("未授权相机：闪光已关闭，声音和振动不受影响");
        }
    }
    @Override protected void onDestroy() {
        dismissImportProgress();
        if (isFinishing() && rule != null) SoundFiles.deleteIfUnused(this, rule.soundUri);
        super.onDestroy();
    }
    private void dismissImportProgress() {
        if (importProgress != null) { importProgress.dismiss(); importProgress = null; }
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
