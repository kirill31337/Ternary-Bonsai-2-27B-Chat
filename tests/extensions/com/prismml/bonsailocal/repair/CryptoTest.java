package com.prismml.bonsailocal.repair;
import java.io.*;import java.net.*;import java.security.*;import javax.crypto.*;import javax.net.ssl.*;
public class CryptoTest {
 public static void main(String[] args)throws Exception {
  KeyGenerator gen=KeyGenerator.getInstance("AES");gen.init(256);SecretKey key=gen.generateKey();String secret="sensitive-Brave-key-fixture";String encrypted=KeyVault.seal(secret,key);if(encrypted.contains(secret)||!KeyVault.open(encrypted,key).equals(secret))throw new AssertionError("AES-GCM roundtrip failed");System.out.println("PASS actual AES-GCM secret roundtrip");
  boolean bad=false;try{KeyVault.open(encrypted,gen.generateKey());}catch(GeneralSecurityException expected){bad=true;}if(!bad)throw new AssertionError("Wrong key accepted");System.out.println("PASS incorrect key cannot decrypt API secret");
  byte[] damaged=java.util.Base64.getDecoder().decode(encrypted);damaged[damaged.length-1]^=1;bad=false;try{KeyVault.open(java.util.Base64.getEncoder().encodeToString(damaged),key);}catch(GeneralSecurityException expected){bad=true;}if(!bad)throw new AssertionError("Tampered ciphertext accepted");System.out.println("PASS encrypted-secret tampering detected");
  KeyStore ks=KeyStore.getInstance("PKCS12");try(FileInputStream in=new FileInputStream(args[0])){ks.load(in,"fixture-pass".toCharArray());}
  KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,"fixture-pass".toCharArray());TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(ks);
  SSLContext serverContext=SSLContext.getInstance("TLS"),clientContext=SSLContext.getInstance("TLS");serverContext.init(km.getKeyManagers(),null,null);clientContext.init(null,tm.getTrustManagers(),null);
  try(SSLServerSocket listener=(SSLServerSocket)serverContext.getServerSocketFactory().createServerSocket(0,3,InetAddress.getLoopbackAddress())){
   Thread server=new Thread(()->{for(int i=0;i<2;i++)try(SSLSocket s=(SSLSocket)listener.accept()){s.setSoTimeout(5000);s.startHandshake();s.getOutputStream().write(1);}catch(IOException ignored){}});server.start();
   try(Socket tcp=new Socket("127.0.0.1",listener.getLocalPort());SSLSocket tls=WebNet.secure(tcp,"localhost",clientContext.getSocketFactory())){tls.startHandshake();if(tls.getInputStream().read()!=1)throw new AssertionError();}System.out.println("PASS real TLS socket validates matching certificate hostname");
   bad=false;try(Socket tcp=new Socket("127.0.0.1",listener.getLocalPort());SSLSocket tls=WebNet.secure(tcp,"not-localhost.example",clientContext.getSocketFactory())){tls.startHandshake();}catch(SSLException expected){bad=true;}if(!bad)throw new AssertionError("Hostname mismatch accepted");System.out.println("PASS real TLS socket rejects hostname mismatch (not a trust-all client)");server.join(6000);
  }
 }
}
