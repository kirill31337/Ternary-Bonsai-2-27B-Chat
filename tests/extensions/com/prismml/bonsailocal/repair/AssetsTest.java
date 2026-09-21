package com.prismml.bonsailocal.repair;
import java.io.*;import java.nio.file.*;import java.lang.reflect.*;
public class AssetsTest {
 static int n=0; static void ok(boolean b,String m){if(!b)throw new AssertionError(m);System.out.println("PASS "+m);n++;}
 static void bad(Runnable r,String m){try{r.run();throw new AssertionError(m);}catch(IllegalArgumentException e){ok(true,m);}}
 static Object delete(File ext,File in,int id,boolean busy)throws Exception {
  try{return Class.forName("com.prismml.bonsailocal.repair.ModelFiles").getMethod("delete",File.class,File.class,int.class,boolean.class).invoke(null,ext,in,id,busy);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
 }
 public static void main(String[] args)throws Exception {
  ModelCatalog.Model vision;
  try{vision=ModelCatalog.get(2);}catch(IllegalArgumentException e){throw new AssertionError("Pinned vision projector is missing",e);}
  ok(vision.name.equals("Ternary-Bonsai-2-27B-mmproj-Q8_0.gguf"),"exact projector identity");
  ok(vision.size==629246976L&&vision.sha.equals("6807ede61d570bb86ba34b756a0fa109edc33668604de867c6ea6d8f1d631903"),"projector size and publisher SHA");
  bad(()->new RuntimeOptions(2,16384,false,4,8,0),"projector rejected as language weights");
  Path root=Files.createTempDirectory("bonsai-assets-"), ext=Files.createDirectory(root.resolve("external")), in=Files.createDirectory(root.resolve("internal"));
  for(int id=0;id<3;id++){ModelCatalog.Model m=ModelCatalog.get(id);Files.write(ext.resolve(m.name),new byte[]{1,2,3});Files.write(in.resolve(m.marker),new byte[]{4});}
  ModelCatalog.Model p=ModelCatalog.get(0),q=ModelCatalog.get(1);
  for(String s:new String[]{".partial",".resume",".resume.tmp",".importing"})Files.write(ext.resolve(p.name+s),new byte[]{5});
  Files.write(in.resolve(p.marker+".tmp"),new byte[]{6});Files.write(ext.resolve("other.gguf"),new byte[]{7});Files.write(in.resolve("chat"),new byte[]{8});
  try{delete(ext.toFile(),in.toFile(),0,true);throw new AssertionError("active model deletion allowed");}catch(IOException expected){ok(Files.exists(ext.resolve(p.name)),"busy deletion fails before any removal");}
  long removed=((Number)delete(ext.toFile(),in.toFile(),0,false)).longValue();
  ok(removed==9,"delete counts model plus own metadata/partials");
  ok(!Files.exists(ext.resolve(p.name))&&!Files.exists(in.resolve(p.marker)),"selected PTQ and marker removed");
  ok(Files.size(ext.resolve(q.name))==3&&Files.size(ext.resolve(vision.name))==3,"PQ and vision preserved");
  ok(Files.exists(in.resolve("chat"))&&Files.exists(ext.resolve("other.gguf")),"unrelated data preserved");
  ok(((Number)delete(ext.toFile(),in.toFile(),0,false)).longValue()==0,"repeat deletion no-op");
  Path secret=Files.write(root.resolve("original-browser-file"),new byte[]{9});Files.createSymbolicLink(ext.resolve(p.name),secret);
  try{delete(ext.toFile(),in.toFile(),0,false);throw new AssertionError("symlink allowed");}catch(IOException expected){ok(Files.exists(secret),"symlink cannot delete browser original");}
  System.out.println("ASSETS CHECKS "+n);
 }
}
