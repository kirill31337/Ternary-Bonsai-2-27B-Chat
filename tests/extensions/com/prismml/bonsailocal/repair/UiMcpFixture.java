package com.prismml.bonsailocal.repair;
import java.io.*;import java.net.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Actual MCP server, mocked search-provider HTTP boundary for browser integration testing. */
public class UiMcpFixture {
 public static void main(String[]args)throws Exception{
  WebTools.Settings settings=new WebTools.Settings(){public boolean enabled(){return true;}public String provider(){return "duckduckgo";}public String key(){return "";}};
  WebTools tools=new WebTools(settings,new WebTools.Transport(){public WebNet.Response get(String u,Map<String,String> h)throws IOException{return new WebNet.Response(200,Collections.singletonMap("content-type","text/html"),"<a class='result__a' href='https://example.com/source'>Fixture source</a><a class='result__snippet'>Fixture search result, not live Internet.</a>".getBytes(StandardCharsets.UTF_8),URI.create("https://html.duckduckgo.com/"));}});
  try(McpServer server=new McpServer(tools,settings)){Path dir=Paths.get(args[0]);Files.createDirectories(dir);Files.write(dir.resolve("bootstrap.html"),ChatBootstrap.html(server.url(),Integer.parseInt(args[1])).getBytes(StandardCharsets.UTF_8));Files.write(dir.resolve("endpoint.txt"),server.url().getBytes(StandardCharsets.UTF_8));System.out.println("READY");System.out.flush();Thread.sleep(600000);}
 }
}
