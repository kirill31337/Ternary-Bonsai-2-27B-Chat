#!/usr/bin/env python3
"""Compile-only API signatures for the small new Android adapter.
Not runtime mocks and MUST NEVER be packaged. Use ANDROID_JAR for official SDK instead.
The retained 0.2 Activity and native bridge are assembled from smali, not these stubs.
"""
from pathlib import Path
r=Path(__file__).resolve().parent/'build/api-signatures'
s={
'android/content/Context':'''public abstract class Context { public static final String DOWNLOAD_SERVICE="download",NOTIFICATION_SERVICE="notification",POWER_SERVICE="power"; public Context getApplicationContext(){return null;} public java.io.File getExternalFilesDir(String x){return null;} public java.io.File getFilesDir(){return null;} public Object getSystemService(String s){return null;} public ContentResolver getContentResolver(){return null;} public ComponentName startForegroundService(Intent i){return null;} }''',
'android/content/ContentResolver':'''public abstract class ContentResolver {public java.io.InputStream openInputStream(android.net.Uri u)throws java.io.FileNotFoundException{return null;} public void releasePersistableUriPermission(android.net.Uri u,int f){} }''',
'android/content/ComponentName':'''public final class ComponentName {}''',
'android/content/Intent':'''public class Intent { public static final int FLAG_GRANT_READ_URI_PERMISSION=1; public Intent(Context c,Class<?> x){} public Intent setAction(String s){return this;} public String getAction(){return null;} public Intent setData(android.net.Uri u){return this;} public android.net.Uri getData(){return null;} public Intent addFlags(int f){return this;} }''',
'android/net/Uri':'''public abstract class Uri {}''',
'android/app/Service':'''public abstract class Service extends android.content.Context { public static final int START_NOT_STICKY=2; public void onCreate(){} public int onStartCommand(android.content.Intent i,int f,int id){return 2;} public void onDestroy(){} public void onTimeout(int id,int type){} public abstract android.os.IBinder onBind(android.content.Intent i); public final void startForeground(int i,Notification n){} public final void stopForeground(boolean b){} public final void stopSelf(){} public final void stopSelf(int id){} }''',
'android/app/Activity':'''public class Activity extends android.content.Context {public final void runOnUiThread(Runnable r){} }''',
'android/app/DownloadManager':'''public class DownloadManager {public int remove(long... ids){return 0;} }''',
'android/app/Notification':'''public class Notification { public static class Builder {public Builder(android.content.Context c,String ch){} public Builder setSmallIcon(int i){return this;} public Builder setContentTitle(CharSequence t){return this;} public Builder setContentText(CharSequence t){return this;} public Builder setContentIntent(PendingIntent p){return this;} public Builder setOngoing(boolean b){return this;} public Builder setOnlyAlertOnce(boolean b){return this;} public Builder setProgress(int a,int b,boolean c){return this;} public Builder addAction(int icon,CharSequence text,PendingIntent i){return this;} public Notification build(){return null;} } }''',
'android/app/NotificationChannel':'''public final class NotificationChannel {public NotificationChannel(String s,CharSequence n,int i){} }''',
'android/app/NotificationManager':'''public class NotificationManager {public static final int IMPORTANCE_LOW=2; public void createNotificationChannel(NotificationChannel c){} public void notify(int i,Notification n){} }''',
'android/app/PendingIntent':'''public final class PendingIntent {public static final int FLAG_UPDATE_CURRENT=134217728,FLAG_IMMUTABLE=67108864; public static PendingIntent getActivity(android.content.Context c,int r,android.content.Intent i,int f){return null;} public static PendingIntent getService(android.content.Context c,int r,android.content.Intent i,int f){return null;} }''',
'android/os/Handler':'''public class Handler {public Handler(Looper l){} public final boolean post(Runnable r){return true;} public final boolean postDelayed(Runnable r,long ms){return true;} public final void removeCallbacks(Runnable r){} }''',
'android/os/Looper':'''public final class Looper {public static Looper getMainLooper(){return null;} }''',
'android/os/IBinder':'''public interface IBinder {}''',
'android/os/PowerManager':'''public final class PowerManager {public static final int PARTIAL_WAKE_LOCK=1; public WakeLock newWakeLock(int l,String t){return null;} public final class WakeLock {public void setReferenceCounted(boolean b){} public void acquire(long ms){} public boolean isHeld(){return false;} public void release(){} } }''',
'android/webkit/JavascriptInterface':'''@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD) public @interface JavascriptInterface {}''',
'android/R':'''public final class R { public static final class drawable {public static final int stat_sys_download=17301633,ic_media_pause=17301539;} }''',
'com/prismml/bonsailocal/repair/MainActivity':'''public class MainActivity extends android.app.Activity {public void pickModel(){} public void showError(Throwable e){} }''',
'com/prismml/bonsailocal/repair/Bridge':'''public class Bridge {public native String status();public native void start();public native void stop();public native void selfTest();public native boolean configureOptions(int model,int ctx,boolean q4,int t,int tb,int reasoning);public native boolean configureRuntime(int model,int ctx,boolean q4,int t,int tb,int reasoning,int gpuLayers);}''',
}

s['android/content/Context']=s['android/content/Context'].replace('public Context getApplicationContext()', 'public void startActivity(Intent i){} public Context getApplicationContext()')
s['android/app/Activity']=s['android/app/Activity'].replace('public final void runOnUiThread', 'public void startActivityForResult(android.content.Intent i,int r){} public final void runOnUiThread')
s['android/content/Intent']=s['android/content/Intent'].replace('public static final int FLAG_GRANT_READ_URI_PERMISSION=1;', 'public static final int FLAG_GRANT_READ_URI_PERMISSION=1,FLAG_ACTIVITY_NEW_TASK=268435456; public static final String CATEGORY_OPENABLE="android.intent.category.OPENABLE",ACTION_VIEW="android.intent.action.VIEW"; public Intent(String a,android.net.Uri u){} public Intent addCategory(String c){return this;}')
s['android/net/Uri']='public abstract class Uri {public static Uri parse(String s){return null;}public abstract String getScheme();}'
s['com/prismml/bonsailocal/repair/Bridge']=s['com/prismml/bonsailocal/repair/Bridge'].replace('public native String status()', 'public native boolean configureVision(boolean on);public native String status()')
s.update({
'android/webkit/ValueCallback': 'public interface ValueCallback<T> {void onReceiveValue(T value);}',
'android/webkit/WebView': 'public class WebView {public String getUrl(){return null;}public android.content.Context getContext(){return null;}public void removeJavascriptInterface(String s){} }',
'android/webkit/WebResourceRequest': 'public interface WebResourceRequest {android.net.Uri getUrl();boolean isForMainFrame();boolean hasGesture();}',
'android/webkit/WebResourceResponse': 'public class WebResourceResponse {public WebResourceResponse(String m,String e,java.io.InputStream i){}public WebResourceResponse(String m,String e,int c,String r,java.util.Map<String,String> h,java.io.InputStream i){} }',
'android/webkit/WebViewClient': 'public class WebViewClient {public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return false;}public boolean shouldOverrideUrlLoading(WebView v,String u){return false;}public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return null;}public WebResourceResponse shouldInterceptRequest(WebView v,String u){return null;} }',
'android/webkit/WebChromeClient': 'public class WebChromeClient {public boolean onShowFileChooser(WebView v,ValueCallback<android.net.Uri[]> cb,FileChooserParams p){return false;}public abstract static class FileChooserParams {public abstract android.content.Intent createIntent();public static android.net.Uri[] parseResult(int c,android.content.Intent i){return null;} } }',
'android/security/keystore/KeyProperties': 'public abstract class KeyProperties {public static final int PURPOSE_ENCRYPT=1,PURPOSE_DECRYPT=2;public static final String BLOCK_MODE_GCM="GCM",ENCRYPTION_PADDING_NONE="NoPadding";}',
'android/security/keystore/KeyGenParameterSpec': 'public final class KeyGenParameterSpec implements java.security.spec.AlgorithmParameterSpec {public static final class Builder {public Builder(String a,int p){}public Builder setBlockModes(String... x){return this;}public Builder setEncryptionPaddings(String... x){return this;}public Builder setKeySize(int n){return this;}public KeyGenParameterSpec build(){return null;}}}',
})

s['com/prismml/bonsailocal/repair/Bridge']=s['com/prismml/bonsailocal/repair/Bridge'].replace('public native String status()', 'public static native void init(android.content.Context c,String e,String l,String f);public native String status()')
s['com/prismml/bonsailocal/repair/Report']='public class Report {public static void record(android.content.Context c,Throwable e){} }'

for name,body in s.items():
 p=r/(name+'.java');p.parent.mkdir(parents=True,exist_ok=True);p.write_text('package '+name.rsplit('/',1)[0].replace('/','.')+';\n'+body+'\n')
print(r)
