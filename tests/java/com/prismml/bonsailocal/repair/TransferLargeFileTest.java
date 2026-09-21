package com.prismml.bonsailocal.repair;
import com.sun.net.httpserver.*;
import java.io.*;import java.net.*;import java.nio.file.*;import java.security.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
/** Sparse files exercise real 64-bit seek and resume beyond 4 GiB without allocating/downloading 6 GB. */
public final class TransferLargeFileTest {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 static String hash(byte[] bytes)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format("%02x",b&255));return s.toString();}
 public static void main(String[] args)throws Exception {
  final long size=5946648928L,remaining=65536,start=size-remaining;
  Path d=Files.createTempDirectory("bonsai-large-resume-");File target=d.resolve("model.gguf").toFile(),part=d.resolve("model.gguf.partial").toFile(),meta=d.resolve("model.gguf.resume").toFile();
  AtomicLong requested=new AtomicLong(-1);CountDownLatch written=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService pool=Executors.newCachedThreadPool();HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(pool);
  server.createContext("/model",e->{try{String r=e.getRequestHeaders().getFirst("Range");String[] p=r.substring(6).split("-");long a=Long.parseLong(p[0]),b=Long.parseLong(p[1]);e.getResponseHeaders().set("Content-Range","bytes "+a+"-"+b+"/"+size);e.sendResponseHeaders(206,b-a+1);if(a==0&&b==0){e.getResponseBody().write(71);}else{requested.set(a);byte[] buf=new byte[8192];Arrays.fill(buf,(byte)0x5a);e.getResponseBody().write(buf);e.getResponseBody().flush();written.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException ignored){}}}catch(IOException ignored){}finally{e.close();}});server.start();
  Thread worker=null;
  try {
   String url="http://127.0.0.1:"+server.getAddress().getPort()+"/model",sha=hash(new byte[]{1});
   try(RandomAccessFile f=new RandomAccessFile(part,"rw")){f.setLength(size);}
   Properties props=new Properties();props.setProperty("version","1");props.setProperty("url",url);props.setProperty("sha256",sha);props.setProperty("size",Long.toString(size));props.setProperty("parts","8");
   for(int i=0;i<8;i++)props.setProperty("offset."+i,Long.toString(size*(i+1)/8-size*i/8-(i==7?remaining:0)));
   try(FileOutputStream out=new FileOutputStream(meta)){props.store(out,"64-bit fixture");}
   TransferEngine engine=new TransferEngine(target,d.resolve("model.complete").toFile(),new TransferEngine.Config(url,size,sha,8));
   ok(engine.snapshot().done==start,"checkpoint lost 64-bit offsets");
   worker=new Thread(()->{try{engine.download();}catch(IOException expected){}});worker.start();ok(written.await(4,TimeUnit.SECONDS),"no high-offset request");
   long until=System.currentTimeMillis()+4000;while(engine.snapshot().done<start+8192&&System.currentTimeMillis()<until)Thread.sleep(5);
   // Release the fixture connection before pause: JVM HttpURLConnection.disconnect can wait for a live reader.
   release.countDown();engine.pause();worker.join(10000);ok(!worker.isAlive(),"64-bit pause hung");ok(requested.get()>=start&&requested.get()>0xffffffffL,"range truncated to 32 bits");
   try(RandomAccessFile f=new RandomAccessFile(part,"r")){f.seek(start);ok(f.read()==0x5a,"write went to wrong 64-bit position");}
   ok(!target.exists()&&!d.resolve("model.complete").toFile().exists(),"unverified sparse data promoted");
   System.out.println("PASS real HTTP resume and positional write beyond 4 GiB (5,946,648,928-byte sparse file)");
  }finally{release.countDown();server.stop(0);pool.shutdownNow();if(worker!=null)worker.join(10000);try(java.util.stream.Stream<Path> paths=Files.walk(d)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}}
  byte[] good=new byte[1024*1024];new Random(22).nextBytes(good);Path base=Files.createTempDirectory("bonsai-import-cleanup-");File out=base.resolve("model.gguf").toFile();TransferEngine en=new TransferEngine(out,base.resolve("marker").toFile(),new TransferEngine.Config("http://127.0.0.1/unused",good.length,hash(good),8));
  Path blocker=base.resolve("model.gguf.importing");Files.createDirectory(blocker);Files.write(blocker.resolve("child"),new byte[]{1});
  try{en.importFrom(new ByteArrayInputStream(good));throw new AssertionError("directory conflict accepted");}catch(IOException expected){}
  Files.delete(blocker.resolve("child"));Files.delete(blocker);en.importFrom(new ByteArrayInputStream(good));ok(out.length()==good.length,"failed import permanently wedged the engine");
  System.out.println("PASS import can be retried after a filesystem cleanup error");
  System.out.println("2 large-file and cleanup tests passed (host JVM, not Android)");
 }
}
