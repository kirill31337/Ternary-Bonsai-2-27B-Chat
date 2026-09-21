package com.prismml.bonsailocal.repair;

import android.app.*;
import android.content.*;
import android.os.*;

/** Keeps user-started local inference alive independently of the window. */
public final class RuntimeService extends Service {
    public static final String START = "com.prismml.bonsailocal.RUN";
    public static final String TEST = "com.prismml.bonsailocal.TEST";
    public static final String STOP = "com.prismml.bonsailocal.STOP";
    private static final String CHANNEL = "bonsai_inference";
    private static final int NOTICE = 3011;
    private static volatile RuntimeService current;
    private static volatile String failure = "";
    private final Bridge bridge = new Bridge();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wake;
    private boolean foreground, commanded;
    private String lastNotice = "";

    public static String error() { return failure; }
    public static void request(Context context, String action) {
        failure = "";
        if (STOP.equals(action) && current == null) {
            LocalBenchmark.cancel(); new Bridge().stop(); return;
        }
        try { context.getApplicationContext().startForegroundService(
                new Intent(context, RuntimeService.class).setAction(action)); }
        catch (RuntimeException e) { failure = "Не удалось запустить фоновую работу: " + e.getMessage(); throw e; }
    }
    @Override public void onCreate() {
        super.onCreate();
        try {
            NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Работа модели", NotificationManager.IMPORTANCE_LOW));
            Notification notification = notice("Подготовка модели");
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTICE, notification, 0x40000000); // specialUse
            else startForeground(NOTICE, notification);
            foreground = true;
            RuntimeEnvironment.ensure(this);
            current = this;
        } catch (Throwable e) { fail(e); finish(); }
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foreground) { stopSelf(); return START_NOT_STICKY; }
        try {
            String action = intent == null ? null : intent.getAction();
            if (STOP.equals(action)) { LocalBenchmark.cancel(); bridge.stop(); commanded = true; }
            else if (START.equals(action) || TEST.equals(action)) {
                if (!RuntimeSnapshot.parse(bridge.status()).active()) {
                    if (Transfers.isBusy() || LocalBenchmark.isBusy()) { finish(); return START_NOT_STICKY; }
                    if (START.equals(action) && (!Transfers.readyFile() || !Extensions.prepare(bridge))) {
                        failure = "Модель или модуль изображений не готовы"; finish(); return START_NOT_STICKY;
                    }
                    PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
                    if (wake == null) {
                        wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BonsaiLocal:inference");
                        wake.setReferenceCounted(false);
                    }
                    if (!wake.isHeld()) wake.acquire();
                    if (START.equals(action)) bridge.start(); else bridge.selfTest();
                }
                commanded = true;
            } else if (!commanded) { finish(); return START_NOT_STICKY; }
            handler.removeCallbacks(ticker); handler.post(ticker);
        } catch (Throwable e) { fail(e); bridge.stop(); commanded = true; handler.post(ticker); }
        return START_NOT_STICKY;
    }
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!foreground) return;
            try {
                RuntimeSnapshot snapshot = RuntimeSnapshot.parse(bridge.status());
                if (commanded && !snapshot.active()) { finish(); return; }
                String text = snapshot.description();
                if (!text.equals(lastNotice)) {
                    // A rejected notification refresh must not remove protection from a live model.
                    try { ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE, notice(text)); }
                    catch (RuntimeException e) { Report.record(RuntimeService.this, e); }
                    lastNotice = text;
                }
                handler.postDelayed(this, 750);
            } catch (Throwable e) {
                fail(e);
                // Keep protection while asking the native worker to terminate; a later snapshot
                // confirms exit. Releasing now could leave an unprotected inference child.
                bridge.stop(); commanded = true; handler.postDelayed(this, 750);
            }
        }
    };
    private Notification notice(String text) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent view = PendingIntent.getActivity(this, NOTICE, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, NOTICE + 1, new Intent(this, RuntimeService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Bonsai Local").setContentText(text).setContentIntent(view)
                .setOngoing(true).setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_media_pause, "Остановить", stop).build();
    }
    private void fail(Throwable e) { failure = "Фоновая работа недоступна: " + e.getMessage(); Report.record(this, e); }
    private void finish() {
        handler.removeCallbacks(ticker);
        if (wake != null && wake.isHeld()) wake.release();
        wake = null; foreground = false;
        if (current == this) current = null;
        stopForeground(true); stopSelf();
    }
    @Override public void onDestroy() {
        // Activity destruction never calls this. A stopped service must not leave an unprotected child.
        if (foreground) { LocalBenchmark.cancel(); bridge.stop(); }
        finish(); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
