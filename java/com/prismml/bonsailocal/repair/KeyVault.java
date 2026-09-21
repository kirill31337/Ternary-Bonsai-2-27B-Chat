package com.prismml.bonsailocal.repair;
import android.security.keystore.KeyGenParameterSpec;import android.security.keystore.KeyProperties;
import java.security.*;import java.util.*;import javax.crypto.*;import javax.crypto.spec.GCMParameterSpec;
/** Optional API secret is encrypted using an app-specific Android Keystore AES-GCM key. */
public final class KeyVault {
 private static final String ALIAS="bonsai.web.brave.v1";
 private KeyVault(){}
 private static synchronized javax.crypto.SecretKey key()throws Exception {
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  Key key=store.getKey(ALIAS,null);if(key instanceof javax.crypto.SecretKey)return (javax.crypto.SecretKey)key;
  KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());return generator.generateKey();
 }
 static String seal(String secret,javax.crypto.SecretKey key)throws Exception {Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);c.updateAAD(ALIAS.getBytes("UTF-8"));byte[] body=c.doFinal(secret.getBytes("UTF-8")),iv=c.getIV(),packet=new byte[iv.length+body.length];if(iv.length!=12)throw new GeneralSecurityException("Unexpected GCM IV");System.arraycopy(iv,0,packet,0,iv.length);System.arraycopy(body,0,packet,iv.length,body.length);return Base64.getEncoder().encodeToString(packet);}
 static String open(String value,javax.crypto.SecretKey key)throws Exception {byte[] b=Base64.getDecoder().decode(value);if(b.length<28||b.length>1024)throw new GeneralSecurityException("Invalid encrypted secret");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOf(b,12)));c.updateAAD(ALIAS.getBytes("UTF-8"));return new String(c.doFinal(b,12,b.length-12),"UTF-8");}
 public static String encrypt(String value)throws Exception{return seal(value,key());}
 public static String decrypt(String value)throws Exception{return value.isEmpty()?"":open(value,key());}
}
