package com.prismml.bonsailocal.repair;

import java.io.*;import java.net.*;import java.nio.charset.*;import java.util.*;import java.util.concurrent.*;import java.util.zip.GZIPInputStream;
import javax.net.ssl.*;

/** Public HTTPS GET only. DNS is validated then the actual socket is pinned to that IP.
 * TLS still authenticates the original hostname (including SNI). Never trusts all certificates.
 * No cookies, proxy tunnelling, arbitrary ports, JavaScript, uploads or device-file access.
 */
public final class WebNet {
 public static final int MAX_BODY=2*1024*1024;
 public interface Policy {boolean enabled();}
 public static final class Response {
  public final int status;public final Map<String,String> headers;public final byte[] body;public final URI uri;
  Response(int s,Map<String,String> h,byte[] b,URI u){status=s;headers=h;body=b;uri=u;}
  public String text(){String ct=headers.getOrDefault("content-type","");Charset cs=StandardCharsets.UTF_8;int at=ct.toLowerCase(Locale.ROOT).indexOf("charset=");if(at>=0){String name=ct.substring(at+8).split("[; ]",2)[0].replace("\"","").trim();try{cs=Charset.forName(name);}catch(Exception ignored){}}return new String(body,cs);}
 }
 private static final ExecutorService DNS=new ThreadPoolExecutor(0,2,15,TimeUnit.SECONDS,new SynchronousQueue<Runnable>(),new ThreadFactory(){public Thread newThread(Runnable r){Thread t=new Thread(r,"Bonsai-DNS");t.setDaemon(true);return t;}});
 private final Policy policy;
 public WebNet(Policy p){policy=p;}
 public static URI uri(String text)throws IOException {
  if(text==null||text.length()>2048)throw new IOException("URL отсутствует или слишком длинный");
  for(int i=0;i<text.length();i++)if(text.charAt(i)<33||text.charAt(i)==127||text.charAt(i)=='\\')throw new IOException("Некорректный URL");
  try{URI u=new URI(text);String host=u.getHost();
   if(!"https".equalsIgnoreCase(u.getScheme())||host==null||host.length()>253||u.getRawUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||u.getRawFragment()!=null)throw new IOException("Разрешены только публичные HTTPS-страницы без логина, фрагмента и нестандартного порта");
   String h=host.toLowerCase(Locale.ROOT);if(h.equals("localhost")||h.endsWith(".localhost")||h.endsWith(".local")||h.endsWith(".internal")||h.endsWith(".home")||h.endsWith(".lan")||h.indexOf('%')>=0)throw new IOException("Локальные адреса запрещены");
   return u;
  }catch(URISyntaxException e){throw new IOException("Некорректный URL");}
 }
 public static boolean publicAddress(InetAddress a){
  if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return false;
  byte[] ip=a.getAddress();int x=ip[0]&255,y=ip[1]&255;
  if(ip.length==4){if(x==0||x==10||x==127||x>=224||x==169&&y==254||x==172&&y>=16&&y<=31||x==192&&y==168||x==100&&y>=64&&y<=127||x==198&&(y==18||y==19))return false;
   // Documentation, benchmarking and special-purpose ranges are not Internet destinations.
   if(x==192&&(y==0||y==2)||x==198&&y==51&&(ip[2]&255)==100||x==203&&y==0&&(ip[2]&255)==113)return false;return true;}
  if(ip.length!=16||(x&0xe0)!=0x20)return false; // only global-unicast 2000::/3
  if(x==0x20&&y==2)return false; // 6to4 can tunnel to private v4
  if(x==0x20&&y==1){int z=ip[2]&255,w=ip[3]&255;if(z==0||z==0x0d&&w==0xb8)return false;} // Teredo/special + documentation
  return true;
 }
 private int timeout(long deadline)throws IOException {if(!policy.enabled())throw new IOException("Интернет выключен в настройках Bonsai");long left=deadline-System.currentTimeMillis();if(left<=0)throw new SocketTimeoutException("Лимит времени веб-запроса");return (int)Math.min(left,8000);}
 private InetAddress[] resolve(final String host,long deadline)throws IOException {
  Future<InetAddress[]> f;try{f=DNS.submit(new Callable<InetAddress[]>(){public InetAddress[] call()throws Exception{return InetAddress.getAllByName(host);}});}catch(RejectedExecutionException e){throw new IOException("DNS занят, повторите позже");}
  InetAddress[] ips;try{ips=f.get(Math.min(5000,timeout(deadline)),TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();f.cancel(true);throw new IOException("Запрос отменён");}catch(Exception e){f.cancel(true);throw new IOException("Не удалось разрешить DNS-имя");}
  if(ips.length==0)throw new IOException("DNS не вернул адрес");for(InetAddress a:ips)if(!publicAddress(a))throw new IOException("Доступ к непубличному IP запрещён");return ips;
 }
 public Response get(String url,Map<String,String> extra)throws IOException {
  URI current=uri(url);long deadline=System.currentTimeMillis()+25000;
  for(int hop=0;hop<=4;hop++){
   timeout(deadline);Response r=once(current,extra,deadline);
   if(r.status==301||r.status==302||r.status==303||r.status==307||r.status==308){if(hop==4)throw new IOException("Слишком много перенаправлений");String location=r.headers.get("location");if(location==null)throw new IOException("Перенаправление без Location");URI next=uri(current.resolve(location).toASCIIString());
    if(!extra.isEmpty()&&!next.getHost().equalsIgnoreCase(current.getHost()))throw new IOException("Перенаправление с API-ключом на другой сервер запрещено");
    current=next;continue;
   }
   return r;
  }throw new IOException("Слишком много перенаправлений");
 }
 private Response once(URI u,Map<String,String> extra,long deadline)throws IOException {
  String host=u.getHost();if(host.startsWith("[")&&host.endsWith("]"))host=host.substring(1,host.length()-1);
  InetAddress[] ips=resolve(host,deadline);IOException last=null;
  for(int attempt=0;attempt<Math.min(ips.length,3);attempt++){
   Socket tcp=new Socket();SSLSocket tls=null;
   try{tcp.connect(new InetSocketAddress(ips[attempt],443),timeout(deadline));tcp.setSoTimeout(timeout(deadline));
    tls=secure(tcp,host,(SSLSocketFactory)SSLSocketFactory.getDefault());tls.setSoTimeout(timeout(deadline));tls.startHandshake();
    String path=u.getRawPath();if(path==null||path.isEmpty())path="/";if(u.getRawQuery()!=null)path+="?"+u.getRawQuery();
    StringBuilder req=new StringBuilder("GET ").append(path).append(" HTTP/1.1\r\nHost: ").append(u.getHost()).append("\r\nUser-Agent: BonsaiLocal/1.0.0\r\nAccept: text/html,application/json,text/plain;q=0.9\r\nAccept-Encoding: gzip\r\nConnection: close\r\n");
    for(Map.Entry<String,String> e:extra.entrySet()){if(!"X-Subscription-Token".equals(e.getKey())||!"api.search.brave.com".equalsIgnoreCase(u.getHost())||!e.getValue().matches("[!-~]{1,512}"))throw new IOException("Недопустимый API-заголовок");req.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");}
    req.append("\r\n");tls.getOutputStream().write(req.toString().getBytes(StandardCharsets.US_ASCII));tls.getOutputStream().flush();
    final SSLSocket active=tls;final InputStream raw=tls.getInputStream();
    InputStream bounded=new FilterInputStream(raw){public int read()throws IOException{active.setSoTimeout(timeout(deadline));return super.read();}public int read(byte[] b,int o,int n)throws IOException{active.setSoTimeout(timeout(deadline));return in.read(b,o,n);}};
    Response response=readResponse(new BufferedInputStream(bounded),u);
    timeout(deadline);return response;
   }catch(IOException e){last=e;}finally{if(tls!=null)try{tls.close();}catch(IOException ignored){}try{tcp.close();}catch(IOException ignored){}}
  }
  throw last==null?new IOException("Не удалось подключиться к HTTPS-сайту"):last;
 }
 /** Separate socket upgrade permits an actual TLS identity test without weakening public IP policy. */
 static SSLSocket secure(Socket tcp,String host,SSLSocketFactory factory)throws IOException {
  SSLSocket ssl=(SSLSocket)factory.createSocket(tcp,host,443,true);SSLParameters p=ssl.getSSLParameters();p.setEndpointIdentificationAlgorithm("HTTPS");ssl.setSSLParameters(p);return ssl;
 }
 static String line(InputStream in,int cap)throws IOException {ByteArrayOutputStream b=new ByteArrayOutputStream();int c;while((c=in.read())!=-1){if(c=='\n'){byte[] a=b.toByteArray();if(a.length==0||a[a.length-1]!='\r')throw new IOException("HTTP line endings");return new String(a,0,a.length-1,StandardCharsets.ISO_8859_1);}if(b.size()>=cap)throw new IOException("HTTP header limit");b.write(c);}throw new EOFException("Incomplete HTTP headers");}
 static Map<String,String> headers(InputStream in)throws IOException {Map<String,String> h=new LinkedHashMap<>();int total=0;for(int n=0;n<100;n++){String l=line(in,8192);total+=l.length();if(total>32768)throw new IOException("HTTP headers too large");if(l.isEmpty())return h;int c=l.indexOf(':');if(c<1||!l.substring(0,c).matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+"))throw new IOException("Invalid HTTP header");String key=l.substring(0,c).toLowerCase(Locale.ROOT),v=l.substring(c+1).trim();if(v.indexOf('\r')>=0)throw new IOException("Invalid HTTP header");if(h.containsKey(key)&&(key.equals("content-length")||key.equals("transfer-encoding")||key.equals("host")||key.equals("origin")))throw new IOException("Ambiguous HTTP header");h.put(key,v);}throw new IOException("Too many HTTP headers");}
 static Response readResponse(InputStream in,URI u)throws IOException {
  String first=line(in,8192);if(!first.matches("HTTP/1\\.[01] [0-9]{3}( .*)?"))throw new IOException("Некорректный ответ HTTP");int status=Integer.parseInt(first.substring(9,12));Map<String,String> h=headers(in);
  if(status<200)throw new IOException("Неподдерживаемый промежуточный ответ HTTP");
  if(status>=300&&status<400)return new Response(status,h,new byte[0],u);
  byte[] b=readBody(in,h,MAX_BODY);String enc=h.getOrDefault("content-encoding","");
  if(enc.equalsIgnoreCase("gzip"))try(GZIPInputStream gz=new GZIPInputStream(new ByteArrayInputStream(b))){b=limited(gz,MAX_BODY);}else if(!enc.isEmpty()&&!enc.equalsIgnoreCase("identity"))throw new IOException("Неподдерживаемое сжатие страницы");
  return new Response(status,h,b,u);
 }
 static byte[] readBody(InputStream in,Map<String,String> h,int max)throws IOException {
  String te=h.get("transfer-encoding"),cl=h.get("content-length");if(te!=null&&cl!=null)throw new IOException("Ambiguous HTTP framing");
  if(te!=null){if(!"chunked".equalsIgnoreCase(te))throw new IOException("Unsupported transfer encoding");ByteArrayOutputStream b=new ByteArrayOutputStream();for(int chunks=0;chunks<100000;chunks++){
    String l=line(in,1024);String number=l.split(";",2)[0].trim();if(!number.matches("[0-9a-fA-F]{1,8}"))throw new IOException("Bad HTTP chunk size");long size=Long.parseLong(number,16);
    if(size==0){headers(in);return b.toByteArray();}if(size>max-b.size())throw new IOException("Страница превышает лимит 2 МБ");copyExactly(in,b,(int)size);if(in.read()!='\r'||in.read()!='\n')throw new IOException("Bad chunk ending");
   }throw new IOException("Too many chunks");}
  if(cl!=null){long n;try{if(!cl.matches("[0-9]{1,10}"))throw new NumberFormatException();n=Long.parseLong(cl);}catch(NumberFormatException e){throw new IOException("Invalid Content-Length");}if(n>max)throw new IOException("Страница превышает лимит 2 МБ");ByteArrayOutputStream b=new ByteArrayOutputStream((int)n);copyExactly(in,b,(int)n);return b.toByteArray();}
  return limited(in,max);
 }
 static void copyExactly(InputStream in,OutputStream out,int n)throws IOException {byte[] b=new byte[8192];while(n>0){int k=in.read(b,0,Math.min(n,b.length));if(k<0)throw new EOFException("Incomplete HTTP body");if(k==0)continue;out.write(b,0,k);n-=k;}}
 static byte[] limited(InputStream in,int max)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];for(int n;(n=in.read(buf))!=-1;){if(n==0)continue;if(n>max-out.size())throw new IOException("Ответ слишком большой");out.write(buf,0,n);}return out.toByteArray();}
}
