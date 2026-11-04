package com.shilapi.xcertplay.aion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Cipher;

public class TestMain {
    @org.junit.Test public void androidCryptoProviderAlsoRestoresTheNonCrtFixture()throws Exception{
        java.security.Provider original=java.security.Security.getProvider("Conscrypt");
        int position=-1;
        java.security.Provider[] providers=java.security.Security.getProviders();
        for(int i=0;i<providers.length;i++)if(providers[i]==original)position=i+1;
        java.security.Provider provider=original!=null?original:(java.security.Provider)Class.forName("org.conscrypt.OpenSSLProvider").getConstructor().newInstance();
        java.security.Security.removeProvider(provider.getName());
        java.security.Security.insertProviderAt(provider,1);
        try{verify();}finally{
            java.security.Security.removeProvider(provider.getName());
            if(original!=null)java.security.Security.insertProviderAt(original,position);
        }
    }
    static int checks;
    interface Throwing {void run()throws Exception;}
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    static void rejects(Throwing action,String name)throws Exception{try{action.run();}catch(Exception expected){checks++;return;}throw new AssertionError(name);}
    @org.junit.Test public void verify()throws Exception{
        for(int phone=-1;phone<=3;phone++)for(int media=-1;media<=3;media++){
            check(BluetoothRule.shouldDisconnect(true,true,phone,media)==(phone==2&&(media==1||media==2)),"only media with connected telephone");
            check(!BluetoothRule.shouldDisconnect(false,true,phone,media),"disabled automation never mutates");
            check(!BluetoothRule.shouldDisconnect(true,false,phone,media),"unpaired target never mutates");
        }
        check(PermissionPolicy.query("com.shihab.diplay",PermissionPolicy.WRITE).equals("appops get com.shihab.diplay WRITE_SETTINGS"),"package target");
        for(String invalid:new String[]{"com.app;reboot","com.app\nwhoami","com.app --uid","../system","com..app","com.app$(id)","com.app'",""})
            rejects(()->PermissionPolicy.query(invalid,PermissionPolicy.WRITE),"reject package injection");
        rejects(()->PermissionPolicy.change("com.app","WRITE_SECURE_SETTINGS","allow"),"reject broader permissions");
        rejects(()->PermissionPolicy.change("com.app",PermissionPolicy.WRITE,"allow;reboot"),"reject mode injection");
        check(PermissionPolicy.parseMode("No operations.\n",PermissionPolicy.WRITE).equals("default"),"no-op default");
        check(PermissionPolicy.parseMode("WRITE_SETTINGS: allow; time=+1s",PermissionPolicy.WRITE).equals("allow"),"allow output");
        check(PermissionPolicy.parseMode("SYSTEM_ALERT_WINDOW: ignore",PermissionPolicy.OVERLAY).equals("ignore"),"ignore output");
        rejects(()->PermissionPolicy.parseMode("Permission denied",PermissionPolicy.WRITE),"do not guess on failure");
        rejects(()->PermissionPolicy.parseMode("WRITE_SETTINGS: allow\nWRITE_SETTINGS: ignore",PermissionPolicy.WRITE),"ambiguous result");
        rejects(()->PermissionPolicy.parseMode("Uid mode: ignore\nWRITE_SETTINGS: allow",PermissionPolicy.WRITE),"UID-level conflict");
        check(PermissionPolicy.checkedOutput("WRITE_SETTINGS: allow\n__CAR_PERMISSION_EXIT_0\n").equals("WRITE_SETTINGS: allow"),"exit marker");
        rejects(()->PermissionPolicy.checkedOutput("Success without exit status"),"uncertain execution");
        rejects(()->PermissionPolicy.checkedOutput("refused\n__CAR_PERMISSION_EXIT_91\n"),"reject nonzero exit");
        check(PermissionPolicy.guarded("id").contains("!= \"2000\""),"UID guard");

        ByteArrayOutputStream encoded=new ByteArrayOutputStream();AdbWire.send(encoded,AdbWire.WRTE,7,1,new byte[]{1,2,3});
        byte[] packet=encoded.toByteArray();check(AdbWire.read(new ByteArrayInputStream(packet)).data.length==3,"valid packet");
        byte[] badMagic=packet.clone();badMagic[20]^=1;rejects(()->AdbWire.read(new ByteArrayInputStream(badMagic)),"bad magic");
        byte[] badChecksum=packet.clone();badChecksum[26]^=1;rejects(()->AdbWire.read(new ByteArrayInputStream(badChecksum)),"bad checksum");
        byte[] badLength=packet.clone();ByteBuffer.wrap(badLength).order(ByteOrder.LITTLE_ENDIAN).putInt(12,AdbWire.MAX_PACKET+1);rejects(()->AdbWire.read(new ByteArrayInputStream(badLength)),"oversize header");
        rejects(()->AdbWire.read(new ByteArrayInputStream(new byte[6])),"truncated header");
        KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);KeyPair key=g.generateKeyPair();
        check(Arrays.equals(AdbKeys.restore(key.getPrivate().getEncoded()).getPublic().getEncoded(),key.getPublic().getEncoded()),"restore persisted CRT key");
        // Robolectric installs Conscrypt globally; construct this synthetic non-CRT fixture with the host JDK provider.
        java.security.KeyFactory factory=java.security.KeyFactory.getInstance("RSA","SunRsaSign");
        java.security.spec.RSAPrivateKeySpec simple=factory.getKeySpec(key.getPrivate(),java.security.spec.RSAPrivateKeySpec.class);
        check(Arrays.equals(AdbKeys.restore(factory.generatePrivate(simple).getEncoded()).getPublic().getEncoded(),key.getPublic().getEncoded()),"restore persisted non-CRT key");
        rejects(()->AdbWire.signToken(key,new byte[19]),"bad auth challenge");
        byte[] token=new byte[20];Arrays.fill(token,(byte)7);byte[] signature=AdbWire.signToken(key,token);
        Cipher cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");cipher.init(Cipher.DECRYPT_MODE,key.getPublic());
        byte[] recovered=cipher.doFinal(signature);check(Arrays.equals(Arrays.copyOfRange(recovered,recovered.length-20,recovered.length),token),"RSA AUTH challenge");
        String publicText=new String(AdbWire.publicKey(key),"UTF-8");byte[] raw=Base64.getDecoder().decode(publicText.substring(0,publicText.indexOf(' ')));
        ByteBuffer rawBuffer=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);check(raw.length==524&&rawBuffer.getInt(0)==64&&rawBuffer.getInt(520)==65537,"Android public key layout");
        BigInteger modulus=((RSAPublicKey)key.getPublic()).getModulus(), base=BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv=BigInteger.valueOf(rawBuffer.getInt(4)&0xffffffffL);check(modulus.multiply(n0inv).add(BigInteger.ONE).mod(base).equals(BigInteger.ZERO),"Montgomery n0inv");
        byte[] rr=Arrays.copyOfRange(raw,264,520);for(int i=0,j=rr.length-1;i<j;i++,j--){byte x=rr[i];rr[i]=rr[j];rr[j]=x;}
        check(new BigInteger(1,rr).equals(BigInteger.ONE.shiftLeft(4096).mod(modulus)),"Montgomery R squared");
        rejects(()->AdbWire.shell(key,new InetSocketAddress("192.0.2.1",5555),"id",s->{}),"remote endpoints forbidden");
        mockSession(key,token);
        System.out.println("PASS: "+checks+" protocol, authentication and authorization-policy checks");
    }
    static void mockSession(KeyPair key,byte[] token)throws Exception{
        AtomicReference<Throwable> failure=new AtomicReference<>();
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            server.setSoTimeout(6000);
            Thread peer=new Thread(()->{try(Socket s=server.accept()){
                s.setSoTimeout(6000);AdbWire.Packet hello=AdbWire.read(s.getInputStream());check(hello.command==AdbWire.CNXN,"client CNXN");
                AdbWire.send(s.getOutputStream(),AdbWire.AUTH,1,0,token);
                check(AdbWire.read(s.getInputStream()).arg0==2,"client AUTH signature");
                AdbWire.send(s.getOutputStream(),AdbWire.AUTH,1,0,token);
                AdbWire.Packet pub=AdbWire.read(s.getInputStream());check(pub.arg0==3&&pub.data[pub.data.length-1]==0,"public key approval request");
                AdbWire.send(s.getOutputStream(),AdbWire.CNXN,0x01000000,4096,AdbWire.bytes("device::\0"));
                AdbWire.Packet open=AdbWire.read(s.getInputStream());check(open.command==AdbWire.OPEN&&new String(open.data,"UTF-8").contains("appops get com.shihab.diplay WRITE_SETTINGS"),"shell stream target");
                AdbWire.send(s.getOutputStream(),AdbWire.OKAY,42,1,new byte[0]);
                for(String chunk:new String[]{"WRITE_SETTINGS: ","allow\n__CAR_PERMISSION_EXIT_0\n"}){
                    AdbWire.send(s.getOutputStream(),AdbWire.WRTE,42,1,AdbWire.bytes(chunk));
                    AdbWire.Packet ack=AdbWire.read(s.getInputStream());check(ack.command==AdbWire.OKAY&&ack.arg0==1&&ack.arg1==42,"stream ACK");
                }
                AdbWire.send(s.getOutputStream(),AdbWire.CLSE,42,1,new byte[0]);
                check(AdbWire.read(s.getInputStream()).command==AdbWire.CLSE,"close ACK");
            }catch(Throwable t){failure.set(t);}});
            peer.start();String value=AdbWire.shell(key,new InetSocketAddress(InetAddress.getLoopbackAddress(),server.getLocalPort()),PermissionPolicy.guarded(PermissionPolicy.query("com.shihab.diplay",PermissionPolicy.WRITE)),s->{});
            peer.join(6500);if(peer.isAlive())throw new AssertionError("mock server did not finish");if(failure.get()!=null)throw new AssertionError(failure.get());
            check(PermissionPolicy.checkedOutput(value).equals("WRITE_SETTINGS: allow"),"end-to-end output");
        }
    }
}
