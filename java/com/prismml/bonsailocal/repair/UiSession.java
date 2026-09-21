package com.prismml.bonsailocal.repair;

import android.content.Context;
import android.content.MutableContextWrapper;
import android.os.Build;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.lang.ref.WeakReference;

/** Retain the live document and streaming request, but never retain a destroyed Activity. */
public final class UiSession {
    private static WebView web, management, chat;
    private static String chatSetup;
    private static Context app;
    private static MutableContextWrapper context;
    private static WeakReference<MainActivity> owner = new WeakReference<>(null);
    private static TransferFacade facade;
    private static BonsaiChromeClient chrome;
    private UiSession() {}
    public static WebView open(MainActivity activity) {
        if (web == null) {
            app = activity.getApplicationContext();
            context = new MutableContextWrapper(app);
            context.setBaseContext(activity);
            chrome = new BonsaiChromeClient(activity);
            facade = new TransferFacade(activity);
            management = create(); management.addJavascriptInterface(facade, "Bonsai");
            management.setWebViewClient(new LocalWebClient(new LocalWebClient.ChatNavigation() {
                @Override public void open(String url) {
                    MainActivity activity = owner.get();
                    if (activity != null) showChat(activity, url);
                }
            }));
            management.loadUrl("file:///android_asset/index.html");
            web = management;
        }
        if (web.getParent() instanceof ViewGroup) ((ViewGroup)web.getParent()).removeView(web);
        context.setBaseContext(activity); owner = new WeakReference<>(activity);
        facade.attach(activity); chrome.attach(activity);
        activity.setContentView(web); web.onResume();
        return web;
    }
    private static WebView create() {
        WebView view = new WebView(context);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowContentAccess(true);
        view.setWebViewClient(new LocalWebClient()); view.setWebChromeClient(chrome);
        if (Build.VERSION.SDK_INT >= 26) view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        return view;
    }
    public static void showChat(MainActivity activity, String url) {
        if (!LocalWebClient.chatUrl(url)) return;
        String setup = Extensions.bootstrapHtml();
        if (chat == null) chat = create();
        // Apply changed tool consent on an explicit open, never on Activity resume.
        if (!setup.equals(chatSetup)) { chat.loadUrl(url); chatSetup = setup; }
        switchTo(activity, chat);
    }
    public static boolean back(MainActivity activity) {
        if (web != chat || chat == null) return false;
        switchTo(activity, management); return true;
    }
    private static void switchTo(MainActivity activity, WebView target) {
        if (web != null && web.getParent() instanceof ViewGroup) ((ViewGroup)web.getParent()).removeView(web);
        web = target; open(activity);
    }
    public static void detach(MainActivity activity) {
        BonsaiChromeClient.cancel(activity);
        if (owner.get() != activity) return; // An obsolete window must not detach its replacement.
        if (web != null && web.getParent() instanceof ViewGroup) ((ViewGroup)web.getParent()).removeView(web);
        if (context != null) context.setBaseContext(app);
        owner.clear();
        if (facade != null) facade.attach(null);
        if (chrome != null) chrome.attach(null);
        // No stopLoading, destroy, reload, onPause or global pauseTimers: active chat keeps running.
    }
}
