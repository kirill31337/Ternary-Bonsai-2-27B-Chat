package com.prismml.bonsailocal.repair;
import android.content.Context;import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Application-owned extension settings. Never exposes the API key to chat/JavaScript/logs. */
public final class Extensions {
 private static File file;private static volatile int mode;private static volatile boolean vision;
 private static volatile String provider="duckduckgo",secret="",sealed="",error="",test="";
 private static McpServer server;private static WebTools tools;private static final AtomicBoolean checking=new AtomicBoolean();
 private static final WebTools.Settings SETTINGS=new WebTools.Settings(){public boolean enabled(){return mode!=0;}public String provider(){return provider;}public String key(){return secret;}};
 private Extensions(){}
 public static synchronized void configure(Context c){if(file!=null)return;file=new File(c.getFilesDir(),"extensions.properties");Properties p=new Properties();try{if(file.isFile()&&file.length()<8192)try(FileInputStream in=new FileInputStream(file)){p.load(in);}mode=Integer.parseInt(p.getProperty("mode","0"));if(mode<0||mode>2)mode=0;provider=p.getProperty("provider","duckduckgo");if(!provider.equals("brave"))provider="duckduckgo";vision=Boolean.parseBoolean(p.getProperty("vision","false"));sealed=p.getProperty("keyCipher","");secret=KeyVault.decrypt(sealed);}catch(Exception e){mode=0;secret="";error="Не удалось прочитать настройки/ключ. Повторно сохраните настройки интернета.";}
  tools=new WebTools(SETTINGS);try{server=new McpServer(tools,SETTINGS);}catch(IOException e){error="Не удалось запустить локальный веб-модуль. Перезапустите приложение.";}
 }
 private static void save(int m,String p,boolean v,String cipher)throws IOException {Properties props=new Properties();props.setProperty("mode",""+m);props.setProperty("provider",p);props.setProperty("vision",""+v);props.setProperty("keyCipher",cipher);File tmp=new File(file.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){props.store(out,"Bonsai extension settings; key encrypted with Android Keystore");out.getFD().sync();}try{Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}}
 public static synchronized String setWeb(int next,String which,String key,boolean clear){
  try{if(next<0||next>2||!(which.equals("brave")||which.equals("duckduckgo")))return "Недопустимые настройки";if(checking.get()&&next!=0)return "Дождитесь окончания проверки сети.";
   String nextSecret=clear?"":secret,nextCipher=clear?"":sealed;
   if(!key.isEmpty()){if(!key.matches("[!-~]{1,512}"))return "Некорректный API-ключ";nextCipher=KeyVault.encrypt(key);nextSecret=key;}
   if(next!=0&&which.equals("brave")&&nextSecret.isEmpty())return "Введите API-ключ Brave или выберите DuckDuckGo без ключа.";
   if(next!=0&&server==null)return "Локальный модуль не запущен. Перезапустите приложение.";
   save(next,which,vision,nextCipher);provider=which;sealed=nextCipher;secret=nextSecret;mode=next;error="";return "";
  }catch(Exception e){return "Не удалось сохранить настройки/защитить ключ в Android Keystore. Ключ не сохранён.";}
 }
 public static synchronized String setVision(boolean next,Bridge bridge){
  if(Transfers.isBusy()||LocalBenchmark.isBusy())return "Дождитесь завершения передачи/замера.";
  if(next&&!ModelCatalog.get(2).ready(Transfers.externalDir(),Transfers.internalDir()))return "Сначала скачайте или импортируйте mmproj и дождитесь проверки SHA-256.";
  if(!bridge.configureVision(next))return "Сначала остановите движок.";
  try{save(mode,provider,next,sealed);vision=next;return "";}catch(IOException e){bridge.configureVision(vision);return "Не удалось сохранить переключатель изображений.";}
 }
 public static boolean prepare(Bridge b){boolean available=ModelCatalog.get(2).ready(Transfers.externalDir(),Transfers.internalDir());if(vision&&!available){error="Включён модуль изображений, но проверенный mmproj не найден. Скачайте модуль или выключите изображения.";return false;}return b.configureVision(vision&&available);}
 public static synchronized String deleteAsset(int id,Bridge bridge){try{
   String state=bridge.status();boolean locked=state.contains("\"alive\":true")||Transfers.isBusy()||LocalBenchmark.isBusy()||state.contains("\"pending\":1")||state.contains("\"pending\":2")||state.contains("\"pending\":3")||state.contains("\"state\":1,")||state.contains("\"state\":2,")||state.contains("\"state\":3,")||state.contains("\"state\":6,");
   if(locked)return "Сначала остановите движок и дождитесь завершения передачи/замера.";
   if(id==2){String s=setVision(false,bridge);if(!s.isEmpty())return s;}
   ModelFiles.delete(Transfers.externalDir(),Transfers.internalDir(),id,false);Transfers.resetAfterDelete();bridge.configureOptions(Transfers.options().modelIndex,Transfers.options().ctxSize,Transfers.options().kvQ4,Transfers.options().threads,Transfers.options().batchThreads,Transfers.options().thinking);return "";
  }catch(Exception e){return "Удаление не завершено: "+e.getMessage();}}
 public static String status(){List<Object> files=new ArrayList<>();for(int i=0;i<3;i++){ModelCatalog.Model m=ModelCatalog.get(i);long bytes=0;String problem="";try{bytes=ModelFiles.bytes(Transfers.externalDir(),Transfers.internalDir(),i);}catch(IOException e){problem=e.getMessage();}files.add(MiniJson.map("index",i,"name",m.name,"size",m.size,"bytes",bytes,"ready",m.ready(Transfers.externalDir(),Transfers.internalDir()),"error",problem));}
  return MiniJson.write(MiniJson.map("vision",vision,"mode",mode,"provider",provider,"hasKey",!secret.isEmpty(),"webReady",server!=null,"error",error,"test",test,"testing",checking.get(),"lastTool",server==null?"":server.last(),"files",files));
 }
 public static String bootstrapHtml(){if(server==null)return "<!doctype html><meta charset='utf-8'><p>Веб-модуль недоступен. Вернитесь и перезапустите приложение.</p>";return ChatBootstrap.html(server.url(),mode);}
 public static boolean ownEndpoint(String url){return server!=null&&server.url().equals(url);}
 public static void test(){if(!checking.compareAndSet(false,true))return;test="Проверка реального интернет-запроса…";Thread worker=new Thread(new Runnable(){public void run(){try{Object result=tools.search("PrismML Bonsai",2);test=MiniJson.write(result);}catch(Exception e){test="Ошибка сети/провайдера: "+(e instanceof IOException?e.getMessage():"Некорректный ответ сервера");}finally{checking.set(false);}}},"Bonsai-WebCheck");worker.setDaemon(true);worker.start();}
}
