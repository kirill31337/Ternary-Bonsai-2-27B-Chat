package com.prismml.bonsailocal.repair;
import java.io.*;
/** Immutable publisher-pinned language/projector identities. No arbitrary paths/URLs from WebView. */
public final class ModelCatalog {
 private ModelCatalog(){}
 public static final String REVISION="6ed5e12bf84b7a63069882c91dd9e9218647d17b";
 public static final class Model {
  public final int index; public final String quant,name,marker,sha,url; public final long size;
  private Model(int i,String q,String m,long n,String hash){index=i;quant=q;name="Ternary-Bonsai-2-27B-"+q+".gguf";marker=m;size=n;sha=hash;url="https://huggingface.co/prism-ml/Ternary-Bonsai-2-27B-gguf/resolve/"+REVISION+"/"+name+"?download=true";}
  public boolean ready(File ext,File internal){
   if(ext==null||internal==null)return false;
   File file=new File(ext,name),mark=new File(internal,marker);
   if(!file.isFile()||file.length()!=size||!mark.isFile()||mark.length()>96)return false;
   try(BufferedReader in=new BufferedReader(new FileReader(mark));FileInputStream f=new FileInputStream(file)){
    String h=in.readLine();if(!sha.equals(h)&&!(index==0&&"ok".equals(h)))return false;
    return f.read()=='G'&&f.read()=='G'&&f.read()=='U'&&f.read()=='F';
   }catch(IOException ex){return false;}
  }
 }
 private static final Model PTQ=new Model(0,"PTQ1_0","model.complete",5946648928L,"53107f530aa52eb00912263ab1ee29bd199261c87cd7b4ad4ca1318c1fe33ee3");
 private static final Model PQ=new Model(1,"PQ2_0","pq2.complete",7206168928L,"3907dc1658db1f78a9826bf8d5bcb8dc65db0d466388937af57f2294fae62ec1");
 private static final Model VISION=new Model(2,"mmproj-Q8_0","vision.complete",629246976L,"6807ede61d570bb86ba34b756a0fa109edc33668604de867c6ea6d8f1d631903");
 public static Model get(int id){if(id==0)return PTQ;if(id==1)return PQ;if(id==2)return VISION;throw new IllegalArgumentException("Неизвестный формат модели");}
}
