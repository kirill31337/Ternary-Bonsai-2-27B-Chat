package com.prismml.bonsailocal.repair;
import java.io.*;import java.nio.file.*;import java.util.Properties;
/** Validated/persisted startup options; lower memory profile is not a speed guarantee. */
public final class RuntimeOptions {
 public final int modelIndex,ctxSize,threads,batchThreads,thinking,gpuLayers;public final boolean kvQ4;
 public RuntimeOptions(int m,int c,boolean q,int t,int b,int r){
  this(m,c,q,t,b,r,0);
 }
 public RuntimeOptions(int m,int c,boolean q,int t,int b,int r,int gpu){
  if(m<0||m>1)throw new IllegalArgumentException("Выберите языковую модель PTQ1_0 или PQ2_0");ModelCatalog.get(m);if(c!=4096&&c!=8192&&c!=16384&&c!=32768&&c!=65536&&c!=131072&&c!=262144)throw new IllegalArgumentException("Недопустимый контекст");
  if(!validThreads(t)||!validThreads(b)||r<0||r>2)throw new IllegalArgumentException("Недопустимые настройки производительности");
  if(gpu!=0&&gpu!=8&&gpu!=16&&gpu!=32&&gpu!=99)throw new IllegalArgumentException("Недопустимое число GPU-слоёв");
  if(gpu>0&&m!=0)throw new IllegalArgumentException("Vulkan пока доступен только для PTQ1_0. Выберите PTQ1_0 или CPU.");
  modelIndex=m;ctxSize=c;kvQ4=q;threads=t;batchThreads=b;thinking=r;gpuLayers=gpu;
 }
 private static boolean validThreads(int t){return t==2||t==4||t==6||t==8;}
 public static RuntimeOptions defaults(int model){return new RuntimeOptions(model,16384,false,4,8,0);}
 public String json(){return "{\"modelIndex\":"+modelIndex+",\"ctxSize\":"+ctxSize+",\"kvQ4\":"+kvQ4+",\"threads\":"+threads+",\"batchThreads\":"+batchThreads+",\"thinking\":"+thinking+",\"gpuLayers\":"+gpuLayers+"}";}
 public void save(File file)throws IOException{
  Properties p=new Properties();p.setProperty("version","2");p.setProperty("model",""+modelIndex);p.setProperty("ctx",""+ctxSize);p.setProperty("q4",""+kvQ4);p.setProperty("threads",""+threads);p.setProperty("batchThreads",""+batchThreads);p.setProperty("thinking",""+thinking);p.setProperty("gpuLayers",""+gpuLayers);
  File temp=new File(file.getPath()+".tmp");try(FileOutputStream o=new FileOutputStream(temp)){p.store(o,"Bonsai Local runtime options");o.getFD().sync();}
  try{Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
 }
 public static RuntimeOptions load(File file,int detectedModel){
  if(!file.isFile()||file.length()>4096)return defaults(detectedModel);
  try(FileInputStream in=new FileInputStream(file)){Properties p=new Properties();p.load(in);
   String version=p.getProperty("version");if(!"1".equals(version)&&!"2".equals(version))return defaults(detectedModel);
   String q=p.getProperty("q4");if(!"true".equals(q)&&!"false".equals(q))return defaults(detectedModel);
   return new RuntimeOptions(Integer.parseInt(p.getProperty("model")),Integer.parseInt(p.getProperty("ctx")),Boolean.parseBoolean(q),Integer.parseInt(p.getProperty("threads")),Integer.parseInt(p.getProperty("batchThreads")),Integer.parseInt(p.getProperty("thinking")),"1".equals(version)?0:Integer.parseInt(p.getProperty("gpuLayers","0")));
  }catch(IOException|RuntimeException e){return defaults(detectedModel);}
 }
}
