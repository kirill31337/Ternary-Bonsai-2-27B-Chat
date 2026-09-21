package com.prismml.bonsailocal.repair;
import java.io.*;import java.nio.file.*;import java.util.*;
/** Real JNI and ProcessBuilder argv; sparse files are NOT model inference fixtures. */
public class VisionRegression {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);System.out.println("PASS "+m);}
 static void sparse(Path p,long len)throws Exception{try(RandomAccessFile f=new RandomAccessFile(p.toFile(),"rw")){f.writeBytes("GGUF");f.setLength(len);}}
 static void exit(Bridge b,int n)throws Exception{for(int i=0;i<70;i++){if(b.status().contains("\"exit\":"+n+",")&&b.status().contains("\"alive\":false"))return;Thread.sleep(100);}throw new AssertionError(b.status());}
 public static void main(String[]args)throws Exception{
  Path dir=Files.createTempDirectory("bonsai-vision-"),ext=Files.createDirectory(dir.resolve("ext")),libs=Files.createDirectory(dir.resolve("libs")),logs=Files.createDirectory(dir.resolve("logs"));Bridge b=new Bridge();Bridge.init(new Object(),ext.toString(),libs.toString(),logs.toString());
  try{
   ok(!b.configureVision(true),"missing projector cannot be enabled");
   Path vision=ext.resolve("Ternary-Bonsai-2-27B-mmproj-Q8_0.gguf");sparse(vision,629246976L);Files.writeString(logs.resolve("vision.complete"),"wrong");ok(!b.configureVision(true),"unverified projector cannot be enabled");
   Files.writeString(logs.resolve("vision.complete"),"6807ede61d570bb86ba34b756a0fa109edc33668604de867c6ea6d8f1d631903\n");ok(b.configureVision(true),"verified pinned projector accepted");
   sparse(ext.resolve("Ternary-Bonsai-2-27B-PQ2_0.gguf"),7206168928L);Files.writeString(logs.resolve("pq2.complete"),"3907dc1658db1f78a9826bf8d5bcb8dc65db0d466388937af57f2294fae62ec1\n");ok(b.configureOptions(1,16384,false,4,8,0),"vision coexists with existing language options");
   Path exe=libs.resolve("libllama_server_exec.so"),argv=dir.resolve("argv");Files.writeString(exe,"#!/bin/sh\nprintf '%s\\n' \"$@\" > '"+argv+"'\nsleep 1\nexit 27\n");exe.toFile().setExecutable(true);b.start();ok(!b.configureVision(false),"cannot alter queued/live projector");exit(b,27);List<String> passed=Files.readAllLines(argv);
   ok(passed.get(passed.indexOf("--mmproj")+1).equals(vision.toString()),"real launch argv contains installed mmproj path");
   ok(passed.contains("--no-mmproj-offload")&&passed.get(passed.indexOf("--image-max-tokens")+1).equals("512"),"CPU projector and bounded image tokens reach process");
   ok(b.configureVision(false),"projector can be disabled after process termination");Files.writeString(exe,"#!/bin/sh\nprintf '%s\\n' \"$@\" > '"+argv+"'\nexit 28\n");b.start();exit(b,28);ok(!Files.readAllLines(argv).contains("--mmproj"),"text-only run does not load optional projector");
   ok(Files.size(vision)==629246976L,"disabling projector does not delete its file");
  }finally{Bridge.shutdown();}
 }
}
