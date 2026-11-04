package com.shilapi.xcertplay.aion;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import javax.crypto.Cipher;

/** Standard ADB authentication and a single legacy shell stream; never bypasses approval. */
@android.annotation.TargetApi(27)
public final class AdbWire {
    static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f;
    static final int OKAY = 0x59414b4f, WRTE = 0x45545257, CLSE = 0x45534c43;
    static final int MAX_PACKET = 1024 * 1024, MAX_OUTPUT = 65536;
    public interface Notice { void message(String value); }
    static final class Packet {
        final int command, arg0, arg1; final byte[] data;
        Packet(int c, int a, int b, byte[] d) { command=c; arg0=a; arg1=b; data=d; }
    }
    private AdbWire() {}

    public static String localShell(KeyPair key, String command, Notice notice) throws Exception {
        // Deliberately not configurable: the app cannot connect to other machines.
        return shell(key, new InetSocketAddress("127.0.0.1", 5555), command, notice);
    }
    static String shell(KeyPair key, InetSocketAddress address, String command, Notice notice) throws Exception {
        if (!address.getAddress().isLoopbackAddress()) throw new IOException("只允许连接车机本机 ADB");
        if (command.indexOf('\0') >= 0 || command.length() > 2048) throw new IOException("命令格式无效");
        long end = System.nanoTime() + 60_000_000_000L;
        try (Socket socket = new Socket()) {
            socket.connect(address, 2500);
            socket.setSoTimeout(20000);
            InputStream in=socket.getInputStream(); OutputStream out=socket.getOutputStream();
            send(out, CNXN, 0x01000000, 4096, bytes("host::\0"));
            boolean signed=false, publicSent=false, connected=false;
            for (int attempts=0; attempts<12; attempts++) {
                if (System.nanoTime()>end) throw new IOException("ADB 授权等待超时");
                Packet p=read(in);
                if (p.command==CNXN) { connected=true; break; }
                if (p.command!=AUTH || p.arg0!=1 || p.data.length!=20)
                    throw new IOException("ADB 握手响应不符合预期");
                if (!signed) { send(out, AUTH, 2, 0, signToken(key,p.data)); signed=true; }
                else if (!publicSent) {
                    notice.message("请在车机弹窗中核对并允许“车机DiPlay”的 USB 调试请求；未允许时不会获得访问权。");
                    send(out, AUTH, 3, 0, publicKey(key)); publicSent=true;
                } else throw new IOException("车机尚未授权DiPlay，请允许调试请求后重试");
            }
            if (!connected) throw new IOException("未建立已授权的 ADB 连接");
            send(out, OPEN, 1, 0, bytes("shell:"+command+"\0"));
            int remote=0; ByteArrayOutputStream result=new ByteArrayOutputStream();
            for (int packets=0; packets<256; packets++) {
                if (System.nanoTime()>end) throw new IOException("命令执行超时，请检查授权状态");
                Packet p=read(in);
                if (p.arg1!=1 || p.arg0==0) throw new IOException("ADB 数据流标识不匹配");
                if (remote==0) remote=p.arg0;
                if (remote!=p.arg0) throw new IOException("ADB 远端数据流已变化");
                if (p.command==OKAY) continue;
                if (p.command==WRTE) {
                    if (result.size()+p.data.length>MAX_OUTPUT) throw new IOException("命令输出过大，已停止读取");
                    result.write(p.data); send(out,OKAY,1,remote,new byte[0]);
                } else if (p.command==CLSE) {
                    send(out,CLSE,1,remote,new byte[0]);
                    return new String(result.toByteArray(),StandardCharsets.UTF_8);
                } else throw new IOException("ADB 数据流响应异常");
            }
            throw new IOException("ADB 数据包超过限制");
        }
    }
    static byte[] signToken(KeyPair key,byte[] token) throws Exception {
        if (token.length!=20) throw new IOException("ADB token 长度错误");
        byte[] prefix={0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14};
        byte[] digestInfo=new byte[prefix.length+token.length];
        System.arraycopy(prefix,0,digestInfo,0,prefix.length);
        System.arraycopy(token,0,digestInfo,prefix.length,token.length);
        Cipher cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE,key.getPrivate());
        return cipher.doFinal(digestInfo);
    }
    static byte[] publicKey(KeyPair pair) throws Exception {
        RSAPublicKey key=(RSAPublicKey)pair.getPublic(); BigInteger n=key.getModulus();
        if (n.bitLength()!=2048 || !key.getPublicExponent().equals(BigInteger.valueOf(65537)))
            throw new IOException("ADB 密钥参数不受支持");
        BigInteger base=BigInteger.ONE.shiftLeft(32);
        int n0inv=n.mod(base).modInverse(base).negate().mod(base).intValue();
        ByteBuffer binary=ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        binary.putInt(64).putInt(n0inv).put(little(n,256));
        binary.put(little(BigInteger.ONE.shiftLeft(4096).mod(n),256)).putInt(65537);
        return bytes(Base64.getEncoder().encodeToString(binary.array())+" car-permission-helper@aion\0");
    }
    private static byte[] little(BigInteger value,int size) {
        byte[] big=value.toByteArray(), small=new byte[size];
        for (int i=0;i<size && i<big.length;i++) small[i]=big[big.length-1-i];
        return small;
    }
    static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    static void send(OutputStream out,int cmd,int arg0,int arg1,byte[] data) throws IOException {
        int sum=0; for (byte b:data) sum+=b&255;
        ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(cmd^0xffffffff);
        out.write(h.array());out.write(data);out.flush();
    }
    static Packet read(InputStream in) throws IOException {
        ByteBuffer h=ByteBuffer.wrap(exact(in,24)).order(ByteOrder.LITTLE_ENDIAN);
        int cmd=h.getInt(),a=h.getInt(),b=h.getInt(),size=h.getInt(),sum=h.getInt(),magic=h.getInt();
        if (magic!=(cmd^0xffffffff) || size<0 || size>MAX_PACKET) throw new IOException("ADB 数据包头无效");
        byte[] data=exact(in,size); int actual=0;for(byte x:data)actual+=x&255;
        if (sum!=actual) throw new IOException("ADB 数据包校验失败");
        return new Packet(cmd,a,b,data);
    }
    private static byte[] exact(InputStream in,int size) throws IOException {
        byte[] data=new byte[size]; int pos=0;
        while(pos<size) {int n=in.read(data,pos,size-pos);if(n<0)throw new EOFException("ADB 连接已关闭");if(n==0)continue;pos+=n;}
        return data;
    }
}
