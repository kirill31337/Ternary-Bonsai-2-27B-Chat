package com.prismml.bonsailocal.repair;
import java.io.*;import java.net.*;import java.nio.charset.*;import java.util.*;import java.util.zip.GZIPOutputStream;
public class WebTest {
 static int n;static void ok(boolean b,String m){if(!b)throw new AssertionError(m);System.out.println("PASS "+m);n++;}
 interface Checked {void run()throws Exception;}
 static void bad(Checked r,String m)throws Exception{try{r.run();throw new AssertionError(m);}catch(IOException|IllegalArgumentException expected){ok(true,m);}}
 static class S implements WebTools.Settings {boolean on=true;String prov="duckduckgo",token="key-fixture";public boolean enabled(){return on;}public String provider(){return prov;}public String key(){return token;}}
 static byte[] b(String s){return s.getBytes(StandardCharsets.UTF_8);}
 static final String DDG="<html><a class='result__a' href='//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fpage&amp;rut=x'>A &amp; <b>B</b></a><a class='result__snippet'>Snippet &#x41;</a><a class='result__a' href='https://example.net/'>Second</a><a class='result__a' href='https://example.com/page'>Duplicate</a></html>";
 static class FakeNet implements WebTools.Transport {int calls;String last;Map<String,String> headers;WebNet.Response response;
  FakeNet()throws Exception{response=new WebNet.Response(200,Collections.singletonMap("content-type","text/html"),b(DDG),new URI("https://example.com/"));}
  public WebNet.Response get(String u,Map<String,String> h){calls++;last=u;headers=h;return response;}}
 static WebNet.Response wire(String s)throws Exception {return WebNet.readResponse(new ByteArrayInputStream(b(s)),new URI("https://example.com/"));}
 static WebNet.Response rpc(McpServer m,String method,String body,String origin,String path)throws Exception {URI u=new URI(m.url());try(Socket sock=new Socket("127.0.0.1",u.getPort())){sock.setSoTimeout(5000);String msg=method+" "+(path==null?u.getRawPath():path)+" HTTP/1.1\r\nHost: 127.0.0.1:"+u.getPort()+"\r\n"+(origin==null?"":"Origin: "+origin+"\r\n")+"Content-Type: application/json\r\nContent-Length: "+b(body).length+"\r\n\r\n"+body;sock.getOutputStream().write(b(msg));sock.getOutputStream().flush();return WebNet.readResponse(new BufferedInputStream(sock.getInputStream()),u);}}
 public static void main(String[]a)throws Exception{
  Object o=MiniJson.parse("{\"id\":42,\"text\":\"Привет \\uD83D\\uDE00\",\"a\":[true,false,null,-1.25e2]}");ok(MiniJson.object(MiniJson.parse(MiniJson.write(o))).get("id").equals(42L),"JSON nested unicode roundtrip and integer IDs");
  for(String s:new String[]{"{\"x\":1,\"x\":2}","[1,]","01","1e","1e9999","{bad}","true x","\"\\q\"","\"line\n\""})bad(()->MiniJson.parse(s),"malformed JSON rejected: "+s.replace('\n',' '));
  String deep=String.join("",Collections.nCopies(40,"["))+"1"+String.join("",Collections.nCopies(40,"]"));bad(()->MiniJson.parse(deep),"JSON nesting bounded");
  ok(!MiniJson.write("</script>\u2028").contains("<"),"JSON cannot terminate bootstrap script");
  for(String ip:new String[]{"0.0.0.0","127.0.0.1","10.1.2.3","172.16.0.1","192.168.1.1","169.254.169.254","100.64.1.1","198.18.0.1","192.0.2.1","198.51.100.1","203.0.113.1","224.0.0.1","255.255.255.255","::1","::","fd00::1","fe80::1","2001:db8::1","2002:7f00:1::","::ffff:127.0.0.1"})ok(!WebNet.publicAddress(InetAddress.getByName(ip)),"blocked nonpublic address "+ip);
  ok(WebNet.publicAddress(InetAddress.getByName("8.8.8.8"))&&WebNet.publicAddress(InetAddress.getByName("2606:4700:4700::1111")),"public IPv4 and IPv6 allowed");
  for(String u:new String[]{"http://example.com/","file:///etc/passwd","https://localhost/","https://router.local/","https://user:pass@example.com/","https://example.com:8080/","https://example.com/\r\nX:test","https://example.com\\@evil.test/"})bad(()->WebNet.uri(u),"unsafe URL rejected "+u.replace('\r',' ').replace('\n',' '));
  ok(WebNet.uri("https://example.com/a?q=x%20y").getHost().equals("example.com"),"public HTTPS URL accepted");
  ok(wire("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabc").text().equals("abc"),"Content-Length HTTP response");
  ok(wire("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n2\r\nde\r\n0\r\n\r\n").text().equals("abcde"),"chunked HTTP response including final trailers");
  bad(()->wire("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nTransfer-Encoding: chunked\r\n\r\nx"),"ambiguous framing rejected");
  bad(()->wire("HTTP/1.1 200 OK\r\nContent-Length: 4\r\nContent-Length: 4\r\n\r\nxxxx"),"duplicate framing rejected");
  bad(()->wire("HTTP/1.1 200 OK\r\nContent-Length: 8\r\n\r\nabc"),"truncated response rejected");
  bad(()->wire("HTTP/1.1 200 OK\r\nContent-Length: 3000000\r\n\r\n"),"oversized response rejected before allocation");
  ByteArrayOutputStream gz=new ByteArrayOutputStream();try(GZIPOutputStream z=new GZIPOutputStream(gz)){z.write(b("gzip works"));}ByteArrayOutputStream packet=new ByteArrayOutputStream();packet.write(b("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: "+gz.size()+"\r\n\r\n"));packet.write(gz.toByteArray());ok(WebNet.readResponse(new ByteArrayInputStream(packet.toByteArray()),new URI("https://example.com")).text().equals("gzip works"),"gzip response decoded");
  ok(WebTools.text("<script>steal()</script><style>x</style><h1>Hello &amp; &#x41;</h1><!--hidden--><p>world</p>").equals("Hello & A world"),"HTML scripts/styles/comments removed; entities decoded");
  List<Object> results=WebTools.ddg(DDG,5);ok(results.size()==2&&MiniJson.object(results.get(0)).get("url").equals("https://example.com/page"),"DDG result extraction, unwrapping and deduplication");
  S settings=new S();FakeNet f=new FakeNet();WebTools tools=new WebTools(settings,f);
  ok(((List<?>)MiniJson.object(tools.execute("web_search",MiniJson.map("query","тест"))).get("sources")).size()==2&&f.last.contains("%D1%82"),"search uses actual query and parsed source results");
  settings.on=false;int before=f.calls;bad(()->tools.search("x",3),"off mode blocks network");ok(f.calls==before,"off mode makes no transport call");settings.on=true;
  bad(()->tools.execute("exec",MiniJson.map()),"no shell tool exposed");bad(()->tools.execute("web_search",MiniJson.map("query","x","count",99)),"search limits validated");
  settings.prov="brave";f.response=new WebNet.Response(200,Collections.singletonMap("content-type","application/json"),b("{\"web\":{\"results\":[{\"url\":\"https://example.com/\",\"title\":\"Title\",\"description\":\"Desc\"}]}}"),new URI("https://api.search.brave.com/"));String br=MiniJson.write(tools.search("test",1));ok(f.headers.get("X-Subscription-Token").equals("key-fixture")&&!br.contains("key-fixture")&&!f.last.contains("key-fixture"),"Brave key goes in header only, not result or URL");
  settings.prov="duckduckgo";f.response=new WebNet.Response(202,Collections.singletonMap("content-type","text/html"),b("anomaly-modal"),new URI("https://html.duckduckgo.com/"));bad(()->tools.search("x",3),"CAPTCHA fails explicitly, no bypass");
  f.response=new WebNet.Response(200,Collections.singletonMap("content-type","text/html"),b("<title>T</title><p>"+String.join("",Collections.nCopies(1000,"abc "))+"</p>"),new URI("https://example.com/"));Map<String,Object> read=MiniJson.object(tools.fetch("https://example.com/",512));ok(Boolean.TRUE.equals(read.get("truncated"))&&((String)read.get("text")).length()==512,"page text bounded and labelled truncated");
  f.response=new WebNet.Response(200,Collections.singletonMap("content-type","application/pdf"),b("PDF"),new URI("https://example.com/"));bad(()->tools.fetch("https://example.com",512),"binary content not converted to fake page text");
  f.response=new WebNet.Response(200,Collections.singletonMap("content-type","text/html"),b(DDG),new URI("https://html.duckduckgo.com/"));
  try(McpServer m=new McpServer(tools,settings)){
   String init="{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}";
   WebNet.Response r=rpc(m,"POST",init,McpServer.ORIGIN,null);ok(r.status==200&&r.text().contains("2025-06-18")&&MiniJson.object(MiniJson.parse(r.text())).get("id").equals(0L),"real socket MCP initialize and ID preservation");
   ok(rpc(m,"POST",init,"https://evil.test",null).status==403,"foreign browser Origin rejected");ok(rpc(m,"POST",init,null,null).status==403,"absent Origin rejected");ok(rpc(m,"POST",init,McpServer.ORIGIN,"/mcp/guess").status==403,"wrong secret rejected");
   ok(rpc(m,"GET","",McpServer.ORIGIN,null).status==405,"MCP GET correctly says SSE unsupported");ok(rpc(m,"OPTIONS","",McpServer.ORIGIN,null).status==204,"CORS preflight answered");
   ok(rpc(m,"POST","{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",McpServer.ORIGIN,null).status==202,"MCP notification returns 202 without JSON response");
   r=rpc(m,"POST","{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}",McpServer.ORIGIN,null);ok(r.text().contains("web_search")&&r.text().contains("web_fetch")&&!r.text().contains("exec_shell"),"MCP advertises exactly read-only web tools");
   r=rpc(m,"POST","{\"jsonrpc\":\"2.0\",\"id\":\"search\",\"method\":\"tools/call\",\"params\":{\"name\":\"web_search\",\"arguments\":{\"query\":\"test\"}}}",McpServer.ORIGIN,null);ok(r.text().contains("example.com/page")&&r.text().contains("\"isError\":false"),"MCP actual tools/call returns provider source result");
   settings.on=false;r=rpc(m,"POST","{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}",McpServer.ORIGIN,null);ok(r.text().contains("\"tools\":[]"),"disabled mode removes callable tool catalogue");settings.on=true;
   r=rpc(m,"POST","{bad}",McpServer.ORIGIN,null);ok(r.text().contains("-32700"),"malformed MCP JSON produces parse error and server survives");
  }
  System.out.println("WEB CHECKS "+n);
 }
}
