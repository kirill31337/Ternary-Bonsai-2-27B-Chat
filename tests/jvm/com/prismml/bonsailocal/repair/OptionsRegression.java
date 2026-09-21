package com.prismml.bonsailocal.repair;
import java.io.*;import java.nio.file.*;import java.util.*;
/** Exercises the real JNI launcher with a subprocess that records argv, not LLM performance. */
public final class OptionsRegression {
 static class B { }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
 static void waitExit(Bridge b,int code)throws Exception{for(int i=0;i<100;i++){if(b.status().contains("\"exit\":"+code+","))return;Thread.sleep(100);}throw new AssertionError(b.status());}
 static void waitIdle(Bridge b)throws Exception{for(int i=0;i<80;i++){if(b.status().contains("\"pending\":0")&&!b.status().contains("\"state\":2,"))return;Thread.sleep(100);}throw new AssertionError(b.status());}
 static void file(Path p,long size)throws Exception{try(RandomAccessFile f=new RandomAccessFile(p.toFile(),"rw")){f.writeBytes("GGUF");f.setLength(size);}}
 public static void main(String[] a)throws Exception{
  Path root=Files.createTempDirectory("bonsai-options-"), ext=Files.createDirectory(root.resolve("ext")),libs=Files.createDirectory(root.resolve("lib")),logs=Files.createDirectory(root.resolve("files"));
  Bridge b=new Bridge();Bridge.init(new Object(),ext.toString(),libs.toString(),logs.toString());
  try {
   try {check(b.configureOptions(1,16384,false,4,8,0),"PQ2 configuration accepted while idle");}catch(UnsatisfiedLinkError ex){throw new AssertionError("0.4 launcher has no PQ2/performance configuration API",ex);}
   check(b.status().contains("\"modelIndex\":1"),"chosen format reported");
   check(!b.configureOptions(3,16384,false,4,8,0),"unknown model refused");
   check(!b.configureOptions(1,9999,false,4,8,0),"invalid context refused");
   check(!b.configureOptions(1,16384,false,99,8,0),"invalid thread count refused");
   check(!b.configureOptions(1,16384,false,4,8,8),"invalid thinking mode refused");
   Path pq=ext.resolve("Ternary-Bonsai-2-27B-PQ2_0.gguf"), pt=ext.resolve("Ternary-Bonsai-2-27B-PTQ1_0.gguf");
   file(pq,7206168928L);file(pt,5946648928L);
   Files.writeString(logs.resolve("pq2.complete"),"3907dc1658db1f78a9826bf8d5bcb8dc65db0d466388937af57f2294fae62ec1\n");
   Files.writeString(logs.resolve("model.complete"),"ok\n");
   Path exe=libs.resolve("libllama_server_exec.so"),argv=root.resolve("argv");
   Files.writeString(exe,"#!/bin/sh\nprintf '%s\\n' \"$@\" > '"+argv+"'\nsleep 1\nexit 17\n");exe.toFile().setExecutable(true);
   check(b.configureOptions(1,16384,false,4,8,0),"PQ2 verified marker recognized");b.start();
   check(!b.configureOptions(0,65536,true,8,8,2),"cannot mutate queued launch options");
   waitExit(b,17);List<String> v=Files.readAllLines(argv);
   check(v.contains(pq.toString())&&!v.contains(pt.toString()),"actual process receives PQ2 filename");
   check(v.get(v.indexOf("--ctx-size")+1).equals("16384"),"actual context is 16K");
   check(v.get(v.indexOf("--threads")+1).equals("4")&&v.get(v.indexOf("--threads-batch")+1).equals("8"),"separate decode/prefill threads passed");
   check(v.contains("--jinja")&&v.contains("{\"enable_thinking\":false}"),"thinking OFF passed as template setting");
   check(v.get(v.indexOf("--reasoning-budget")+1).equals("0"),"thinking OFF has zero budget");
   check(v.get(v.indexOf("--cache-type-k")+1).equals("f16")&&v.get(v.indexOf("--cache-type-v")+1).equals("f16"),"speed profile uses FP16 KV");
   check(v.get(v.indexOf("--host")+1).equals("127.0.0.1"),"server stays loopback only");
   b.stop();waitIdle(b);check(b.configureOptions(0,262144,true,6,6,1),"legacy PTQ plus 256K accepted");
   Files.writeString(exe,"#!/bin/sh\nprintf '%s\\n' \"$@\" > '"+argv+"'\nexit 18\n");b.start();waitExit(b,18);v=Files.readAllLines(argv);
   check(v.contains(pt.toString())&&v.contains("262144"),"PTQ and 256K preserved");
   check(v.contains("q4_0")&&v.contains("medium"),"Q4 and medium reasoning preserved");
   check(v.get(v.indexOf("--reasoning-budget")+1).equals("-1"),"medium reasoning not zero budget");
   check(Files.size(pq)==7206168928L&&Files.size(pt)==5946648928L,"switching leaves both model files intact");
  } finally {Bridge.shutdown();}
 }
}
