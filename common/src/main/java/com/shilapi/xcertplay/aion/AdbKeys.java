package com.shilapi.xcertplay.aion;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;

@android.annotation.TargetApi(27)
final class AdbKeys {
    private AdbKeys() {}
    static KeyPair restore(byte[] encoded) throws Exception {
        KeyFactory factory=KeyFactory.getInstance("RSA");
        PrivateKey privateKey=factory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
        // Android providers may return a non-CRT private key. Never cast to RSAPrivateCrtKey.
        RSAPrivateKeySpec spec=factory.getKeySpec(privateKey,RSAPrivateKeySpec.class);
        KeyPair pair=new KeyPair(factory.generatePublic(new RSAPublicKeySpec(spec.getModulus(),RSAKeyGenParameterSpec.F4)),privateKey);
        // This app creates only F4 keys. Validate a restored key before using its reconstructed public key.
        byte[] token=new byte[20];Arrays.fill(token,(byte)0x5a);
        Cipher verifier=Cipher.getInstance("RSA/ECB/PKCS1Padding");
        verifier.init(Cipher.DECRYPT_MODE,pair.getPublic());
        byte[] recovered=verifier.doFinal(AdbWire.signToken(pair,token));
        if(recovered.length!=35 || !Arrays.equals(Arrays.copyOfRange(recovered,15,35),token))
            throw new IllegalStateException("本机调试密钥校验失败，未建立连接");
        return pair;
    }
}
