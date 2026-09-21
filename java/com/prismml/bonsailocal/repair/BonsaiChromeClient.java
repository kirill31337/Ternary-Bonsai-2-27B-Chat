package com.prismml.bonsailocal.repair;
import java.lang.ref.WeakReference;
import android.app.Activity;import android.content.Intent;import android.net.Uri;import android.webkit.*;import java.util.*;
/** SAF chooser for local chat attachments. No broad storage, camera or microphone permission. */
public final class BonsaiChromeClient extends WebChromeClient {
 private static final int PICK=7396;
 private static final Map<Activity,ValueCallback<Uri[]>> waiting=new WeakHashMap<>();
 private volatile WeakReference<MainActivity> owner;
 public BonsaiChromeClient(MainActivity a){attach(a);}
 public void attach(MainActivity a){owner=new WeakReference<>(a);}
 @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
  MainActivity activity=owner.get();
  if(activity==null){callback.onReceiveValue(null);return true;}
  if(!LocalWebClient.chatUrl(view.getUrl())){callback.onReceiveValue(null);return true;}
  cancel(activity);waiting.put(activity,callback);
  try{Intent intent=params.createIntent();intent.addCategory(Intent.CATEGORY_OPENABLE);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);activity.startActivityForResult(intent,PICK);}
  catch(RuntimeException e){cancel(activity);activity.showError(e);}return true;
 }
 public static boolean result(Activity a,int request,int result,Intent data){if(request!=PICK)return false;ValueCallback<Uri[]> cb=waiting.remove(a);if(cb==null)return true;Uri[] out=null;
  try{Uri[] parsed=FileChooserParams.parseResult(result,data);if(parsed!=null&&parsed.length<=16){List<Uri> safe=new ArrayList<>();for(Uri uri:parsed)if(uri!=null&&"content".equals(uri.getScheme()))safe.add(uri);if(!safe.isEmpty())out=safe.toArray(new Uri[0]);}}
  catch(RuntimeException ignored){}cb.onReceiveValue(out);return true;
 }
 public static void cancel(Activity a){ValueCallback<Uri[]> cb=waiting.remove(a);if(cb!=null)cb.onReceiveValue(null);}
}
