import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
/** APK signing helper. Production keys/passwords must be supplied outside Git. */
@SuppressWarnings("deprecation")
public final class SignApk {
  static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b&255));return s.toString();}
  static String cert(ApkVerifier.Result result)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(result.getSignerCertificates().get(0).getEncoded()));}
  public static void main(String[] args)throws Exception {
    if(args.length<7)throw new IllegalArgumentException("usage: SignApk INPUT OUTPUT KEYSTORE ALIAS STOREPASS KEYPASS PREVIOUS_APK_OR_DASH");
    char[] storePass=args[4].toCharArray(), keyPass=args[5].toCharArray();
    KeyStore store=KeyStore.getInstance(KeyStore.getDefaultType());
    try(InputStream in=new FileInputStream(args[2])){store.load(in,storePass);}
    String alias=args[3];
    PrivateKey key=(PrivateKey)store.getKey(alias,keyPass);
    if(key==null)throw new KeyStoreException("No private key for alias "+alias);
    List<X509Certificate> chain=new ArrayList<>();
    for(java.security.cert.Certificate c:store.getCertificateChain(alias))chain.add((X509Certificate)c);
    ApkSigner.SignerConfig signer=new ApkSigner.SignerConfig.Builder(alias,key,chain).build();
    new ApkSigner.Builder(Collections.singletonList(signer)).setInputApk(new File(args[0])).setOutputApk(new File(args[1]))
      .setMinSdkVersion(28).setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).setV4SigningEnabled(false)
      .setOtherSignersSignaturesPreserved(false).setLibraryPageAlignmentBytes(16384).build().sign();
    ApkVerifier.Result result=new ApkVerifier.Builder(new File(args[1])).setMinCheckedPlatformVersion(28).build().verify();
    System.out.println("APK verified: "+result.isVerified()+"; v1="+result.isVerifiedUsingV1Scheme()+"; v2="+result.isVerifiedUsingV2Scheme()+"; v3="+result.isVerifiedUsingV3Scheme());
    System.out.println("Certificate SHA-256: "+cert(result));
    for(Object w:result.getWarnings())System.out.println("Warning: "+w);
    for(Object e:result.getAllErrors())System.out.println("Error: "+e);
    if(!result.isVerified())throw new SecurityException("APK signature verification failed");
    if(!"-".equals(args[6])){
      ApkVerifier.Result old=new ApkVerifier.Builder(new File(args[6])).setMinCheckedPlatformVersion(28).build().verify();
      if(!old.isVerified()||!cert(old).equals(cert(result)))throw new SecurityException("Update signing certificate mismatch");
      System.out.println("Previous APK certificate matches: true");
    }
  }
}
