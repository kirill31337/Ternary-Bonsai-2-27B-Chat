package com.prismml.bonsailocal.repair;
import java.util.*;
/** Small bounded JSON codec for native MCP (no Android/runtime dependency). */
public final class MiniJson {
 private final String s;private int p,nodes;
 private MiniJson(String s){if(s==null||s.length()>2*1024*1024)throw new IllegalArgumentException("JSON too large");this.s=s;}
 public static Object parse(String s){MiniJson j=new MiniJson(s);Object v=j.value(0);j.ws();if(j.p!=s.length())throw j.err();return v;}
 @SuppressWarnings("unchecked") public static Map<String,Object> object(Object x){if(!(x instanceof Map))throw new IllegalArgumentException("Expected JSON object");return (Map<String,Object>)x;}
 public static String string(Map<String,Object> m,String k,String def){Object o=m.get(k);if(o==null)return def;if(!(o instanceof String))throw new IllegalArgumentException("Expected string: "+k);return (String)o;}
 public static Map<String,Object> map(Object... items){Map<String,Object> m=new LinkedHashMap<>();if(items.length%2!=0)throw new IllegalArgumentException();for(int i=0;i<items.length;i+=2)m.put((String)items[i],items[i+1]);return m;}
 private IllegalArgumentException err(){return new IllegalArgumentException("Invalid JSON at "+p);}
 private void ws(){while(p<s.length()&&(s.charAt(p)==' '||s.charAt(p)=='\t'||s.charAt(p)=='\n'||s.charAt(p)=='\r'))p++;}
 private boolean take(char c){ws();if(p<s.length()&&s.charAt(p)==c){p++;return true;}return false;}
 private Object value(int depth){if(depth>32||++nodes>30000)throw new IllegalArgumentException("JSON nesting/size limit");ws();if(p>=s.length())throw err();char c=s.charAt(p);
  if(c=='"')return str();
  if(c=='{'){p++;Map<String,Object> m=new LinkedHashMap<>();if(take('}'))return m;do{ws();if(p>=s.length()||s.charAt(p)!='"')throw err();String k=str();if(!take(':')||m.containsKey(k))throw err();m.put(k,value(depth+1));if(take('}'))return m;}while(take(','));throw err();}
  if(c=='['){p++;List<Object> a=new ArrayList<>();if(take(']'))return a;do{a.add(value(depth+1));if(take(']'))return a;}while(take(','));throw err();}
  for(String lit:new String[]{"true","false","null"})if(s.startsWith(lit,p)){p+=lit.length();return lit.equals("null")?null:Boolean.valueOf(lit);}
  int start=p;if(c=='-')p++;if(p>=s.length())throw err();if(s.charAt(p)=='0')p++;else {if(s.charAt(p)<'1'||s.charAt(p)>'9')throw err();digits();}
  boolean decimal=false;if(p<s.length()&&s.charAt(p)=='.'){decimal=true;p++;int before=p;digits();if(p==before)throw err();}
  if(p<s.length()&&(s.charAt(p)=='e'||s.charAt(p)=='E')){decimal=true;p++;if(p<s.length()&&(s.charAt(p)=='+'||s.charAt(p)=='-'))p++;int before=p;digits();if(before==p)throw err();}
  String text=s.substring(start,p);try{if(!decimal)return Long.valueOf(text);double x=Double.parseDouble(text);if(Double.isNaN(x)||Double.isInfinite(x))throw err();return x;}catch(NumberFormatException e){throw err();}
 }
 private void digits(){while(p<s.length()&&s.charAt(p)>='0'&&s.charAt(p)<='9')p++;}
 private String str(){p++;StringBuilder b=new StringBuilder();while(p<s.length()){char c=s.charAt(p++);if(c=='"')return b.toString();if(c<32)throw err();if(c=='\\'){if(p>=s.length())throw err();c=s.charAt(p++);switch(c){case '"':case '\\':case '/':b.append(c);break;case 'b':b.append('\b');break;case 'f':b.append('\f');break;case 'n':b.append('\n');break;case 'r':b.append('\r');break;case 't':b.append('\t');break;case 'u':if(p+4>s.length())throw err();try{b.append((char)Integer.parseInt(s.substring(p,p+4),16));}catch(NumberFormatException e){throw err();}p+=4;break;default:throw err();}}else b.append(c);}throw err();}
 public static String write(Object o){StringBuilder b=new StringBuilder();write(o,b,0);return b.toString();}
 private static void write(Object o,StringBuilder b,int depth){if(depth>32)throw new IllegalArgumentException("JSON nesting limit");if(o==null){b.append("null");return;}if(o instanceof String){b.append('"');String s=(String)o;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'||c=='\\')b.append('\\').append(c);else if(c<32||c=='<'||c=='>'||c=='&'||c=='\u2028'||c=='\u2029')b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else b.append(c);}b.append('"');}
  else if(o instanceof Number){if(o.toString().equals("NaN")||o.toString().contains("Infinity"))throw new IllegalArgumentException("Non-finite JSON number");b.append(o);}else if(o instanceof Boolean)b.append(o);
  else if(o instanceof Map){b.append('{');boolean first=true;for(Map.Entry<?,?> e:((Map<?,?>)o).entrySet()){if(!(e.getKey() instanceof String))throw new IllegalArgumentException();if(!first)b.append(',');first=false;write(e.getKey(),b,depth+1);b.append(':');write(e.getValue(),b,depth+1);}b.append('}');}
  else if(o instanceof Iterable){b.append('[');boolean first=true;for(Object v:(Iterable<?>)o){if(!first)b.append(',');first=false;write(v,b,depth+1);}b.append(']');}else throw new IllegalArgumentException("Unsupported JSON value");
 }
}
