package com.prismml.bonsailocal.repair;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.app.DownloadManager;
import java.io.*;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Process-wide transfer state, independent of Activity/WebView lifetime. */
public final class Transfers {
 public static final String DOWNLOAD="com.prismml.bonsailocal.DOWNLOAD", IMPORT="com.prismml.bonsailocal.IMPORT", PAUSE="com.prismml.bonsailocal.PAUSE";
 // Legacy constants remain for source compatibility; active identity comes from ModelCatalog.
 public static final String NAME="Ternary-Bonsai-2-27B-PTQ1_0.gguf";
 private static Context app;private static File external,internal;private static volatile TransferEngine engine;
 private static volatile RuntimeOptions options;private static int assetIndex;
 private static final AtomicBoolean busy=new AtomicBoolean(),pending=new AtomicBoolean();private static volatile String failure="";
 private Transfers(){}
 public static synchronized void configure(Context c){
  if(app!=null)return;
  Context a=c.getApplicationContext();File ext=a.getExternalFilesDir(null);
  if(ext==null)throw new IllegalStateException("Хранилище модели недоступно. Разблокируйте телефон и повторите.");
  external=ext;internal=a.getFilesDir();app=a;
  int fallback=(ModelCatalog.get(0).ready(external,internal)||new File(external,NAME+".resume").exists())?0:1;
  options=RuntimeOptions.load(new File(internal,"runtime-options.properties"),fallback);assetIndex=options.modelIndex;engine=fresh();
 }
 private static TransferEngine fresh(){ModelCatalog.Model m=ModelCatalog.get(assetIndex);return new TransferEngine(new File(external,m.name),new File(internal,m.marker),new TransferEngine.Config(m.url,m.size,m.sha,8));}
 public static RuntimeOptions options(){return options;}
 public static File internalDir(){return internal;}
 public static File externalDir(){return external;}
 public static synchronized boolean selectAsset(int id){ModelCatalog.get(id);if(isBusy())return false;assetIndex=id;engine=fresh();failure="";return true;}
 public static synchronized void resetAfterDelete(){if(isBusy())throw new IllegalStateException("Передача ещё идёт");assetIndex=options.modelIndex;engine=fresh();failure="";}
 public static int assetIndex(){return assetIndex;}
 private static boolean applyNative(Bridge b,RuntimeOptions o){return b.configureOptions(o.modelIndex,o.ctxSize,o.kvQ4,o.threads,o.batchThreads,o.thinking);}
 public static synchronized boolean restoreRuntime(Bridge b){return applyNative(b,options);}
 public static synchronized boolean updateOptions(RuntimeOptions next,Bridge bridge)throws IOException{
  if(isBusy())return false;
  RuntimeOptions old=options;if(!applyNative(bridge,next))return false;
  try{next.save(new File(internal,"runtime-options.properties"));}catch(IOException ex){applyNative(bridge,old);throw ex;}
  options=next;if(old.modelIndex!=next.modelIndex){assetIndex=next.modelIndex;engine=fresh();failure="";}
  return true;
 }
 public static String modelsJson(){if(options==null)return "[]";StringBuilder s=new StringBuilder("[");for(int i=0;i<2;i++){ModelCatalog.Model m=ModelCatalog.get(i);if(i>0)s.append(',');s.append("{\"index\":").append(i).append(",\"quant\":").append(quote(m.quant)).append(",\"name\":").append(quote(m.name)).append(",\"size\":").append(m.size).append(",\"ready\":").append(m.ready(external,internal)).append('}');}return s.append(']').toString();}
 public static boolean isBusy(){return busy.get()||pending.get();}
 public static synchronized boolean begin(Context c){configure(c);if(!busy.compareAndSet(false,true))return false;pending.set(false);failure="";engine=fresh();return true;}
 public static void end(){busy.set(false);pending.set(false);}
 public static void pause(){TransferEngine e=engine;if(e!=null)e.pause();}
 public static synchronized void request(Context c,String action,Uri uri){
  configure(c);if(isBusy())return;pending.set(true);
  Intent i=new Intent(c,TransferService.class).setAction(action);
  if(uri!=null)i.setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  try{c.startForegroundService(i);}catch(RuntimeException ex){pending.set(false);throw ex;}
 }
 public static boolean readyFile(){return options!=null&&ModelCatalog.get(options.modelIndex).ready(external,internal);}
 /** Called only after an explicit user request. Never cancels a browser's download. */
 public static void cancelLegacy()throws IOException{
  File id=new File(internal,"download.id");if(!id.exists())return;
  long n=-1;try(BufferedReader r=new BufferedReader(new FileReader(id))){n=Long.parseLong(r.readLine().trim());}catch(NumberFormatException ignored){}
  if(n>=0&&!new File(internal,"model.complete").isFile()){
   DownloadManager dm=(DownloadManager)app.getSystemService(Context.DOWNLOAD_SERVICE);
   if(dm==null)throw new IOException("Системная загрузка недоступна. Повторите после перезапуска приложения.");
   dm.remove(n);log("Legacy DownloadManager job cancelled on explicit transfer selection; old partial is not migrated.");
  }
  try(FileOutputStream f=new FileOutputStream(id)){f.write("-1".getBytes("UTF-8"));f.getFD().sync();}
  refreshNative();
 }
 public static void perform(String action,Uri uri)throws IOException{
  cancelLegacy();
  if(DOWNLOAD.equals(action)){engine.download();}
  else if(IMPORT.equals(action)){
   if(uri==null)throw new IOException("Файл не выбран");
   InputStream in=app.getContentResolver().openInputStream(uri);
   if(in==null)throw new IOException("Файловый менеджер не открыл файл");
   engine.importFrom(in);
  }else throw new IOException("Неизвестная операция");
  log("Transfer completed; SHA-256 matched pinned "+ModelCatalog.get(assetIndex).quant+" model. No automatic inference.");refreshNative();
 }
 public static void release(Uri uri){if(uri!=null&&app!=null)try{app.getContentResolver().releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(RuntimeException ignored){}}
 private static void refreshNative(){try{new Bridge().stop();}catch(UnsatisfiedLinkError ignored){/* Import/download also works before JNI initialization. */}}
 public static void fail(Throwable e){
  TransferEngine x=engine;String phase=x==null?"":x.snapshot().state;
  if(!phase.equals("paused")){failure=e.toString();log(failure);}
 }
 private static void log(String text){if(internal==null)return;File file=new File(internal,"transfer.log");try(FileOutputStream out=new FileOutputStream(file,file.length()<256*1024)){out.write((text+"\n").getBytes("UTF-8"));}catch(IOException ignored){}}
 public static String json(){TransferEngine e=engine;if(e==null)return "{\"state\":\"idle\",\"busy\":false}";
  TransferEngine.Snapshot s=e.snapshot();String err=failure.length()>0?failure:s.error;
  return "{\"assetIndex\":"+assetIndex+",\"state\":"+quote(failure.length()>0?"error":s.state)+",\"busy\":"+isBusy()+",\"done\":"+s.done+",\"total\":"+s.total+",\"speed\":"+s.speed+",\"connections\":"+s.connections+",\"resumable\":"+s.resumable+",\"error\":"+quote(err)+"}";
 }
 public static String describe(){
  TransferEngine e=engine;if(e==null)return "Подготовка передачи";TransferEngine.Snapshot s=e.snapshot();
  String p=s.state.equals("import")?"Импорт файла":s.state.equals("verify")?"Проверка SHA-256":s.state.equals("ready")?"Модель готова":s.state.equals("paused")?"Приостановлено":"Скачивание";
  if(failure.length()>0||s.state.equals("error"))return "Ошибка. Откройте приложение для подробностей.";
  return p+" · "+(100*s.done/s.total)+"% · "+String.format(Locale.US,"%.1f",s.speed/1e6)+" МБ/с"+(s.state.equals("download")?" · "+s.connections+" соединений":"");
 }
 public static int percent(){TransferEngine e=engine;if(e==null)return 0;TransferEngine.Snapshot s=e.snapshot();return (int)Math.min(100,100*s.done/s.total);}
 public static String quote(String s){if(s==null)s="";StringBuilder o=new StringBuilder("\"");for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'||c=='\\')o.append('\\').append(c);else if(c<32)o.append(String.format(Locale.US,"\\u%04x",(int)c));else o.append(c);}return o.append('"').toString();}
}
