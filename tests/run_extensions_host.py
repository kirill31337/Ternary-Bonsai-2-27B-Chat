#!/usr/bin/env python3
"""All extension host checks; none substitutes for an Android device/emulator or real model."""
from pathlib import Path
import runpy,shutil,subprocess,os
R=Path(__file__).resolve().parents[1];os.chdir(R);runpy.run_path(str(R/'make_api_stubs.py'))
d=R/'build/extensions-host';shutil.rmtree(d,ignore_errors=True);d.mkdir(parents=True);shutil.copytree(R/'build/api-signatures',d/'src',dirs_exist_ok=True)
P='com/prismml/bonsailocal/repair/'
for f in (R/'java').rglob('*.java'):
 if f.stem in ('RuntimeEnvironment','RuntimeService','RuntimeSnapshot','UiSession'):continue # tested in dedicated lifecycle harnesses
 p=d/'src'/f.relative_to(R/'java');p.parent.mkdir(parents=True,exist_ok=True);shutil.copy(f,p)
for f in (R/'tests/extensions').rglob('*.java'):p=d/'src'/f.relative_to(R/'tests/extensions');p.parent.mkdir(parents=True,exist_ok=True);shutil.copy(f,p)
shutil.copy(R/'tests/jvm'/P/'Bridge.java',d/'src'/P)
def put(name,body):
 p=d/'src'/(name+'.java');p.parent.mkdir(parents=True,exist_ok=True);p.write_text('package '+name.rsplit('/',1)[0].replace('/','.')+';\n'+body)
put('com/prismml/bonsailocal/repair/RuntimeService', '''public class RuntimeService {public static final String START="start",STOP="stop",TEST="test";public static String error(){return "";}public static void request(android.content.Context c,String action){}}''')
put('android/net/Uri', '''public class Uri {final String s;Uri(String u){s=u;}public static Uri parse(String s){return new Uri(s);}public String getScheme(){try{return new java.net.URI(s).getScheme();}catch(Exception e){return null;}}public String toString(){return s;}}''')
put('android/content/Intent', '''public class Intent {public static final int FLAG_GRANT_READ_URI_PERMISSION=1,FLAG_ACTIVITY_NEW_TASK=268435456;public static final String CATEGORY_OPENABLE="android.intent.category.OPENABLE",ACTION_VIEW="android.intent.action.VIEW";public android.net.Uri[] uris;public int flags;public String category;private String action;private android.net.Uri data;public Intent(String a,android.net.Uri d){action=a;data=d;}public Intent(Context c,Class<?> x){}public Intent addCategory(String s){category=s;return this;}public Intent addFlags(int f){flags|=f;return this;}public Intent setAction(String a){action=a;return this;}public String getAction(){return action;}public Intent setData(android.net.Uri u){data=u;return this;}public android.net.Uri getData(){return data;}}''')
put('android/app/Activity', '''public class Activity extends android.content.Context {public int pickerCalls,requestCode,externalCalls;public android.content.Intent lastIntent;public void startActivityForResult(android.content.Intent i,int request){pickerCalls++;requestCode=request;lastIntent=i;}public final void runOnUiThread(Runnable r){r.run();}public void startActivity(android.content.Intent i){externalCalls++;}}''')
put('android/webkit/WebView', '''public class WebView {public String url;public android.content.Context context;public java.util.List<String> removed=new java.util.ArrayList<>();public String getUrl(){return url;}public android.content.Context getContext(){return context;}public void removeJavascriptInterface(String name){removed.add(name);}}''')
put('android/webkit/WebResourceResponse', '''public class WebResourceResponse {public int code;public java.io.InputStream body;public WebResourceResponse(String m,String e,java.io.InputStream i){code=200;body=i;}public WebResourceResponse(String m,String e,int c,String r,java.util.Map<String,String> h,java.io.InputStream i){code=c;body=i;}}''')
put('android/webkit/WebChromeClient', '''public class WebChromeClient {public boolean onShowFileChooser(WebView w,ValueCallback<android.net.Uri[]> c,FileChooserParams p){return false;}public abstract static class FileChooserParams{public abstract android.content.Intent createIntent();public static android.net.Uri[] parseResult(int r,android.content.Intent i){return r==-1&&i!=null?i.uris:null;}}}''')
cl=d/'classes';cl.mkdir()
subprocess.run(['javac','-d',str(cl)]+[str(p) for p in (d/'src').rglob('*.java')],check=True)
tests=['BoundaryTest'] if os.environ.get('BOUNDARY_ONLY') else ['ModulesPresentTest','AssetsTest','WebTest','CryptoTest','BoundaryTest']
fixture=d/'tls-fixture.p12'
if 'CryptoTest' in tests:
 subprocess.run(['keytool','-genkeypair','-alias','fixture','-keyalg','RSA','-keysize','2048','-validity','2','-dname','CN=localhost','-ext','SAN=dns:localhost','-keystore',str(fixture),'-storetype','PKCS12','-storepass','fixture-pass','-keypass','fixture-pass','-noprompt'],check=True,stdout=subprocess.DEVNULL)
for t in tests:
 print('== '+t+' ==',flush=True)
 subprocess.run(['java','-Xcheck:jni','-Dbonsai.lib='+str(R/'build/libbonsai_host.so'),'-cp',str(cl),'com.prismml.bonsailocal.repair.'+t]+([str(fixture)] if t=='CryptoTest' else []),check=True)
if not os.environ.get('BOUNDARY_ONLY'):
 subprocess.run(['node','tests/bootstrap_test.cjs'],check=True)
 subprocess.run(['node','tests/ui_extensions_test.cjs'],check=True)
