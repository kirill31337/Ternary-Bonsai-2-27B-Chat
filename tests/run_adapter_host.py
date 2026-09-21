#!/usr/bin/env python3
"""Real Transfers/RuntimeOptions/ModelCatalog and native JNI, with Android Context boundary double.
No emulator, no real Android services or real large GGUF weights.
"""
from pathlib import Path
import runpy,subprocess,shutil
R=Path(__file__).resolve().parents[1];runpy.run_path(str(R/'make_api_stubs.py'))
d=R/'build/adapter-host';shutil.rmtree(d,ignore_errors=True);d.mkdir(parents=True)
shutil.copytree(R/'build/api-signatures',d/'src')
P='com/prismml/bonsailocal/repair/'
for name in ['TransferEngine','Transfers','RuntimeOptions','ModelCatalog','TransferService']:
 shutil.copy(R/'java'/P/(name+'.java'),d/'src'/P)
shutil.copy(R/'tests/jvm'/P/'Bridge.java',d/'src'/P)
(d/'src'/P/'AdapterHostTest.java').write_text('''package com.prismml.bonsailocal.repair;
import java.io.*;import java.nio.file.*;
public final class AdapterHostTest {
 static int n;static void ok(boolean b,String s){if(!b)throw new AssertionError(s);n++;System.out.println("PASS "+s);}
 static class C extends android.content.Context {
  final File ext,files;int starts;boolean deny;C(Path p)throws Exception{ext=Files.createDirectory(p.resolve("ext")).toFile();files=Files.createDirectory(p.resolve("files")).toFile();}
  public android.content.Context getApplicationContext(){return this;}public File getFilesDir(){return files;}public File getExternalFilesDir(String s){return ext;}
  public android.content.ComponentName startForegroundService(android.content.Intent i){if(deny)throw new SecurityException("permission denied fixture");starts++;return new android.content.ComponentName();}
 }
 static void sparse(File f,long size)throws Exception{try(RandomAccessFile r=new RandomAccessFile(f,"rw")){r.writeBytes("GGUF");r.setLength(size);}}
 public static void main(String[]args)throws Exception {
  Path p=Files.createTempDirectory("bonsai-adapter-");C c=new C(p);Path libs=Files.createDirectory(p.resolve("lib"));Bridge b=new Bridge();
  ModelCatalog.Model pt=ModelCatalog.get(0),pq=ModelCatalog.get(1);sparse(new File(c.ext,pt.name),pt.size);Files.writeString(new File(c.files,pt.marker).toPath(),pt.sha+"\\n");
  Bridge.init(c,c.ext.toString(),libs.toString(),c.files.toString());
  try {
   Transfers.configure(c);ok(Transfers.options().modelIndex==0&&Transfers.readyFile(),"upgrade detects existing PTQ, no redownload");
   ok(Transfers.restoreRuntime(b),"persisted format restored to native engine");
   ok(Transfers.updateOptions(RuntimeOptions.defaults(1),b),"model selector switches to PQ2");
   ok(Transfers.json().contains("7206168928")&&!Transfers.readyFile(),"active downloader uses PQ2 size and readiness");
   ok(b.status().contains("\\"modelIndex\\":1"),"Java selection reaches JNI");
   ok(new File(c.ext,pt.name).length()==pt.size&&new File(c.files,pt.marker).exists(),"switch retains old verified PTQ file and marker");
   Transfers.request(c,Transfers.DOWNLOAD,null);ok(c.starts==1&&Transfers.isBusy(),"foreground request reserves busy state before service starts");
   ok(!Transfers.updateOptions(RuntimeOptions.defaults(0),b),"pending service cannot be retargeted to another model");
   Transfers.request(c,Transfers.DOWNLOAD,null);ok(c.starts==1,"duplicate pending request ignored");
   ok(Transfers.begin(c)&&!Transfers.updateOptions(RuntimeOptions.defaults(0),b),"running transfer cannot be retargeted");Transfers.end();
   c.deny=true;try{Transfers.request(c,Transfers.DOWNLOAD,null);throw new AssertionError("expected permission failure");}catch(SecurityException expected){}
   ok(!Transfers.isBusy(),"failed foreground request releases reservation");c.deny=false;
   sparse(new File(c.ext,pq.name),pq.size);Files.writeString(new File(c.files,pq.marker).toPath(),pq.sha+"\\n");ok(Transfers.readyFile()&&Transfers.modelsJson().contains("\\"ready\\":true"),"selected PQ2 recognized alongside PTQ");
   File prefs=new File(c.files,"runtime-options.properties");Files.delete(prefs.toPath());Files.createDirectory(prefs.toPath());Files.writeString(prefs.toPath().resolve("occupied"),"fixture");
   boolean failed=false;try{Transfers.updateOptions(new RuntimeOptions(0,32768,true,6,6,1),b);}catch(IOException expected){failed=true;}
   ok(failed&&Transfers.options().modelIndex==1&&b.status().contains("\\"modelIndex\\":1"),"disk persistence failure rolls Java and native selection back");
   Files.delete(prefs.toPath().resolve("occupied"));Files.delete(prefs.toPath());
   ok(Transfers.updateOptions(new RuntimeOptions(1,8192,false,6,8,0),b),"small context and manual tuning accepted after recovery");
   RuntimeOptions loaded=RuntimeOptions.load(prefs,0);ok(loaded.modelIndex==1&&loaded.ctxSize==8192&&loaded.threads==6,"settings survive disk reload");
   b.selfTest();ok(!Transfers.updateOptions(RuntimeOptions.defaults(0),b),"queued runtime job blocks changing active model");
   System.out.println(n+" real adapter/JNI checks passed; Android Context is a boundary double.");
  } finally {Bridge.shutdown();}
 }
}''')
classes=d/'classes';classes.mkdir()
subprocess.run(['javac','-d',str(classes)]+[str(p) for p in (d/'src').rglob('*.java')],check=True)
subprocess.run(['java','-Xcheck:jni','-Dbonsai.lib='+str(R/'build/libbonsai_host.so'),'-cp',str(classes),'com.prismml.bonsailocal.repair.AdapterHostTest'],check=True)
