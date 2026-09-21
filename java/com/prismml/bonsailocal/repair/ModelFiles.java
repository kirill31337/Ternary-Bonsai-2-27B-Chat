package com.prismml.bonsailocal.repair;
import java.io.*;import java.nio.file.*;import java.util.*;
/** No caller-selected paths. Removal is restricted to a single pinned catalogue asset. */
public final class ModelFiles {
 private ModelFiles(){}
 private static List<File> files(File ext,File in,int id)throws IOException {
  if(ext==null||in==null)throw new IOException("Хранилище недоступно");
  ModelCatalog.Model m=ModelCatalog.get(id);List<File> out=new ArrayList<>();
  for(String suffix:new String[]{"",".partial",".resume",".resume.tmp",".importing"})out.add(checked(ext,m.name+suffix));
  out.add(checked(in,m.marker));out.add(checked(in,m.marker+".tmp"));return out;
 }
 private static File checked(File root,String name)throws IOException {
  File base=root.getCanonicalFile(),f=new File(base,name);
  // Reject links even inside the storage directory; never follow a link to somebody else's file.
  if(Files.isSymbolicLink(f.toPath())||!f.getCanonicalFile().getParentFile().equals(base)||f.isDirectory())throw new IOException("Небезопасный путь: удаление отменено");
  return f;
 }
 public static long bytes(File ext,File in,int id)throws IOException {long n=0;for(File f:files(ext,in,id))n+=f.length();return n;}
 public static synchronized long delete(File ext,File in,int id,boolean busy)throws IOException {
  if(busy)throw new IOException("Сначала остановите движок и дождитесь завершения передачи.");
  List<File> all=files(ext,in,id); // Validate *all* paths before touching any file.
  long removed=0;
  // Remove markers first, so an interrupted operation cannot leave a false readiness marker.
  Collections.reverse(all);
  for(File f:all){long n=f.length();if(Files.deleteIfExists(f.toPath()))removed+=n;}
  return removed;
 }
}
