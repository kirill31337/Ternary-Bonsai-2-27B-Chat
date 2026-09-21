#!/usr/bin/env python3
"""Execute production session retention with Android boundary doubles; not a device test."""
from pathlib import Path
import subprocess, shutil

ROOT = Path(__file__).resolve().parents[1]
PKG = 'com/prismml/bonsailocal/repair'
for name in ('UiSession', 'RuntimeSnapshot'):
    assert (ROOT/'java'/PKG/(name+'.java')).exists(), f'Missing background behavior: {name}'
BUILD = ROOT/'build/background-host'
shutil.rmtree(BUILD, ignore_errors=True)
SRC = BUILD/'src'

def put(name, body):
    path = SRC/(name+'.java'); path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('package '+name.rsplit('/', 1)[0].replace('/', '.')+';\n'+body)

put('android/content/Context', '''public class Context {public Context getApplicationContext(){return this;}}''')
put('android/content/MutableContextWrapper', '''public class MutableContextWrapper extends Context {public Context base;public MutableContextWrapper(Context c){base=c;}public void setBaseContext(Context c){base=c;}}''')
put('android/os/Build', '''public class Build {public static class VERSION {public static int SDK_INT=35;}}''')
put('android/view/ViewParent', 'public interface ViewParent {}')
put('android/view/View', '''public class View {public ViewParent parent; public ViewParent getParent(){return parent;}}''')
put('android/view/ViewGroup', '''public class ViewGroup extends View implements ViewParent {public void removeView(View v){v.parent=null;}}''')
put('android/webkit/WebSettings', '''public class WebSettings {public void setJavaScriptEnabled(boolean b){}public void setDomStorageEnabled(boolean b){}public void setAllowFileAccessFromFileURLs(boolean b){}public void setAllowUniversalAccessFromFileURLs(boolean b){}public void setAllowContentAccess(boolean b){}}''')
put('android/webkit/WebView', '''public class WebView extends android.view.View {public static final int RENDERER_PRIORITY_IMPORTANT=2;public static int created;public int loads;public String url;public boolean destroyed,bridged;public android.content.Context context;public WebView(android.content.Context c){context=c;created++;}public WebSettings getSettings(){return new WebSettings();}public void setWebViewClient(Object o){}public void setWebChromeClient(Object o){}public void addJavascriptInterface(Object o,String n){bridged=true;}public void removeJavascriptInterface(String n){}public void loadUrl(String u){url=u;loads++;}public String getUrl(){return url;}public void setRendererPriorityPolicy(int p,boolean waived){}public void onResume(){}public void destroy(){destroyed=true;}}''')
put(PKG+'/MainActivity', '''public class MainActivity extends android.content.Context {public android.webkit.WebView view;public void setContentView(android.view.View v){if(v.parent!=null)throw new IllegalStateException("still attached");view=(android.webkit.WebView)v;v.parent=new android.view.ViewGroup();}public void runOnUiThread(Runnable r){r.run();}}''')
put(PKG+'/TransferFacade', '''public class TransferFacade {public MainActivity owner;public TransferFacade(MainActivity a){owner=a;}public void attach(MainActivity a){owner=a;}}''')
put(PKG+'/BonsaiChromeClient', '''public class BonsaiChromeClient {public MainActivity owner;public BonsaiChromeClient(MainActivity a){owner=a;}public void attach(MainActivity a){owner=a;}public static void cancel(MainActivity a){}}''')
put(PKG+'/Extensions', 'public class Extensions {public static String setup="disabled";public static String bootstrapHtml(){return setup;}}')
put(PKG+'/LocalWebClient', 'public class LocalWebClient {public interface ChatNavigation{void open(String url);}public LocalWebClient(){}public LocalWebClient(ChatNavigation n){}public static boolean chatUrl(String u){return u.startsWith("http://127.0.0.1:18080/");}}')
put(PKG+'/BackgroundHostTest', '''public class BackgroundHostTest {
 static int n;static void ok(boolean b,String message){if(!b)throw new AssertionError(message);n++;}
 public static void main(String[] args){
  MainActivity first=new MainActivity();android.webkit.WebView management=UiSession.open(first);
  UiSession.showChat(first,"http://127.0.0.1:18080/bonsai-connect");android.webkit.WebView page=first.view;
  ok(management!=page&&management.bridged&&!page.bridged,"Chat must never share the privileged management document");
  page.loadUrl("http://127.0.0.1:18080/#/chat/existing-conversation");int loads=page.loads;
  UiSession.detach(first);ok(!page.destroyed,"Activity destruction destroyed active chat");
  MainActivity second=new MainActivity();android.webkit.WebView resumed=UiSession.open(second);
  ok(resumed==page,"Recreation must reuse the same chat document");ok(page.loads==loads,"Recreation reloaded/cancelled the chat stream");
  ok(page.url.endsWith("existing-conversation"),"Conversation route lost");
  UiSession.detach(first);ok(page.getParent()!=null,"Late destruction detached replacement Activity");
  UiSession.detach(second);ok(page.getParent()==null,"Destroyed owner still retains the view");
  ok(((android.content.MutableContextWrapper)page.context).base!=second,"Destroyed Activity leaked through WebView context");
  UiSession.open(second);
  ok(UiSession.back(second)&&second.view==management,"Back from chat must expose model controls");
  ok(!UiSession.back(second),"Back from management should background the task");
  UiSession.showChat(second,"http://127.0.0.1:18080/bonsai-connect");
  ok(second.view==page&&page.loads==loads,"Returning from controls must retain the conversation/stream");
  UiSession.back(second);UiSession.detach(second);MainActivity third=new MainActivity();
  ok(UiSession.open(third)==management,"Recreation should restore the controls when they were selected");
  UiSession.showChat(third,"http://127.0.0.1:18080/bonsai-connect");
  ok(third.view==page&&!page.bridged,"Restarting from controls must not grant chat the management bridge");
  UiSession.back(third);Extensions.setup="ask-per-call";
  UiSession.showChat(third,"http://127.0.0.1:18080/bonsai-connect");
  ok(page.loads==loads+1&&!page.bridged,"Changed tool consent must refresh bootstrap only on explicit chat opening");
  UiSession.detach(third);
  ok(((android.content.MutableContextWrapper)management.context).base!=third&&((android.content.MutableContextWrapper)page.context).base!=third,"Either retained view leaked destroyed Activity");
  ok(android.webkit.WebView.created==2,"Duplicate management or chat WebView created");
  ok(RuntimeSnapshot.parse("{\\"state\\":2,\\"pending\\":0,\\"alive\\":false}").active(),"Loading lost its service");
  ok(RuntimeSnapshot.parse("{\\"state\\":3,\\"pending\\":0,\\"alive\\":true}").active(),"Ready chat lost its service");
  ok(RuntimeSnapshot.parse("{\\"state\\":5,\\"pending\\":2,\\"alive\\":false}").active(),"Pending stop released protection too early");
  ok(RuntimeSnapshot.parse("{\\"state\\":4,\\"pending\\":0,\\"alive\\":true}").active(),"Live failed process lost protection");
  ok(!RuntimeSnapshot.parse("{\\"state\\":5,\\"pending\\":0,\\"alive\\":false}").active(),"Idle model leaked service");
  ok(!RuntimeSnapshot.parse("{\\"state\\":4,\\"pending\\":0,\\"alive\\":false}").active(),"Terminated runtime leaked service");
  System.out.println(n+" background lifecycle assertions passed; Android boundaries simulated");
 }
}''')
for name in ('UiSession', 'RuntimeSnapshot', 'MiniJson'):
    shutil.copy(ROOT/'java'/PKG/(name+'.java'), SRC/PKG)
classes=BUILD/'classes';classes.mkdir(parents=True)
subprocess.run(['javac','--release','8','-d',str(classes)]+[str(p) for p in SRC.rglob('*.java')],check=True)
subprocess.run(['java','-cp',str(classes),'com.prismml.bonsailocal.repair.BackgroundHostTest'],check=True)
