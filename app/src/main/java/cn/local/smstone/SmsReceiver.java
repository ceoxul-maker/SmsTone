package cn.local.smstone;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.util.HashSet;
import java.util.Set;

public final class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        if (context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) return;
        RuleStore store = new RuleStore(context);
        if (!store.enabled()) return;
        SmsMessage[] messages;
        try { messages = Telephony.Sms.Intents.getMessagesFromIntent(intent); }
        catch (RuntimeException error) { store.recordResult("未识别到短信号码"); return; }
        if (messages == null) return;
        Set<String> senders = new HashSet<>();
        for (SmsMessage message : messages) {
            if (message != null && message.getOriginatingAddress() != null) senders.add(message.getOriginatingAddress());
        }
        // A multipart SMS arrives as multiple PDUs; post once per matching sender, never per segment.
        for (String sender : senders) {
            for (Rule rule : store.all()) {
                if (rule.enabled && GroupNumbers.matches(rule.number, sender)) {
                    String previousChannel = rule.channelId;
                    String result = rule.forceAlarm ? AlarmService.start(context, rule, false) : AlertNotifier.post(context, rule, false);
                    if (!rule.channelId.equals(previousChannel)) store.save(rule);
                    store.recordResult(rule.displayName() + "：" + result);
                    break;
                }
            }
        }
    }
}
