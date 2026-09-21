package com.prismml.bonsailocal.repair;
import java.io.*;import java.nio.file.*;import java.net.*;import java.util.*;
import android.app.Activity;import android.content.*;import android.net.Uri;import android.webkit.*;
/** Production adapters exercised with explicit Android API boundary doubles; not an emulator. */
public final class BoundaryTest {
 static int n;static void ok(boolean b,String s){if(!b)throw new AssertionError(s);n++;System.out.println("PASS "+s);}
 static class A extends MainActivity {
  File ext,files;int services,picks,errors;
  A(Path p)throws Exception{ext=Files.createDirectory(p.resolve("ext")).toFile();files=Files.createDirectory(p.resolve("files")).toFile();}
  public Context getApplicationContext(){return this;}public File getExternalFilesDir(String x){return ext;}public File getFilesDir(){return files;}
  public ComponentName startForegroundService(Intent i){services++;return new ComponentName();}
  public void pickModel(){picks++;}public void showError(Throwable e){errors++;}
 }
 static class R implements WebResourceRequest {String url;boolean main,gesture;R(String s,boolean m,boolean g){url=s;main=m;gesture=g;}public Uri getUrl(){return Uri.parse(url);}public boolean isForMainFrame(){return main;}public boolean hasGesture(){return gesture;}}
 static class Result implements ValueCallback<Uri[]>{int calls;Uri[] last;public void onReceiveValue(Uri[] r){calls++;last=r;}}
 static File sparse(File root,ModelCatalog.Model m)throws Exception{File f=new File(root,m.name);try(RandomAccessFile out=new RandomAccessFile(f,"rw")){out.writeBytes("GGUF");out.setLength(m.size);}return f;}
 static void ready(A a,int id)throws Exception {ModelCatalog.Model m=ModelCatalog.get(id);sparse(a.ext,m);Files.writeString(new File(a.files,m.marker).toPath(),m.sha+"\n");}
 public static void main(String[] argv)throws Exception {
  Path p=Files.createTempDirectory("bonsai-boundary-");A a=new A(p);WebView v=new WebView();v.context=a;v.url="http://127.0.0.1:18080/";LocalWebClient client=new LocalWebClient();
  ok(LocalWebClient.chatUrl(v.url)&&!LocalWebClient.chatUrl("http://127.0.0.1:18080.evil/")&&!LocalWebClient.chatUrl("http://127.0.0.1:18081/")&&!LocalWebClient.chatUrl("http://user@127.0.0.1:18080/"),"trusted chat URL is exact, not prefix matching");
  client.shouldOverrideUrlLoading(v,new R("https://example.com/",false,false));ok(a.externalCalls==0,"iframe cannot open external browser without user gesture");
  client.shouldOverrideUrlLoading(v,new R("https://example.com/",true,false));ok(a.externalCalls==0,"automatic top-level navigation cannot open external browser");
  client.shouldOverrideUrlLoading(v,new R("https://example.com/",true,true));ok(a.externalCalls==1,"explicit source link opens external browser only");
  ok(!client.shouldOverrideUrlLoading(v,new R("http://127.0.0.1:18080/",true,true))&&v.removed.contains("Bonsai"),"management JavascriptInterface removed before trusted chat navigation");
  final String[] requested={null};LocalWebClient manager=new LocalWebClient(new LocalWebClient.ChatNavigation(){public void open(String url){requested[0]=url;}});
  v.url="file:///android_asset/index.html";v.removed.clear();
  ok(manager.shouldOverrideUrlLoading(v,new R("http://127.0.0.1:18080/bonsai-connect",true,true))&&requested[0].endsWith("/bonsai-connect")&&v.removed.isEmpty(),"management navigation is cancelled and delegated to a separate unprivileged chat document");
  requested[0]=null;manager.shouldOverrideUrlLoading(v,new R("http://127.0.0.1:18081/",true,true));ok(requested[0]==null,"untrusted local origin cannot reach native chat navigation");
  ok(client.shouldInterceptRequest(v,new R("https://untrusted.example/image",false,false)).code==403,"external subresource blocked in privileged chat WebView");
  ok(client.shouldInterceptRequest(v,new R("http://127.0.0.1:29999/",false,false)).code==403,"other local services blocked in chat WebView");
  ok(client.shouldInterceptRequest(v,new R("http://127.0.0.1:18080/v1/models",false,false))==null,"chat API requests stay with actual native server");
  ok(client.shouldInterceptRequest(v,new R("http://127.0.0.1:18080/bonsai-connect",false,false)).code==403,"bootstrap is not served to subframes");
  BonsaiChromeClient chooser=new BonsaiChromeClient(a);WebChromeClient.FileChooserParams params=new WebChromeClient.FileChooserParams(){public Intent createIntent(){return new Intent("android.intent.action.GET_CONTENT",null);}};Result result=new Result();
  v.url="https://not-chat.example/";chooser.onShowFileChooser(v,result,params);ok(result.calls==1&&result.last==null&&a.pickerCalls==0,"untrusted page cannot open file chooser");
  v.url="http://127.0.0.1:18080/";result=new Result();chooser.onShowFileChooser(v,result,params);ok(a.pickerCalls==1&&a.requestCode==7396&&a.lastIntent.category.equals(Intent.CATEGORY_OPENABLE)&&a.lastIntent.flags==Intent.FLAG_GRANT_READ_URI_PERMISSION,"SAF chooser requests narrow readable documents");
  Intent selection=new Intent("result",null);selection.uris=new Uri[]{Uri.parse("content://docs/image"),Uri.parse("file:///private/model"),Uri.parse("https://remote.example/private"),null};ok(BonsaiChromeClient.result(a,7396,-1,selection)&&result.last.length==1&&"content".equals(result.last[0].getScheme()),"only user-selected content URIs are returned to WebView");
  Result one=new Result(),two=new Result();chooser.onShowFileChooser(v,one,params);chooser.onShowFileChooser(v,two,params);ok(one.calls==1&&one.last==null&&two.calls==0,"new picker cancels previous callback once");BonsaiChromeClient.cancel(a);ok(two.calls==1&&two.last==null,"Activity cleanup cancels pending file callback");
  Result cancelled=new Result();chooser.onShowFileChooser(v,cancelled,params);BonsaiChromeClient.result(a,7396,0,null);ok(cancelled.calls==1&&cancelled.last==null,"cancelled Android result resolves callback");ok(!BonsaiChromeClient.result(a,7303,-1,selection),"GGUF import request is not intercepted by chat picker");
  ready(a,0);ready(a,1);Bridge b=new Bridge();Path libs=Files.createDirectory(p.resolve("libs"));Bridge.init(a,a.ext.toString(),libs.toString(),a.files.toString());
  try {
   TransferFacade facade=new TransferFacade(a);ok(facade.configure(1,16384,false,4,8,0).isEmpty(),"0.5 PQ selection preserved through real options/JNI adapter");
   Map<String,Object> status=MiniJson.object(MiniJson.parse(Extensions.status()));ok(((Number)status.get("mode")).intValue()==0&&Boolean.FALSE.equals(status.get("vision")),"fresh extension settings are offline and text-only");
   ok(!facade.setVision(true).isEmpty(),"missing projector rejected by Android adapter before native launch");
   facade.chooseVision();ok(Transfers.assetIndex()==2&&Transfers.options().modelIndex==1&&a.picks==1,"projector picker changes transfer identity, not active language model");
   facade.downloadVision();ok(a.services==1&&Transfers.isBusy(),"projector download reserves existing foreground service");ok(!facade.deleteAsset(0).isEmpty(),"queued transfer blocks model deletion");ok(new File(a.ext,ModelCatalog.get(0).name).exists(),"blocked deletion leaves PTQ untouched");Transfers.begin(a);Transfers.end();
   ready(a,2);ok(facade.setVision(true).isEmpty(),"verified mmproj enables actual native vision option");ok(b.status().contains("\"vision\":true"),"Java vision choice reaches JNI");
   ok(facade.deleteAsset(0).isEmpty()&&!new File(a.ext,ModelCatalog.get(0).name).exists(),"deletion removes only PTQ through real facade");ok(new File(a.ext,ModelCatalog.get(1).name).length()==7206168928L&&new File(a.ext,ModelCatalog.get(2).name).length()==629246976L,"PQ and projector survive PTQ removal");
   ok(facade.deleteAsset(2).isEmpty()&&!new File(a.ext,ModelCatalog.get(2).name).exists()&&!b.status().contains("\"vision\":true"),"deleting projector disables vision without removing PQ");ok(Transfers.options().modelIndex==1&&Transfers.readyFile(),"language selection remains ready after auxiliary deletion");
   ok(facade.configureWeb(1,"duckduckgo","",false).isEmpty(),"keyless search permission persists without Android Keystore access");String conf=Files.readString(new File(a.files,"extensions.properties").toPath());ok(conf.contains("mode=1")&&conf.contains("provider=duckduckgo"),"web configuration stored atomically alongside model state");
   String page=Extensions.bootstrapHtml();ok(page.contains("\"mode\":1")&&page.contains("Bonsai Web"),"saved web mode drives actual chat bootstrap");
   String endpoint=page.substring(page.indexOf("\"url\":\"")+7);endpoint=endpoint.substring(0,endpoint.indexOf('"'));ok(Extensions.ownEndpoint(endpoint)&&client.shouldInterceptRequest(v,new R(endpoint,false,false))==null,"only authenticated app-created MCP endpoint allowed by request filter");
   ok(!facade.configureWeb(2,"brave","",false).isEmpty(),"Brave with no key rejected instead of pretending search works");
   ok(!facade.configureWeb(2,"brave","sensitive-fixture",false).isEmpty(),"unavailable host Android Keystore fails closed with no plaintext fallback");
   ok(!Files.readString(new File(a.files,"extensions.properties").toPath()).contains("sensitive-fixture")&&!Extensions.status().contains("sensitive-fixture"),"failed key encryption never writes/exposes the supplied secret");
   ok(facade.configureWeb(0,"duckduckgo","",true).isEmpty(),"explicit web Off retained without changing PQ files");
   b.selfTest();ok(!facade.deleteAsset(1).isEmpty()&&new File(a.ext,ModelCatalog.get(1).name).exists(),"queued native command blocks deletion of selected model");
  }finally{Bridge.shutdown();try(java.util.stream.Stream<Path> paths=Files.walk(p)){paths.sorted(Comparator.reverseOrder()).forEach(f->{try{Files.delete(f);}catch(IOException ignored){}});}}
  System.out.println("ANDROID BOUNDARY CHECKS "+n+"; actual Java/JNI logic, Android API doubles and sparse GGUF fixtures, no model inference.");
 }
}
