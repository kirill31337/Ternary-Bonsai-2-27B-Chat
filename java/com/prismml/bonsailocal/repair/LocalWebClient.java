package com.prismml.bonsailocal.repair;
import android.webkit.*;import android.net.Uri;import android.content.*;import java.io.*;import java.net.*;import java.util.*;
/** Management bridge is removed before loading chat. Web results never enter the WebView.
 * Only the chat origin and our authenticated local MCP endpoint may make HTTP requests in it.
 */
public final class LocalWebClient extends WebViewClient {
 public LocalWebClient(){}
 public static boolean chatUrl(String s){if(s==null)return false;try{URI u=new URI(s);return "http".equals(u.getScheme())&&"127.0.0.1".equals(u.getHost())&&u.getPort()==18080&&u.getRawUserInfo()==null;}catch(Exception e){return false;}}
 @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return navigate(v,r.getUrl().toString(),r.isForMainFrame()&&r.hasGesture());}
 @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return navigate(v,url,false);}
 private boolean navigate(WebView v,String url,boolean explicitGesture){
  if(chatUrl(url)){v.removeJavascriptInterface("Bonsai");return false;}
  if("file:///android_asset/index.html".equals(url)&&!chatUrl(v.getUrl()))return false;
  // Source links are opened explicitly in the user's browser, not inside a privileged WebView.
  if(explicitGesture&&url!=null&&url.startsWith("https://"))try{v.getContext().startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException ignored){}
  return true;
 }
 @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return intercept(r.getUrl().toString(),r.isForMainFrame());}
 @Override public WebResourceResponse shouldInterceptRequest(WebView v,String url){return intercept(url,true);}
 private static WebResourceResponse intercept(String url,boolean main){
  if(chatUrl(url)){
   try{URI u=new URI(url);if("/bonsai-connect".equals(u.getPath())){if(!main)return deny();return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(Extensions.bootstrapHtml().getBytes("UTF-8")));}}
   catch(Exception e){return deny();}return null;
  }
  if(Extensions.ownEndpoint(url))return null;
  if(url!=null&&(url.startsWith("http:")||url.startsWith("https:")||url.startsWith("file:")&&!"file:///android_asset/index.html".equals(url)))return deny();
  return null;
 }
 private static WebResourceResponse deny(){return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",Collections.<String,String>emptyMap(),new ByteArrayInputStream(new byte[0]));}
}
