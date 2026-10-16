package cn.local.smstone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AlarmActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) AlarmService.restorePendingVolume(context);
        else if ("cn.local.smstone.STOP_ALARM".equals(intent.getAction())) {
            context.stopService(new Intent(context, AlarmService.class));
            AlarmService.restorePendingVolume(context);
        }
    }
}
