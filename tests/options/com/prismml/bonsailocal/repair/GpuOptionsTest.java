package com.prismml.bonsailocal.repair;
import java.io.*;import java.nio.file.*;
public final class GpuOptionsTest {
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("PASS "+text);}
 public static void main(String[] args)throws Exception{
  Path file=Files.createTempFile("bonsai-gpu-options-",".properties");
  String old="version=1\nmodel=0\nctx=8192\nq4=false\nthreads=6\nbatchThreads=8\nthinking=1\n";
  Files.writeString(file,old);
  RuntimeOptions r=RuntimeOptions.load(file.toFile(),1);
  check(r.modelIndex==0&&r.ctxSize==8192&&r.threads==6&&r.json().contains("\"gpuLayers\":0"),"upgrade preserves CPU settings without enabling GPU");
  String current=old.replace("version=1","version=2")+"gpuLayers=8\n";
  Files.writeString(file,current);r=RuntimeOptions.load(file.toFile(),1);
  check(r.modelIndex==0&&r.ctxSize==8192&&r.json().contains("\"gpuLayers\":8"),"PTQ Vulkan selection loaded from disk");
  r.save(file.toFile());r=RuntimeOptions.load(file.toFile(),1);
  check(r.json().contains("\"gpuLayers\":8"),"Vulkan selection survives save and reload");
  Files.writeString(file,current.replace("model=0","model=1"));r=RuntimeOptions.load(file.toFile(),1);
  check(r.json().contains("\"gpuLayers\":0"),"unsupported PQ2 Vulkan configuration cannot be restored");
  Files.writeString(file,current.replace("gpuLayers=8","gpuLayers=-1"));r=RuntimeOptions.load(file.toFile(),0);
  check(r.json().contains("\"gpuLayers\":0"),"invalid GPU layer count falls back to CPU");
 }
}
