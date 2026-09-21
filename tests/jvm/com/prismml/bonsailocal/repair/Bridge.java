package com.prismml.bonsailocal.repair;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;

/** Desktop JVM harness for the production JNI library. Android services are deliberately absent. */
public class Bridge {
    static { System.load(System.getProperty("bonsai.lib")); }
    public static native void init(Object context,String external,String libraries,String files);
    public static native void shutdown();
    public native boolean configureVision(boolean value);public native String status();
    public native void start();
    public native void stop();
    public native void selfTest();
    public native boolean configureOptions(int model,int context,boolean q4,int threads,int batchThreads,int thinking);
    static int passed;
    static void ok(boolean b,String name) {
        if(!b)throw new AssertionError(name);
        System.out.println("PASS "+name);passed++;
    }
    static boolean hasState(Bridge b,int s){return b.status().contains("\"state\":"+s+",");}
    static void await(Bridge b,int s) throws Exception {
        for(int i=0;i<100;i++){if(hasState(b,s))return;Thread.sleep(100);}
        throw new AssertionError("Expected state "+s+" got "+b.status());
    }
    static void awaitResult(Bridge b,int state,int exit) throws Exception {
        // A short-lived child may finish between polls; assert the stable outcome,
        // not observation of the transient RUNNING state.
        for(int i=0;i<100;i++){
            String status=b.status();
            if(status.contains("\"state\":"+state+",") && status.contains("\"exit\":"+exit+","))return;
            Thread.sleep(100);
        }
        throw new AssertionError("Expected state "+state+" / exit "+exit+" got "+b.status());
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("bonsai-jni-test-");
        Path ext=Files.createDirectory(root.resolve("external"));
        Path libs=Files.createDirectory(root.resolve("libraries"));
        Path logs=Files.createDirectory(root.resolve("files"));
        Bridge b=new Bridge();
        try {
            boolean caught=false;
            try{init(null,ext.toString(),libs.toString(),logs.toString());}catch(IllegalArgumentException e){caught=true;}
            ok(caught,"null context rejected without JVM crash");
            caught=false;
            try{init(new Object(),null,libs.toString(),logs.toString());}catch(IllegalArgumentException e){caught=true;}
            ok(caught,"null external storage rejected without JVM crash");
            caught=false;
            try{init(new Object(),"x".repeat(3000),libs.toString(),logs.toString());}catch(IllegalArgumentException e){caught=true;}
            ok(caught,"overlong path rejected");
            init(new Object(),ext.toString(),libs.toString(),logs.toString());
            Thread.sleep(800);
            ok(hasState(b,0),"initialization remains idle, no download or model load");
            b.selfTest();await(b,4);
            ok(b.status().contains("ProcessBuilder.start"),"missing executable reported as a recoverable error");
            ok(Files.readString(logs.resolve("native.log")).contains("Cannot run program"),"subprocess launch exception persisted");
            b.stop();await(b,0);
            b.start();await(b,4);
            ok(b.status().contains("android/content/Context")||b.status().contains("android.content.Context"),"missing Android service class is handled, not a pending-JNI exception abort");
            b.stop();await(b,0);
            Path exe=libs.resolve("libllama_server_exec.so");
            Files.writeString(exe,"#!/bin/sh\necho self-test-only\nexit 0\n");exe.toFile().setExecutable(true);
            b.selfTest();awaitResult(b,0,0);
            ok(b.status().contains("\"exit\":0"),"successful subprocess self-test returns to idle");
            ok(Files.readString(logs.resolve("server.log")).contains("self-test-only"),"subprocess stdout captured");
            Files.writeString(exe,"#!/bin/sh\necho controlled-error >&2\nexit 7\n");
            b.selfTest();awaitResult(b,4,7);
            ok(b.status().contains("\"exit\":7"),"nonzero subprocess exit reported without killing the UI process");
            ok(Files.readString(logs.resolve("server.log")).contains("controlled-error"),"subprocess stderr captured");
            shutdown();
            // Sparse fake file is only a host lifecycle fixture, not model weights.
            Path model=ext.resolve("Ternary-Bonsai-2-27B-PTQ1_0.gguf");
            try(RandomAccessFile f=new RandomAccessFile(model.toFile(),"rw")){f.write(new byte[]{'G','G','U','F'});f.setLength(5946648928L);}
            Files.writeString(logs.resolve("model.complete"),"ok\n");
            Path marker=root.resolve("process-was-launched");
            Files.writeString(exe,"#!/bin/sh\ntouch '"+marker+"'\nexit 9\n");
            init(new Object(),ext.toString(),libs.toString(),logs.toString());
            Thread.sleep(900);
            ok(hasState(b,5)&&!Files.exists(marker),"completed model on disk is NOT automatically loaded on reopening");
            b.start();await(b,4);
            ok(Files.exists(marker)&&b.status().contains("\"exit\":9"),"model subprocess starts only after explicit command");
            shutdown();
            init(new Object(),ext.toString(),libs.toString(),logs.toString());
            ok(hasState(b,5),"reinitialization after shutdown works");
            shutdown();
            System.out.println("JNI lifecycle tests: "+passed+" passed.");
        } finally { shutdown(); }
    }
}
