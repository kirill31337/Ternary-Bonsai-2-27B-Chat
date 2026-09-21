package com.prismml.bonsailocal.repair;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

/** Keep both retained web content and native diagnostics inside the usable window. */
public final class WindowLayout {
    private WindowLayout() {}

    public static void setContentView(Activity activity, View content) {
        if (Build.VERSION.SDK_INT < 35) {
            activity.setContentView(content);
            return;
        }
        // Android 15 enforces edge-to-edge for target 35. Pad an Activity-owned
        // container, never the retained WebView, and consume the insets only once.
        FrameLayout root = new FrameLayout(activity);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return WindowInsets.CONSUMED;
            }
        });
        activity.setContentView(root);
        root.requestApplyInsets();
    }
}
