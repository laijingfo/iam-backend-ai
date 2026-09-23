package com.lenovo.security.utils;


import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * @description Rsa 工具类，公钥私钥生成，加解密
 **/
public class RsaUtils {

    private static final String SRC = "a123456";


    public static void main(String[] args) throws Exception {
        String publicKey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDKJBZrDmrgHWlHEQ55V4E7lWYd2hE3hECIssBvPC4wm64XALp2Qyz0NyV7ELPONyTMZSyAUrfPBm3zY5GcCAJJG73baU0KLJRfDNnMJ5foAbYSM+tDWKnhGqIEx76srTj9TCXuCS+SV8kBsCKix3U1x45bcC0C4pIPdEv2pYaN/QIDAQAB";
        String privateKey = "MIICdQIBADANBgkqhkiG9w0BAQEFAASCAl8wggJbAgEAAoGBAMokFmsOauAdaUcRDnlXgTuVZh3aETeEQIiywG88LjCbrhcAunZDLPQ3JXsQs843JMxlLIBSt88GbfNjkZwIAkkbvdtpTQoslF8M2cwnl+gBthIz60NYqeEaogTHvqytOP1MJe4JL5JXyQGwIqLHdTXHjltwLQLikg90S/alho39AgMBAAECgYBT+ngh0+SM5UsmjiyANt1mvqOlBUKf2N7iq7iMvejGovbJcbEFjtTvRLXakc7RflFYZTP+v97DMH2fXx9QPneL4LgSmk3HRg6Q1XC+KxlfOLamngmhmQaDRekhuUsW7+25pTkY6fo/FhcyBD1sc+VwXfIbunWgPGI/yzgbR/pgoQJBAO4EmJukeSymaXs11jq4QUaxrZy2AW2VwGz9P5hRp7mziNZYkpyBb2R77DbecOzNA3hg6WY88xUjZy2bffF8xmUCQQDZaZwkR4vzwUBwvs7qhJhaeWTIGS0FGBe5I5WTpeuyNxb0FrHeqhVev9u9Gc1UaP2rvW/3Bq/U7kstof8XaAO5AkAQ2957V8EFepwKEBwmeJqXCUrEyNd9DZhdn+p7PX1l1+OfWxK3ZyuesHBgyoyxGSxfwG2HwlVtb96FCnh3PGFJAkBtn5+4d07tAHNSphNFEsiTTgRwFKmrmONVWcjw4sd+W+K5/rt/D3mpBcpxhhIPTsUhlGphP0Dnd6P2hWlPTwYZAkAwPtAm1s+gpBq6OT5VdO92gz+RV0C1NjJi6gegtiBD2TSItVMZ4t53IcDK0uPpArGFyYSsnotYY4XeZP3yEAel";

        String s = RsaUtils.decryptByPrivateKey(privateKey, "KgAZVyU4ioj8n9kZvhJAUesgzVN+f7H4NUWOpctfDAYfya3rpaJCs8g4RznPTRWNrSlTz5kyQmnpm6SF/2d8PuLWab78devSboT7QDxM0o4akEVapRhH8jWvVmwZEGIppfiMzCZdslkybjnK//9Kbi+HOHNQcAZ1m3Ttsr/NEK0=");
        System.out.println(s);
    }

    /**
     * 公钥加密私钥解密
     */
    private static void test1(RsaKeyPair keyPair) throws Exception {
        System.out.println("***************** 公钥加密私钥解密开始 *****************");
        String text1 = encryptByPublicKey(keyPair.getPublicKey(), RsaUtils.SRC);
        String text2 = decryptByPrivateKey(keyPair.getPrivateKey(), "AiMa+wxYTKCOtUQOQAp0E4Gcgyjt1nwiIMTy4FOJ3FJqG2FSCI3a518+epksr8Vb9k1EbX0CZrjXh0nlqcJosg==");
        System.out.println("加密前：" + RsaUtils.SRC);
        System.out.println("加密后：" + text1);
        System.out.println("解密后：" + text2);
        if (RsaUtils.SRC.equals(text2)) {
            System.out.println("解密字符串和原始字符串一致，解密成功");
        } else {
            System.out.println("解密字符串和原始字符串不一致，解密失败");
        }
        System.out.println("***************** 公钥加密私钥解密结束 *****************");
    }

    /**
     * 私钥加密公钥解密
     *
     * @throws Exception /
     */
    private static void test2(RsaKeyPair keyPair) throws Exception {
        System.out.println("***************** 私钥加密公钥解密开始 *****************");
        String text1 = encryptByPrivateKey(keyPair.getPrivateKey(), RsaUtils.SRC);
        String text2 = decryptByPublicKey(keyPair.getPublicKey(), text1);
        System.out.println("加密前：" + RsaUtils.SRC);
        System.out.println("加密后：" + text1);
        System.out.println("解密后：" + text2);
        if (RsaUtils.SRC.equals(text2)) {
            System.out.println("解密字符串和原始字符串一致，解密成功");
        } else {
            System.out.println("解密字符串和原始字符串不一致，解密失败");
        }
        System.out.println("***************** 私钥加密公钥解密结束 *****************");
    }

    /**
     * 公钥解密
     *
     * @param publicKeyText 公钥
     * @param text          待解密的信息
     * @return /
     * @throws Exception /
     */
    public static String decryptByPublicKey(String publicKeyText, String text) throws Exception {
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(Base64.decodeBase64(publicKeyText));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(x509EncodedKeySpec);
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, publicKey);
        byte[] result = cipher.doFinal(Base64.decodeBase64(text));
        return new String(result);
    }

    /**
     * 私钥加密
     *
     * @param privateKeyText 私钥
     * @param text           待加密的信息
     * @return /
     * @throws Exception /
     */
    public static String encryptByPrivateKey(String privateKeyText, String text) throws Exception {
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(Base64.decodeBase64(privateKeyText));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(pkcs8EncodedKeySpec);
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);
        byte[] result = cipher.doFinal(text.getBytes());
        return Base64.encodeBase64String(result);
    }

    /**
     * 私钥解密
     *
     * @param privateKeyText 私钥
     * @param text           待解密的文本
     * @return /
     * @throws Exception /
     */
    public static String decryptByPrivateKey(String privateKeyText, String text) throws Exception {
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec5 = new PKCS8EncodedKeySpec(Base64.decodeBase64(privateKeyText));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(pkcs8EncodedKeySpec5);
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] result = cipher.doFinal(Base64.decodeBase64(text));
        return new String(result);
    }

    /**
     * 公钥加密
     *
     * @param publicKeyText 公钥
     * @param text          待加密的文本
     * @return /
     */
    public static String encryptByPublicKey(String publicKeyText, String text) throws Exception {
        X509EncodedKeySpec x509EncodedKeySpec2 = new X509EncodedKeySpec(Base64.decodeBase64(publicKeyText));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(x509EncodedKeySpec2);
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] result = cipher.doFinal(text.getBytes());
        return Base64.encodeBase64String(result);
    }

    /**
     * 构建RSA密钥对
     *
     * @return /
     * @throws NoSuchAlgorithmException /
     */
    public static RsaKeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        RSAPublicKey rsaPublicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
        String publicKeyString = Base64.encodeBase64String(rsaPublicKey.getEncoded());
        String privateKeyString = Base64.encodeBase64String(rsaPrivateKey.getEncoded());
        return new RsaKeyPair(publicKeyString, privateKeyString);
    }


    /**
     * RSA密钥对对象
     */
    public static class RsaKeyPair {

        private final String publicKey;
        private final String privateKey;

        public RsaKeyPair(String publicKey, String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
        }

    }
}
