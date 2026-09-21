package com.prismml.bonsailocal.repair;
import java.io.*;import java.nio.file.*;import java.util.concurrent.*;
public final class TransferPauseTest {
 public static void main(String[] a)throws Exception{
  Path dir=Files.createTempDirectory("bonsai-pause-");CountDownLatch read=new CountDownLatch(1),closed=new CountDownLatch(1);
  InputStream slow=new InputStream(){public int read(){return -1;}public int read(byte[] b,int o,int n)throws IOException{read.countDown();try{closed.await();}catch(InterruptedException e){throw new IOException(e);}return -1;}
   public void close(){try{Thread.sleep(1500);}catch(InterruptedException e){Thread.currentThread().interrupt();}closed.countDown();}};
  TransferEngine en=new TransferEngine(dir.resolve("model.gguf").toFile(),dir.resolve("marker").toFile(),new TransferEngine.Config("http://127.0.0.1/unused",10000,"0000000000000000000000000000000000000000000000000000000000000000",8));
  Thread worker=new Thread(()->{try{en.importFrom(slow);}catch(IOException expected){}});worker.start();if(!read.await(4,TimeUnit.SECONDS))throw new AssertionError("import did not start");long t=System.nanoTime();en.pause();long ms=(System.nanoTime()-t)/1000000;worker.join(10000);
  if(ms>500)throw new AssertionError("pause blocked caller for "+ms+" ms; notification action runs on main thread");
  if(worker.isAlive()||!en.snapshot().state.equals("paused"))throw new AssertionError("import did not stop");
  System.out.println("PASS pause returns without blocking on slow stream close ("+ms+" ms)");
 }
}
