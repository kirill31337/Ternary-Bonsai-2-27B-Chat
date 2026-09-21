package com.prismml.bonsailocal.repair;
import java.nio.file.*;
/** The UI may permit a new multi-GB load only after the previous process exits. */
public final class ShutdownRegression {
 public static void main(String[] a)throws Exception {
  Path root=Files.createTempDirectory("bonsai-stop-"),ext=Files.createDirectory(root.resolve("ext")),libs=Files.createDirectory(root.resolve("lib")),files=Files.createDirectory(root.resolve("files"));
  Path started=root.resolve("started"),ended=root.resolve("ended"),exe=libs.resolve("libllama_server_exec.so");
  Files.writeString(exe,"#!/usr/bin/python3\nimport signal,time,sys\ndef stop(s,f):\n time.sleep(0.9)\n open('"+ended+"','w').write('terminated')\n sys.exit(0)\nsignal.signal(signal.SIGTERM,stop)\nopen('"+started+"','w').write('started')\nwhile True: time.sleep(.1)\n");exe.toFile().setExecutable(true);
  Bridge b=new Bridge();Bridge.init(new Object(),ext.toString(),libs.toString(),files.toString());
  try{b.selfTest();for(int i=0;i<100&&!Files.exists(started);i++)Thread.sleep(30);if(!Files.exists(started))throw new AssertionError("fixture process did not launch");
   b.stop();for(int i=0;i<200;i++){if(b.status().contains("\"state\":0,")&&b.status().contains("\"pending\":0"))break;Thread.sleep(30);}
   if(!Files.exists(ended))throw new AssertionError("Stop returned to idle before the previous process freed memory");
   if(!b.configureOptions(1,16384,false,4,8,0))throw new AssertionError("Unable to reconfigure after stop");
   System.out.println("PASS stop waits for process termination before permitting model/configuration changes");
  }finally{Bridge.shutdown();}
 }
}
