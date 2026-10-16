package cn.local.smstone;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

/** Every preview entry uses a freshly loaded, persisted group. */
public final class PreviewController {
    private PreviewController() {}
    public static void play(Activity activity, String id) {
        if (new RuleStore(activity).find(id) == null) {
            Toast.makeText(activity, "此号码组已不存在，请重新打开", Toast.LENGTH_LONG).show(); return;
        }
        activity.startActivity(new Intent(activity, EffectPreviewActivity.class).putExtra("id", id));
    }
}
