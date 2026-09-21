package com.prismml.bonsailocal.repair;
import java.io.*;import java.nio.file.*;import java.util.*;import java.net.*;
import com.sun.net.httpserver.HttpServer;
/** Real JNI launch and health handling; subprocess/health responses stand in for Android Vulkan. */
public final class GpuRegression {
 static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);System.out.println("PASS "+s);}
 static void await(Bridge b,int state)throws Exception{for(int i=0;i<100;i++){if(b.status().contains("\"state\":"+state+","))return;Thread.sleep(100);}throw new AssertionError(b.status());}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("bonsai-gpu-jni-"),ext=Files.createDirectory(root.resolve("ext")),lib=Files.createDirectory(root.resolve("lib")),files=Files.createDirectory(root.resolve("files"));
  Bridge b=new Bridge();Bridge.init(new Object(),ext.toString(),lib.toString(),files.toString());
  HttpServer health=HttpServer.create(new InetSocketAddress("127.0.0.1",18080),0);
  health.createContext("/health",x->{x.sendResponseHeaders(200,0);x.close();});health.start();
  try{
   try{check(b.configureRuntime(0,4096,false,4,8,0,8),"PTQ Vulkan settings accepted while idle");}catch(UnsatisfiedLinkError e){throw new AssertionError("JNI has no GPU configuration API",e);}
   check(!b.configureRuntime(1,4096,false,4,8,0,8),"PQ2 Vulkan refused at native boundary");
   check(!b.configureRuntime(0,4096,false,4,8,0,-1),"invalid GPU count refused at native boundary");
   try(RandomAccessFile f=new RandomAccessFile(ext.resolve("Ternary-Bonsai-2-27B-PTQ1_0.gguf").toFile(),"rw")){f.writeBytes("GGUF");f.setLength(5946648928L);}
   Files.writeString(files.resolve("model.complete"),"ok\n");
   Path exe=lib.resolve("libllama_server_exec.so"),argv=root.resolve("argv"),env=root.resolve("backend");
   String script="#!/bin/sh\nprintf '%s\\n' \"$@\" > '"+argv+"'\nprintf '%s' \"$GGML_BACKEND_PATH\" > '"+env+"'\necho 'load_tensors: offloaded 8/65 layers to GPU'\necho 'load_tensors: Vulkan0 model buffer size = 500.25 MiB'\necho 'llama_context: Vulkan0 compute buffer size = 10.00 MiB'\nexec sleep 60\n";
   Files.writeString(exe,script);exe.toFile().setExecutable(true);
   b.start();await(b,4);check(b.status().contains("Vulkan"),"missing Vulkan plugin fails without CPU fallback");
   Files.writeString(lib.resolve("libbonsai_vulkan.so"),"test boundary fixture");
   b.stop();await(b,5);b.start();
   check(!b.configureRuntime(0,8192,false,6,8,0,16),"queued launch locks GPU options");
   await(b,3);List<String> v=Files.readAllLines(argv);
   check(v.get(v.indexOf("--device")+1).equals("Vulkan0")&&v.get(v.indexOf("--n-gpu-layers")+1).equals("8"),"Vulkan device and layer count reach actual subprocess");
   check(Files.readString(env).equals(lib.resolve("libbonsai_vulkan.so").toString()),"GPU plugin is loaded only through explicit path");
   check(b.status().contains("\"gpuReportedLayers\":8"),"reported layer count comes from runtime log");
   b.stop();await(b,5);
   Files.writeString(exe,script.replace("offloaded 8/65","offloaded 0/65"));b.start();await(b,4);
   check(!b.status().contains("\"alive\":true"),"zero offload cannot become ready and subprocess is stopped");
   b.stop();await(b,5);
   Files.writeString(exe,script.replace("Vulkan0 model buffer","Vulkan_Host model buffer"));b.start();await(b,4);
   check(!b.status().contains("\"alive\":true"),"nominal layer report without GPU model buffer cannot become ready");
   check(b.configureRuntime(0,4096,false,4,8,0,0),"CPU remains selectable after GPU failure");
   Files.writeString(exe,script.replace("offloaded 8/65","offloaded 0/65"));b.start();await(b,3);v=Files.readAllLines(argv);
   check(v.get(v.indexOf("--device")+1).equals("none")&&v.get(v.indexOf("--n-gpu-layers")+1).equals("0"),"CPU launch explicitly disables device offload");
   check(Files.readString(env).isEmpty(),"CPU does not load the optional plugin");
  }finally{b.stop();health.stop(0);Bridge.shutdown();}
 }
}
