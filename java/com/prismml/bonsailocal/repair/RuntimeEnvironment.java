package com.prismml.bonsailocal.repair;

import android.content.Context;
import java.io.File;

/** One native environment per application process, never tied to an Activity. */
public final class RuntimeEnvironment {
    private static boolean initialized;
    private static boolean notificationAsked;
    private RuntimeEnvironment() {}
    public static void requestNotifications(android.app.Activity activity) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !notificationAsked &&
                activity.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            notificationAsked = true;
            activity.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 3011);
        }
    }
    public static synchronized void ensure(Context context) {
        if (initialized) return;
        Context app = context.getApplicationContext();
        File external = app.getExternalFilesDir(null);
        if (external == null) throw new IllegalStateException("Хранилище модели недоступно");
        System.loadLibrary("bonsai_app");
        Bridge.init(app, external.getAbsolutePath(), app.getApplicationInfo().nativeLibraryDir,
                app.getFilesDir().getAbsolutePath());
        Transfers.configure(app);
        Bridge bridge = new Bridge();
        Transfers.restoreRuntime(bridge);
        Extensions.configure(app);
        initialized = true;
    }
}
