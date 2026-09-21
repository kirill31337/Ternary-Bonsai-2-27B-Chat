package com.prismml.bonsailocal.repair;
import java.nio.file.*;import java.io.*;import java.util.*;
public final class ModelOptionsTest {
 static int count;static void check(boolean b,String name){if(!b)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
 public static void main(String[] z)throws Exception{
  ModelCatalog.Model pt=ModelCatalog.get(0),pq=ModelCatalog.get(1);
  check(pt.size==5946648928L&&pq.size==7206168928L,"both exact large-file sizes are longs");
  check(pq.sha.equals("3907dc1658db1f78a9826bf8d5bcb8dc65db0d466388937af57f2294fae62ec1"),"PQ2 SHA matches publisher pointer");
  check(!pq.name.equals(pt.name)&&!pq.marker.equals(pt.marker),"model targets and ready markers cannot collide");
  check(pq.url.contains("6ed5e12bf84b7a63069882c91dd9e9218647d17b/")&&pq.url.contains("PQ2_0.gguf"),"download is revision-pinned PQ2, not main or PTQ");
  boolean rejected=false;try{ModelCatalog.get(8);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"unknown model IDs refused");
  Path root=Files.createTempDirectory("bonsai-catalog-"),ext=Files.createDirectory(root.resolve("ext")),files=Files.createDirectory(root.resolve("files"));
  Path p=ext.resolve(pt.name);try(RandomAccessFile f=new RandomAccessFile(p.toFile(),"rw")){f.writeBytes("GGUF");f.setLength(pt.size);}
  Files.writeString(files.resolve(pt.marker),"ok\n");
  check(pt.ready(ext.toFile(),files.toFile()),"existing 0.4 PTQ file and legacy marker recognized without moving");
  check(!pq.ready(ext.toFile(),files.toFile()),"PTQ marker cannot mark PQ2 ready");
  Path q=ext.resolve(pq.name);try(RandomAccessFile f=new RandomAccessFile(q.toFile(),"rw")){f.writeBytes("GGUF");f.setLength(pq.size);}
  Files.writeString(files.resolve(pq.marker),pt.sha+"\n");check(!pq.ready(ext.toFile(),files.toFile()),"wrong model hash marker refused");
  Files.writeString(files.resolve(pq.marker),pq.sha+"\n");check(pq.ready(ext.toFile(),files.toFile()),"verified PQ2 is selectable");
  check(pt.ready(ext.toFile(),files.toFile()),"PQ2 did not overwrite existing PTQ");
  try(RandomAccessFile f=new RandomAccessFile(q.toFile(),"rw")){f.setLength(pq.size-1);}check(!pq.ready(ext.toFile(),files.toFile()),"truncated large file refused despite marker");
  RuntimeOptions d=RuntimeOptions.defaults(1);check(d.ctxSize==16384&&!d.kvQ4&&d.threads==4&&d.batchThreads==8&&d.thinking==0,"16K/FP16/4+8/off profile defaults");
  Path config=files.resolve("runtime-options.properties");
  check(RuntimeOptions.load(config.toFile(),0).modelIndex==0,"missing settings preserve detected old model");
  RuntimeOptions o=new RuntimeOptions(1,262144,true,6,8,1);o.save(config.toFile());RuntimeOptions r=RuntimeOptions.load(config.toFile(),0);
  check(r.modelIndex==1&&r.ctxSize==262144&&r.kvQ4&&r.threads==6&&r.batchThreads==8&&r.thinking==1,"all options survive disk roundtrip");
  Files.writeString(config,"ctx=99999\nmodel=../hack\n");check(RuntimeOptions.load(config.toFile(),0).ctxSize==16384,"corrupt settings fall back safely");
  rejected=false;try{new RuntimeOptions(1,9999,false,4,8,0);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid context rejected before native call");
  rejected=false;try{new RuntimeOptions(1,16384,false,3,8,0);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"unsupported thread count refused");
  check(d.json().contains("\"thinking\":0")&&d.json().contains("\"kvQ4\":false"),"settings JSON contains real booleans/numbers");
  System.out.println(count+" model/options tests passed");
 }
}
