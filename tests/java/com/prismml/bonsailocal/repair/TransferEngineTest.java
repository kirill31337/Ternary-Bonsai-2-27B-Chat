package com.prismml.bonsailocal.repair;
import com.sun.net.httpserver.*;
import java.io.*; import java.net.*; import java.nio.file.*; import java.security.*;
import java.util.*; import java.util.concurrent.*; import java.util.concurrent.atomic.*;

public class TransferEngineTest {
 static int passed=0;
 static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
 static String hash(byte[] d)throws Exception {byte[] h=MessageDigest.getInstance("SHA-256").digest(d);StringBuilder s=new StringBuilder();for(byte b:h)s.append(String.format("%02x",b&255));return s.toString();}
 static final class Server implements AutoCloseable {
  final byte[] data=new byte[4*1024*1024+37]; final HttpServer http; final ExecutorService pool=Executors.newCachedThreadPool();
  final AtomicInteger live=new AtomicInteger(),max=new AtomicInteger(),requests=new AtomicInteger(),failures=new AtomicInteger(),cuts=new AtomicInteger();
  final AtomicLong sent=new AtomicLong(); final List<String> ranges=Collections.synchronizedList(new ArrayList<String>());
  volatile boolean ignore=false,wrong=false,corrupt=false,redirect=false; volatile int delay=1;
  Server()throws Exception {new Random(12).nextBytes(data);System.arraycopy(new byte[]{71,71,85,70},0,data,0,4);http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);http.setExecutor(pool);http.createContext("/model",this::serve);http.createContext("/redirect",e->{e.getResponseHeaders().set("Location","/model");e.sendResponseHeaders(302,-1);e.close();});http.start();}
  String url(){return "http://127.0.0.1:"+http.getAddress().getPort()+(redirect?"/redirect":"/model");}
  void serve(HttpExchange e)throws IOException {
   requests.incrementAndGet();int l=live.incrementAndGet();max.accumulateAndGet(l,Math::max);
   try {String r=e.getRequestHeaders().getFirst("Range");ranges.add(r==null?"none":r);
    if(failures.getAndUpdate(x->Math.max(0,x-1))>0){e.sendResponseHeaders(503,-1);return;}
    int st=0,en=data.length-1; boolean partial=r!=null&&!ignore;
    if(partial){String[] p=r.substring(6).split("-");st=Integer.parseInt(p[0]);en=Integer.parseInt(p[1]);if(st>en||en>=data.length){e.sendResponseHeaders(416,-1);return;}
     e.getResponseHeaders().set("Content-Range","bytes "+(wrong?st+1:st)+"-"+en+"/"+data.length);}
    e.getResponseHeaders().set("ETag","\"fixture\"");e.sendResponseHeaders(partial?206:200,en-st+1);
    boolean cut=(en-st>10000)&&cuts.getAndUpdate(x->Math.max(0,x-1))>0;
    int stop=cut?st+(en-st)/2:en;OutputStream o=e.getResponseBody();
    for(int pos=st;pos<=stop;){int n=Math.min(16384,stop-pos+1);byte[] a=Arrays.copyOfRange(data,pos,pos+n);if(corrupt&&pos==0)a[0]^=1;o.write(a);o.flush();sent.addAndGet(n);pos+=n;if(delay>0)try{Thread.sleep(delay);}catch(InterruptedException ex){Thread.currentThread().interrupt();break;}}
   }catch(IOException ignored){}finally{e.close();live.decrementAndGet();}
  }
  public void close(){http.stop(0);pool.shutdownNow();}
 }
 static final class Fixture {
  final Path dir;final File target,marker; final TransferEngine.Config cfg;
  Fixture(Server s)throws Exception{dir=Files.createTempDirectory("bonsai-transfer-test-");target=dir.resolve("model.gguf").toFile();marker=dir.resolve("model.complete").toFile();cfg=new TransferEngine.Config(s.url(),s.data.length,hash(s.data),8);}
  TransferEngine engine(){return new TransferEngine(target,marker,cfg);}
 }
 interface Case{void run()throws Exception;}
 static void test(String name,Case t)throws Exception{t.run();passed++;System.out.println("PASS "+name);}
 public static void main(String[] args)throws Exception{
  test("parallel real HTTP ranges, exact output and marker",()->{try(Server s=new Server()){Fixture f=new Fixture(s);TransferEngine e=f.engine();e.download();check(s.max.get()>=2,"download did not overlap connections");check(Arrays.equals(s.data,Files.readAllBytes(f.target.toPath())),"different bytes");check(f.marker.exists(),"no complete marker");check(e.snapshot().state.equals("ready"),"not ready");}});
  test("pause and new-instance resume retain persisted offsets",()->{try(Server s=new Server()){s.delay=12;Fixture f=new Fixture(s);TransferEngine e=f.engine();AtomicReference<Throwable> err=new AtomicReference<>();Thread t=new Thread(()->{try{e.download();}catch(Throwable ex){err.set(ex);}});t.start();long until=System.currentTimeMillis()+5000;while(e.snapshot().done<400000&&t.isAlive()&&System.currentTimeMillis()<until)Thread.sleep(10);e.pause();t.join(10000);check(!t.isAlive(),"pause hung");check(!f.marker.exists()&&!f.target.exists(),"partial promoted");check(e.snapshot().state.equals("paused"),"pause state lost: "+e.snapshot().state);s.ranges.clear();long n=s.sent.get();TransferEngine e2=f.engine();e2.download();check(s.sent.get()-n<s.data.length,"resumed from zero");check(Arrays.equals(s.data,Files.readAllBytes(f.target.toPath())),"bad resume");}});
  test("range-ignoring server safely falls back to one stream",()->{try(Server s=new Server()){s.ignore=true;Fixture f=new Fixture(s);TransferEngine e=f.engine();e.download();check(e.snapshot().connections==1,"no single-stream fallback");check(Arrays.equals(s.data,Files.readAllBytes(f.target.toPath())),"fallback corruption");}});
  test("wrong Content-Range is rejected",()->{try(Server s=new Server()){s.wrong=true;Fixture f=new Fixture(s);try{f.engine().download();throw new AssertionError("wrong range accepted");}catch(IOException expected){}check(!f.marker.exists(),"invalid complete marker");}});
  test("transient 503 retried without data loss",()->{try(Server s=new Server()){s.failures.set(2);Fixture f=new Fixture(s);f.engine().download();check(s.requests.get()>8,"did not retry");check(Arrays.equals(s.data,Files.readAllBytes(f.target.toPath())),"retry corruption");}});
  test("truncated range body resumes missing bytes",()->{try(Server s=new Server()){s.cuts.set(2);Fixture f=new Fixture(s);f.engine().download();check(Arrays.equals(s.data,Files.readAllBytes(f.target.toPath())),"short-body corruption");}});
  test("relative redirects retain Range",()->{try(Server s=new Server()){s.redirect=true;Fixture f=new Fixture(s);f.engine().download();check(s.max.get()>=2,"redirect serialized all transfers");}});
  test("SHA mismatch never promotes a corrupted download",()->{try(Server s=new Server()){s.corrupt=true;Fixture f=new Fixture(s);try{f.engine().download();throw new AssertionError("corrupt accepted");}catch(IOException expected){}check(!f.target.exists()&&!f.marker.exists(),"corrupt promoted");}});
  test("local import uses stream, preserves original, verifies SHA",()->{try(Server s=new Server()){Fixture f=new Fixture(s);Path source=f.dir.resolve("browser-download.gguf");Files.write(source,s.data);f.engine().importFrom(new FileInputStream(source.toFile()));check(Arrays.equals(s.data,Files.readAllBytes(source)),"source changed");check(Arrays.equals(s.data,Files.readAllBytes(f.target.toPath())),"import wrong");check(s.requests.get()==0,"import used network");}});
  test("incomplete import rejected while old ready file survives",()->{try(Server s=new Server()){Fixture f=new Fixture(s);Files.write(f.target.toPath(),s.data);Files.write(f.marker.toPath(),new byte[]{1});try{f.engine().importFrom(new ByteArrayInputStream(Arrays.copyOf(s.data,500)));throw new AssertionError("short import accepted");}catch(IOException expected){}check(Arrays.equals(s.data,Files.readAllBytes(f.target.toPath())),"old valid model lost");check(f.marker.exists(),"old marker lost");}});
  test("full-size wrong import rejected by hash",()->{try(Server s=new Server()){Fixture f=new Fixture(s);byte[] bad=s.data.clone();bad[123]^=1;try{f.engine().importFrom(new ByteArrayInputStream(bad));throw new AssertionError("wrong file accepted");}catch(IOException expected){}check(!f.marker.exists(),"wrong file marked ready");}});
  test("constructor has no network, finished model avoids redownload",()->{try(Server s=new Server()){Fixture f=new Fixture(s);TransferEngine e=f.engine();check(s.requests.get()==0,"auto network");e.importFrom(new ByteArrayInputStream(s.data));f.engine().download();check(s.requests.get()==0,"redownloaded valid file");}});
  System.out.println("Transfer engine integration tests: "+passed+" passed (local HTTP, JVM; not Android).");
 }
}
