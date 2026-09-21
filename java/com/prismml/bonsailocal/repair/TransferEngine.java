package com.prismml.bonsailocal.repair;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

/** Android-independent transfer implementation. Tested against real local HTTP sockets.
 * Only a verified file is promoted to target. User input streams are never modified.
 * Checkpoints snapshot completed writes BEFORE forcing data and persisting offsets.
 */
public final class TransferEngine {
 public static final class Config {
  public final String url,sha256; public final long size; public final int connections;
  public Config(String url,long size,String sha256,int connections){
   if(url==null||size<4||sha256==null||!sha256.matches("[0-9a-f]{64}")||connections<1||connections>8)throw new IllegalArgumentException("Invalid transfer configuration");
   this.url=url;this.size=size;this.sha256=sha256;this.connections=connections;
  }
 }
 public static final class Snapshot {
  public final String state,error; public final long done,total,speed; public final int connections; public final boolean resumable;
  Snapshot(String s,String e,long d,long t,long b,int c,boolean r){state=s;error=e;done=d;total=t;speed=b;connections=c;resumable=r;}
 }
 private static final Pattern CONTENT_RANGE=Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");
 private final Config cfg; private final File target,marker,partial,metadata,importPartial;
 private final AtomicBoolean running=new AtomicBoolean(); private volatile boolean paused;
 private volatile String state="idle",error=""; private volatile int count;
 private volatile AtomicLongArray offsets; private volatile long localProgress;
 private final AtomicLong traffic=new AtomicLong(); private long sampleTime=System.nanoTime(),sampleBytes,speed;
 private final Set<HttpURLConnection> connections=Collections.newSetFromMap(new ConcurrentHashMap<HttpURLConnection,Boolean>());
 private volatile InputStream importInput;
 public TransferEngine(File target,File marker,Config config){
  this.target=target;this.marker=marker;cfg=config;count=cfg.connections;offsets=new AtomicLongArray(count);
  partial=new File(target.getPath()+".partial");metadata=new File(target.getPath()+".resume");importPartial=new File(target.getPath()+".importing");
  if(partial.isFile()&&metadata.isFile()){state="paused";try{loadCheckpoint(count);}catch(IOException ignored){}}
 }
 public synchronized Snapshot snapshot(){
  long now=System.nanoTime(),n=traffic.get(),elapsed=now-sampleTime;
  if(elapsed>=500000000L){speed=(long)((n-sampleBytes)*1e9/elapsed);sampleBytes=n;sampleTime=now;}
  long done=(state.equals("import")||state.equals("verify"))?localProgress:sum(offsets);
  if(state.equals("ready"))done=cfg.size;
  boolean active=running.get();return new Snapshot(state,error,done,cfg.size,active?speed:0,count,metadata.exists()&&partial.exists());
 }
 public void pause(){
  paused=true;
  // The notification action may run on Android's main thread. Some providers and
  // HTTP implementations block inside close/disconnect; never perform that I/O here.
  final List<HttpURLConnection> active=new ArrayList<HttpURLConnection>(connections);
  final InputStream in=importInput;
  if(active.isEmpty()&&in==null)return;
  Thread closer=new Thread(new Runnable(){public void run(){
   for(HttpURLConnection c:active)try{c.disconnect();}catch(RuntimeException ignored){}
   if(in!=null)try{in.close();}catch(IOException ignored){}catch(RuntimeException ignored){}
  }},"Bonsai-transfer-cancel");closer.setDaemon(true);closer.start();
 }
 private void checkPause()throws InterruptedIOException{if(paused||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Передача приостановлена");}
 private static long sum(AtomicLongArray a){long n=0;for(int i=0;i<a.length();i++)n+=a.get(i);return n;}
 private long begin(int i){return cfg.size*i/count;}
 private long end(int i){return cfg.size*(i+1)/count;}
 private MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static String hex(byte[] h){StringBuilder s=new StringBuilder(64);for(byte b:h){int v=b&255;s.append("0123456789abcdef".charAt(v>>4));s.append("0123456789abcdef".charAt(v&15));}return s.toString();}
 private void ensureDirs()throws IOException{
  File a=target.getParentFile(),b=marker.getParentFile();
  if(!a.isDirectory()&&!a.mkdirs())throw new IOException("Не удаётся создать папку модели");
  if(!b.isDirectory()&&!b.mkdirs())throw new IOException("Не удаётся создать папку состояния");
 }
 private void space(long needed)throws IOException{if(target.getParentFile().getUsableSpace()<Math.max(0,needed)+32L*1024*1024)throw new IOException("Недостаточно свободного места. Для новой модели/импорта нужно ещё около "+String.format(Locale.US,"%.2f",Math.max(0,needed)/1e9)+" ГБ");}
 public void download()throws IOException{
  if(!running.compareAndSet(false,true))throw new IOException("Передача уже выполняется");
  error="";
  try{
   checkPause();ensureDirs();
   if(target.isFile()&&target.length()==cfg.size&&marker.isFile()){
    verify(target);state="ready";return;
   }
   state="download";
   boolean ranged=probe();count=ranged?cfg.connections:1;offsets=new AtomicLongArray(count);
   if(ranged)loadCheckpoint(count);
   // A server without Range cannot safely resume a prefix: start one fresh stream.
   space(cfg.size-sum(offsets));
   try(RandomAccessFile out=new RandomAccessFile(partial,"rw")){
    out.setLength(cfg.size);final FileChannel channel=out.getChannel();
    checkpoint(channel);
    if(!ranged){fetchPart(channel,0,false);}else{
     ExecutorService pool=Executors.newFixedThreadPool(count);
     final AtomicReference<IOException> failure=new AtomicReference<IOException>();
     List<Future<?>> jobs=new ArrayList<Future<?>>();
     try{
      for(int i=0;i<count;i++){final int part=i;jobs.add(pool.submit(new Runnable(){public void run(){try{fetchPart(channel,part,true);}catch(IOException ex){failure.compareAndSet(null,ex);}}}));}
      while(true){boolean all=true;for(Future<?> f:jobs)if(!f.isDone())all=false;
       if(all)break;
       if(failure.get()!=null||paused){for(HttpURLConnection c:connections)c.disconnect();}
       try{Thread.sleep(100);}catch(InterruptedException ex){paused=true;Thread.currentThread().interrupt();}
       // Every checkpoint is crash-consistent; never record unwritten buffer contents.
       if(System.nanoTime()-lastCheckpoint>=1000000000L)checkpoint(channel);
      }
      for(Future<?> f:jobs)try{f.get();}catch(ExecutionException ex){throw new IOException("Ошибка загрузчика",ex.getCause());}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new InterruptedIOException("Передача прервана");}
      if(failure.get()!=null)throw failure.get();
     }finally{
      pool.shutdown();
      try{if(!pool.awaitTermination(40,TimeUnit.SECONDS)){pool.shutdownNow();throw new IOException("Не удалось завершить сетевые потоки");}}catch(InterruptedException ex){pool.shutdownNow();Thread.currentThread().interrupt();}
      checkpoint(channel);
     }
    }
    checkPause();checkpoint(channel);
   }
   checkPause();
   try{verify(partial);}catch(IntegrityException ex){Files.deleteIfExists(metadata.toPath());Files.deleteIfExists(partial.toPath());throw ex;}
   promote(partial);Files.deleteIfExists(metadata.toPath());state="ready";
  }catch(IOException ex){if(paused){state="paused";error="";throw new InterruptedIOException("Пауза. Докачка доступна после повторного запуска.");}state="error";error=ex.getMessage();throw ex;}
  finally{running.set(false);}
 }
 private volatile long lastCheckpoint;
 private void checkpoint(FileChannel channel)throws IOException{
  long[] safe=new long[count];for(int i=0;i<count;i++)safe[i]=offsets.get(i);
  channel.force(false);
  Properties p=new Properties();p.setProperty("version","1");p.setProperty("sha256",cfg.sha256);p.setProperty("url",cfg.url);p.setProperty("size",Long.toString(cfg.size));p.setProperty("parts",Integer.toString(count));
  for(int i=0;i<count;i++)p.setProperty("offset."+i,Long.toString(safe[i]));
  File temp=new File(metadata.getPath()+".tmp");try(FileOutputStream f=new FileOutputStream(temp)){p.store(f,"Bonsai Local resumable transfer");f.getFD().sync();}
  atomicMove(temp,metadata);lastCheckpoint=System.nanoTime();
 }
 private void loadCheckpoint(int parts)throws IOException{
  if(!metadata.isFile()||!partial.isFile()||partial.length()!=cfg.size)return;
  Properties p=new Properties();try(FileInputStream f=new FileInputStream(metadata)){p.load(f);}
  if(!cfg.sha256.equals(p.getProperty("sha256"))||!cfg.url.equals(p.getProperty("url"))||!Long.toString(cfg.size).equals(p.getProperty("size"))||!Integer.toString(parts).equals(p.getProperty("parts")))return;
  AtomicLongArray tmp=new AtomicLongArray(parts);
  try{for(int i=0;i<parts;i++){long n=Long.parseLong(p.getProperty("offset."+i,"-1"));long len=cfg.size*(i+1)/parts-cfg.size*i/parts;if(n<0||n>len)return;tmp.set(i,n);}}catch(NumberFormatException ex){return;}
  offsets=tmp;
 }
 private boolean probe()throws IOException{
  IOException last=null;
  for(int attempt=0;attempt<6;attempt++){
   checkPause();HttpURLConnection c=null;
   try{c=open(0,0,true);int code=c.getResponseCode();
    if(code==206){validateRange(c,0,0);return true;}
    if(code==200){String n=c.getHeaderField("Content-Length");if(n!=null&&Long.parseLong(n)!=cfg.size)throw new IntegrityException("Размер файла на сервере не совпадает с выбранной моделью");return false;}
    throw httpError(code);
   }catch(IntegrityException ex){throw ex;}catch(IOException ex){last=ex;if(attempt==5)throw ex;backoff(attempt);}
   finally{if(c!=null){connections.remove(c);c.disconnect();}}
  }throw last;
 }
 private HttpURLConnection open(long start,long last,boolean range)throws IOException{
  URL u=new URL(cfg.url);boolean secure="https".equalsIgnoreCase(u.getProtocol());
  for(int i=0;i<10;i++){
   checkPause();if(!"https".equalsIgnoreCase(u.getProtocol())&&!"http".equalsIgnoreCase(u.getProtocol()))throw new IOException("Недопустимый протокол загрузки");
   if(secure&&!"https".equalsIgnoreCase(u.getProtocol()))throw new IOException("Небезопасное перенаправление HTTPS → HTTP отклонено");
   HttpURLConnection c=(HttpURLConnection)u.openConnection();connections.add(c);
   try{
    c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);
    c.setRequestProperty("User-Agent","BonsaiLocal/0.5 (Android; resumable)");c.setRequestProperty("Accept-Encoding","identity");
    if(range)c.setRequestProperty("Range","bytes="+start+"-"+last);
    int code=c.getResponseCode();
    if(code==301||code==302||code==303||code==307||code==308){String location=c.getHeaderField("Location");if(location==null)throw new IOException("Сервер вернул перенаправление без адреса");u=new URL(u,location);connections.remove(c);c.disconnect();continue;}
    return c;
   }catch(IOException ex){connections.remove(c);c.disconnect();throw ex;}
  }throw new IOException("Слишком много перенаправлений");
 }
 private static IOException httpError(int code){return new IOException("HTTP "+code+". Повторите докачку позже или импортируйте файл из браузера.");}
 private void validateRange(HttpURLConnection c,long start,long last)throws IOException{
  String value=c.getHeaderField("Content-Range");Matcher m=CONTENT_RANGE.matcher(value==null?"":value);
  try{if(!m.matches()||Long.parseLong(m.group(1))!=start||Long.parseLong(m.group(2))!=last||Long.parseLong(m.group(3))!=cfg.size)throw new IntegrityException("Сервер вернул неверный Content-Range; запись отменена");}
  catch(NumberFormatException ex){throw new IntegrityException("Некорректный Content-Range");}
  String enc=c.getHeaderField("Content-Encoding");if(enc!=null&&!enc.equalsIgnoreCase("identity"))throw new IntegrityException("Сжатый ответ несовместим с докачкой по смещениям");
 }
 private void fetchPart(FileChannel file,int part,boolean range)throws IOException{
  long first=begin(part),limit=end(part);IOException lastError=null;
  for(int attempt=0;attempt<6;attempt++){
   checkPause();long pos=first+offsets.get(part);if(pos==limit)return;HttpURLConnection c=null;
   try{
    c=open(pos,limit-1,range);int code=c.getResponseCode();
    if(range){if(code==200)throw new IntegrityException("Сервер перестал поддерживать Range; файл не будет повреждён");if(code!=206)throw httpError(code);validateRange(c,pos,limit-1);}
    else if(code!=200)throw httpError(code);
    try(InputStream in=c.getInputStream()){
     byte[] bytes=new byte[256*1024];
     while(pos<limit){checkPause();int n=in.read(bytes,0,(int)Math.min(bytes.length,limit-pos));if(n<0)throw new EOFException("Сетевое соединение оборвалось до конца части");if(n==0)continue;
      ByteBuffer b=ByteBuffer.wrap(bytes,0,n);while(b.hasRemaining()){int w=file.write(b,pos);if(w<=0)throw new IOException("Не удалось записать файл модели");pos+=w;}
      offsets.set(part,pos-first);traffic.addAndGet(n);
     }
    }return;
   }catch(IntegrityException ex){throw ex;}catch(IOException ex){lastError=ex;checkPause();if(attempt==5)throw ex;
    if(!range){offsets.set(part,0);}backoff(attempt);
   }finally{if(c!=null){connections.remove(c);c.disconnect();}}
  }throw lastError;
 }
 private void backoff(int attempt)throws InterruptedIOException{
  long delay=Math.min(10000,250L<<attempt);
  for(long i=0;i<delay;i+=100){checkPause();try{Thread.sleep(Math.min(100,delay-i));}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new InterruptedIOException("Передача прервана");}}
 }
 private static final class IntegrityException extends IOException{IntegrityException(String s){super(s);}}
 private void verify(File f)throws IOException{
  state="verify";localProgress=0;if(f.length()!=cfg.size)throw new IntegrityException("Файл неполный: неверный размер");MessageDigest md=digest();
  try(InputStream in=new BufferedInputStream(new FileInputStream(f),1024*1024)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))>=0){checkPause();if(n==0)continue;md.update(b,0,n);localProgress+=n;}}
  if(!cfg.sha256.equals(hex(md.digest())))throw new IntegrityException("SHA-256 не совпадает. Файл повреждён или выбрана другая версия модели. Нужен выбранный в приложении формат Bonsai 2 27B.");
 }
 public void importFrom(InputStream input)throws IOException{
  if(input==null)throw new IOException("Файловый менеджер не предоставил поток файла");
  if(!running.compareAndSet(false,true)){input.close();throw new IOException("Передача уже выполняется");}
  importInput=input;error="";state="import";localProgress=0;
  try{
   ensureDirs();checkPause();space(cfg.size);MessageDigest md=digest();
   try(InputStream in=input;FileOutputStream out=new FileOutputStream(importPartial)){
    byte[] b=new byte[1024*1024];int n;
    while((n=in.read(b))>=0){checkPause();if(n==0)continue;if(localProgress+n>cfg.size)throw new IntegrityException("Этот файл больше выбранной модели. Проверьте переключатель PQ2_0 / PTQ1_0.");out.write(b,0,n);md.update(b,0,n);localProgress+=n;traffic.addAndGet(n);}
    out.getFD().sync();
   }
   checkPause();if(localProgress!=cfg.size)throw new IntegrityException("Файл неполный или это другая модель: ожидается "+cfg.size+" байт");
   state="verify";if(!cfg.sha256.equals(hex(md.digest())))throw new IntegrityException("SHA-256 выбранного файла не совпадает с выбранным форматом Bonsai 2 27B");
   promote(importPartial);Files.deleteIfExists(metadata.toPath());Files.deleteIfExists(partial.toPath());state="ready";
  }catch(IOException ex){if(paused){state="paused";error="Импорт прерван. Выберите файл ещё раз.";}else{state="error";error=ex.getMessage();}throw ex;}
  finally{importInput=null;try{input.close();}catch(IOException ignored){}try{Files.deleteIfExists(importPartial.toPath());}finally{running.set(false);}}
 }
 private void promote(File source)throws IOException{
  checkPause();atomicMove(source,target);
  File tmp=new File(marker.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp)){out.write((cfg.sha256+"\n").getBytes("UTF-8"));out.getFD().sync();}atomicMove(tmp,marker);
 }
 private static void atomicMove(File from,File to)throws IOException{
  try{Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
  catch(AtomicMoveNotSupportedException ex){Files.move(from.toPath(),to.toPath(),StandardCopyOption.REPLACE_EXISTING);}
 }
}
