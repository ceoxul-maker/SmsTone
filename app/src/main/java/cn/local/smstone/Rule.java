package cn.local.smstone;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public final class Rule {
    public String id = UUID.randomUUID().toString();
    public String label = "";
    public String number = "";
    public String soundUri = "content://settings/system/notification_sound";
    public String soundName = "系统默认通知音";
    public String channelId = "";
    public boolean enabled = true;
    public boolean forceAlarm = false;
    public boolean flash = false;
    public boolean vibrate = false;

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("label", label).put("number", number)
            .put("soundUri", soundUri).put("soundName", soundName).put("channelId", channelId).put("enabled", enabled)
            .put("forceAlarm", forceAlarm).put("flash", flash).put("vibrate", vibrate);
    }

    public static Rule fromJson(JSONObject json) {
        Rule rule = new Rule();
        rule.id = json.optString("id", rule.id);
        rule.label = json.optString("label", "");
        rule.number = json.optString("number", "");
        rule.soundUri = json.optString("soundUri", rule.soundUri);
        rule.soundName = json.optString("soundName", rule.soundName);
        rule.channelId = json.optString("channelId", "");
        rule.enabled = json.optBoolean("enabled", true);
        rule.forceAlarm = json.optBoolean("forceAlarm", false);
        rule.flash = json.optBoolean("flash", false);
        rule.vibrate = json.optBoolean("vibrate", false);
        return rule;
    }

    public String displayName() {
        if (!label.trim().isEmpty()) return label;
        java.util.List<String> numbers = GroupNumbers.parse(number);
        return numbers.isEmpty() ? "未命名号码组" : numbers.get(0) + (numbers.size() > 1 ? " 等 " + numbers.size() + " 个号码" : "");
    }
}
