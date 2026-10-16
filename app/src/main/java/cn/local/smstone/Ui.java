package cn.local.smstone;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(245, 246, 242), INK = Color.rgb(30, 43, 37), MUTED = Color.rgb(101, 114, 107);
    static final int GREEN = Color.rgb(23, 107, 89), PALE = Color.rgb(230, 239, 232);
    static int dp(Context c, int value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static LinearLayout column(Context context) { LinearLayout view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL); return view; }
    static LinearLayout row(Context context) { LinearLayout view = new LinearLayout(context); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(android.view.Gravity.CENTER_VERTICAL); return view; }
    static GradientDrawable background(int color, int radius, Context context) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(context, radius)); return shape;
    }
    static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView text = new TextView(context); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setLineSpacing(dp(context, 3), 1f); return text;
    }
    static void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(parent.getContext(), top); parent.addView(child, lp);
    }
    static void weight(LinearLayout parent, View child) { parent.addView(child, new LinearLayout.LayoutParams(0, -2, 1)); }
    static LinearLayout card(Context context) {
        LinearLayout card = column(context); card.setBackground(background(Color.WHITE, 20, context));
        int p = dp(context, 18); card.setPadding(p, p, p, p); return card;
    }
    static Button button(Context context, String title, boolean primary, View.OnClickListener listener) {
        Button button = new Button(context); button.setText(title); button.setAllCaps(false); button.setTextSize(15);
        button.setTextColor(primary ? Color.WHITE : GREEN); button.setBackground(background(primary ? GREEN : PALE, 14, context));
        button.setMinHeight(dp(context, 50)); button.setMinimumHeight(dp(context, 50));
        button.setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8)); button.setOnClickListener(listener); return button;
    }
    static LinearLayout screen(Activity activity) {
        return screen(activity, null);
    }
    static LinearLayout screen(Activity activity, View actions) {
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout outer = column(activity); outer.setBackgroundColor(BG);
        outer.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        LinearLayout content = column(activity); int p = dp(activity, 20); content.setPadding(p, dp(activity, 20), p, dp(activity, 32));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (actions != null) {
            actions.setPadding(dp(activity, 20), dp(activity, 12), dp(activity, 20), dp(activity, 12));
            actions.setBackgroundColor(BG);
            outer.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        }
        activity.setContentView(outer);
        outer.requestApplyInsets(); return content;
    }
}
