package cn.local.smstone;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

public final class RuleStore {
    private final SharedPreferences prefs;
    public RuleStore(Context context) { prefs = context.getSharedPreferences("rules", Context.MODE_PRIVATE); }
    public boolean enabled() { return prefs.getBoolean("enabled", true); }
    public void setEnabled(boolean value) { prefs.edit().putBoolean("enabled", value).apply(); }

    public List<Rule> all() {
        List<Rule> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < array.length(); i++) result.add(Rule.fromJson(array.getJSONObject(i)));
        } catch (JSONException ignored) { /* Preferences are private; leave invalid records disabled. */ }
        return result;
    }

    public Rule find(String id) {
        for (Rule rule : all()) if (rule.id.equals(id)) return rule;
        return null;
    }

    public boolean save(Rule rule) {
        List<Rule> rules = all();
        for (int i = 0; i < rules.size(); i++) if (rules.get(i).id.equals(rule.id)) { rules.set(i, rule); return write(rules); }
        if (rules.size() >= GroupNumbers.MAX_GROUPS) return false;
        rules.add(rule);
        return write(rules);
    }

    public boolean delete(String id) {
        List<Rule> kept = new ArrayList<>();
        for (Rule rule : all()) if (!rule.id.equals(id)) kept.add(rule);
        return write(kept);
    }

    private boolean write(List<Rule> rules) {
        JSONArray array = new JSONArray();
        try { for (Rule rule : rules) array.put(rule.toJson()); }
        catch (JSONException error) { return false; }
        return prefs.edit().putString("items", array.toString()).commit();
    }

    public void recordResult(String summary) {
        prefs.edit().putString("lastResult", summary).putLong("lastTime", System.currentTimeMillis()).apply();
    }
    public String lastResult() { return prefs.getString("lastResult", "还没有触发记录"); }
    public long lastTime() { return prefs.getLong("lastTime", 0); }

    // Durable journal: recovery on next launch/boot if Android kills the alarm process without onDestroy.
    public boolean journalAlarmVolume(int previous) {
        if (prefs.contains("previousAlarmVolume")) return true;
        return prefs.edit().putInt("previousAlarmVolume", previous).commit();
    }
    public int previousAlarmVolume() { return prefs.getInt("previousAlarmVolume", -1); }
    public boolean clearAlarmVolume() { return prefs.edit().remove("previousAlarmVolume").commit(); }
}
