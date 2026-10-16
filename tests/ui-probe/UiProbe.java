package cn.local.smstone.probe;
import android.app.Instrumentation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
/** Test-only instrumentation in its own process; observes all interactive windows, including nonfocusable overlays. */
public final class UiProbe extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args=arguments; start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            AccessibilityServiceInfo info=getUiAutomation().getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            getUiAutomation().setServiceInfo(info);
            Thread.sleep(250);
            StringBuilder text=new StringBuilder();
            String click=args.getString("click", "");
            String wantedPackage=args.getString("package", "");
            boolean clicked=false;
            for (AccessibilityWindowInfo window:getUiAutomation().getWindows()) {
                AccessibilityNodeInfo root=window.getRoot();
                if (root==null) continue;
                if (!wantedPackage.isEmpty() && !wantedPackage.contentEquals(root.getPackageName())) continue;
                collect(root,text);
                if (!clicked && !click.isEmpty()) {
                    for (AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(click)) {
                        if (click.contentEquals(node.getText()) && node.isVisibleToUser()) {
                            AccessibilityNodeInfo target=node;
                            while (target!=null && !target.isClickable()) target=target.getParent();
                            if (target!=null) clicked=target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                            if (clicked) break;
                        }
                    }
                }
            }
            result.putString("visibleText",text.toString());
            result.putString("clicked",Boolean.toString(clicked));
            finish(-1,result);
        } catch (Throwable error) { result.putString("error",error.toString()); finish(0,result); }
    }
    private void collect(AccessibilityNodeInfo node,StringBuilder text) {
        if (node.isVisibleToUser() && node.getText()!=null) text.append(node.getText()).append(" | ");
        for (int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo child=node.getChild(i); if(child!=null) collect(child,text); }
    }
}
