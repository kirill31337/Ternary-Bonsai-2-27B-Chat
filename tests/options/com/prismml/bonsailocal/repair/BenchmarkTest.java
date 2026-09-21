package com.prismml.bonsailocal.repair;
import com.sun.net.httpserver.*;import java.net.*;import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.atomic.AtomicInteger;
public final class BenchmarkTest {
 static int n;static void ok(boolean b,String s){if(!b)throw new AssertionError(s);n++;System.out.println("PASS "+s);}
 public static void main(String[]x)throws Exception{
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",18080),0);AtomicInteger requests=new AtomicInteger(),bad=new AtomicInteger();Path dir=Files.createTempDirectory("bonsai-benchmark-");
  server.createContext("/completion",e->{try{requests.incrementAndGet();String body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);if(!e.getRequestMethod().equals("POST")||!body.contains("\"cache_prompt\":false")||!body.contains("\"n_predict\":32")||!body.contains("\"ignore_eos\":true"))bad.incrementAndGet();Thread.sleep(100);byte[] out="{\"timings\":{\"prompt_n\":12,\"prompt_per_second\":9.5,\"predicted_n\":32,\"predicted_per_second\":3.25},\"content\":\"synthetic\"}".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,out.length);e.getResponseBody().write(out);}catch(Exception ignore){}finally{e.close();}});server.start();
  try {
   LocalBenchmark.start(dir.toFile(),"{\"threads\":4}");LocalBenchmark.start(dir.toFile(),"{}");
   for(int i=0;i<100&&LocalBenchmark.isBusy();i++)Thread.sleep(30);
   ok(!LocalBenchmark.isBusy(),"benchmark completes asynchronously");
   ok(requests.get()==1&&bad.get()==0,"one local request, fixed token count, no prompt cache");
   ok(LocalBenchmark.status().contains("3.25")&&LocalBenchmark.status().contains("\"state\":\"done\""),"actual server timings exposed, no fabricated rates");
   ok(Files.readString(dir.resolve("benchmarks.jsonl")).contains("3.25"),"result persisted with settings");
   ok(Files.readString(dir.resolve("benchmarks.jsonl")).contains("\"busy\":false"),"saved completed result must not be marked busy");
   Files.writeString(dir.resolve("server.log"),"system_info: NEON=1 DOTPROD=1\nload_backend: loaded CPU backend from /lib/cpu.so\n");
   ok(LocalBenchmark.runtimeInfo(dir.toFile()).contains("DOTPROD"),"real engine backend details exposed");
   server.removeContext("/completion");server.createContext("/completion",e->{e.getResponseHeaders().add("Location","https://example.com/not-used");e.sendResponseHeaders(302,-1);e.close();});
   LocalBenchmark.start(dir.toFile(),"{}");for(int i=0;i<100&&LocalBenchmark.isBusy();i++)Thread.sleep(30);
   ok(LocalBenchmark.status().contains("\"state\":\"error\"")&&LocalBenchmark.status().contains("302"),"redirect rejected, no external request");
   LocalBenchmark.cancel();ok(!LocalBenchmark.isBusy(),"cancellation while idle is harmless");
   System.out.println(n+" benchmark transport tests passed, NOT device throughput measurements");
  } finally {server.stop(0);LocalBenchmark.cancel();}
 }
}
