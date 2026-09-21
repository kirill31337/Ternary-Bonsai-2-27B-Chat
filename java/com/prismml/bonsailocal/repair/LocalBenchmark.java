package com.prismml.bonsailocal.repair;
import java.io.*;import java.net.*;import java.util.concurrent.atomic.AtomicBoolean;
/** A fixed loopback-only synthetic completion. No chat data or remote service is used. */
public final class LocalBenchmark {
 private static final AtomicBoolean running=new AtomicBoolean();private static volatile boolean cancelled;
 private static volatile HttpURLConnection connection;private static volatile String phase="idle",response="",error="",settings="{}";
 private static volatile long elapsed;
 private LocalBenchmark(){}
 public static boolean isBusy(){return running.get();}
 public static void start(final File files,final String opts){
  if(!running.compareAndSet(false,true))return;
  cancelled=false;response="";error="";elapsed=0;settings=opts;phase="running";
  new Thread(new Runnable(){public void run(){long begin=System.nanoTime();HttpURLConnection c=null;
   try {
    if(cancelled)throw new InterruptedIOException("Замер отменён");
    c=(HttpURLConnection)new URL("http://127.0.0.1:18080/completion").openConnection();connection=c;
    c.setInstanceFollowRedirects(false);c.setConnectTimeout(3000);c.setReadTimeout(180000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
    byte[] data="{\"prompt\":\"The quick brown fox jumps over the lazy dog.\",\"n_predict\":32,\"stream\":false,\"cache_prompt\":false,\"ignore_eos\":true,\"temperature\":0,\"seed\":42}".getBytes("UTF-8");c.setFixedLengthStreamingMode(data.length);
    if(cancelled)throw new InterruptedIOException("Замер отменён");
    try(OutputStream out=c.getOutputStream()){out.write(data);}
    int code=c.getResponseCode();if(code!=200)throw new IOException("Локальный движок ответил HTTP "+code);
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
     byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(cancelled)throw new InterruptedIOException("Замер отменён");if(out.size()+n>1024*1024)throw new IOException("Слишком большой ответ замера");out.write(buf,0,n);}response=out.toString("UTF-8");
    }
    if(cancelled)throw new InterruptedIOException("Замер отменён");
    if(!response.contains("\"timings\""))throw new IOException("Движок не вернул timings; скорость не вычислялась.");
    elapsed=(System.nanoTime()-begin)/1000000;phase="done";
    if(files!=null)try(FileOutputStream out=new FileOutputStream(new File(files,"benchmarks.jsonl"),true)){out.write((status(false)+"\n").getBytes("UTF-8"));}catch(IOException ex){error="Замер получен, но не сохранён: "+ex.getMessage();}
   } catch(Exception ex){elapsed=(System.nanoTime()-begin)/1000000;error=ex.toString();phase=cancelled?"cancelled":"error";}
   finally{connection=null;if(c!=null)c.disconnect();running.set(false);}
  }},"Bonsai-benchmark").start();
 }
 public static void cancel(){if(!running.get())return;cancelled=true;final HttpURLConnection c=connection;if(c!=null){Thread t=new Thread(new Runnable(){public void run(){c.disconnect();}},"Bonsai-benchmark-cancel");t.setDaemon(true);t.start();}}
 private static String quote(String s){StringBuilder r=new StringBuilder("\"");for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'||c=='\\')r.append('\\').append(c);else if(c<' ')r.append(String.format(java.util.Locale.US,"\\u%04x",(int)c));else r.append(c);}return r.append('"').toString();}
 public static String status(){return status(running.get());}
 private static String status(boolean active){return "{\"state\":"+quote(phase)+",\"busy\":"+active+",\"response\":"+quote(response)+",\"error\":"+quote(error)+",\"settings\":"+settings+",\"elapsedMs\":"+elapsed+"}";}
 public static String runtimeInfo(File files){
  StringBuilder s=new StringBuilder();
  try(BufferedReader r=new BufferedReader(new FileReader("/proc/meminfo"))){String l;while((l=r.readLine())!=null)if(l.startsWith("MemTotal:")||l.startsWith("MemAvailable:"))s.append(l).append('\n');}catch(IOException ex){s.append("Данные RAM недоступны\n");}
  if(files!=null){
   appendRuntimeLog(s,new File(files,"server.log"),"Загрузка модели / чат");
   appendRuntimeLog(s,new File(files,"engine-test.log"),"Проверка движка");
  }
  return s.toString();
 }
 private static void appendRuntimeLog(StringBuilder out,File file,String title){
  if(!file.isFile())return;
  out.append('\n').append(title).append(":\n");
  try(RandomAccessFile f=new RandomAccessFile(file,"r")){
   long size=f.length();int head=(int)Math.min(size,131072);
   int lines=appendLogRange(out,f,0,head,48);
   if(size>head){
    out.append("… последние записи …\n");
    long start=Math.max(head,size-32768);lines+=appendLogRange(out,f,start,(int)(size-start),16);
   }
   if(lines==0)out.append("Данные о backend пока не записаны.\n");
  }catch(IOException ex){out.append("Журнал недоступен: ").append(ex.getMessage()).append('\n');}
 }
 private static int appendLogRange(StringBuilder out,RandomAccessFile file,long start,int length,int limit)throws IOException{
  file.seek(start);byte[] bytes=new byte[length];file.readFully(bytes);
  java.util.ArrayDeque<String> selected=new java.util.ArrayDeque<String>();
  for(String line:new String(bytes,"UTF-8").split("\n")){
   String lower=line.toLowerCase(java.util.Locale.ROOT);
   if(lower.contains("vulkan")||lower.contains("offload")||lower.contains("error")||lower.contains("failed")||lower.contains("system_info")||lower.contains("loaded cpu backend")||lower.contains("buffer size")||lower.contains("cpu_repack")||lower.contains("n_threads")||lower.contains("n_ctx")||lower.contains("cache_type")){
    if(selected.size()==limit){if(start==0)break;selected.removeFirst();}
    selected.addLast(line.length()>500?line.substring(0,500):line);
   }
  }
  for(String line:selected)out.append(line).append('\n');
  return selected.size();
 }
}
