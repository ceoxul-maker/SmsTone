package cn.local.smstone;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.provider.Telephony;
import android.widget.Toast;

public final class SmsApps {
    private SmsApps() {}
    public static Intent intent(Context context) {
        String app = Telephony.Sms.getDefaultSmsPackage(context);
        Intent launch = app == null ? null : context.getPackageManager().getLaunchIntentForPackage(app);
        if (launch == null) launch = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING);
        return launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    public static void openDefault(Activity activity) {
        activity.stopService(new Intent(activity, AlarmService.class));
        try {
            activity.startActivity(intent(activity));
        } catch (Exception error) { Toast.makeText(activity, "未找到默认短信应用，请在手机设置中选择默认短信应用", Toast.LENGTH_LONG).show(); }
    }
}
