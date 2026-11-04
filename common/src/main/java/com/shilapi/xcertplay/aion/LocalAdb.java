package com.shilapi.xcertplay.aion;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.RSAKeyGenParameterSpec;

@android.annotation.TargetApi(27)
final class LocalAdb {
    private final Context context;
    private KeyPair pair;
    LocalAdb(Context value) { context=value.getApplicationContext(); }
    private synchronized KeyPair key() throws Exception {
        if(pair!=null)return pair;
        File file=new File(context.getFilesDir(),"local-adb-key.pk8");
        if(file.isFile()) {
            pair=AdbKeys.restore(Files.readAllBytes(file.toPath()));
        } else {
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(new RSAKeyGenParameterSpec(2048,RSAKeyGenParameterSpec.F4));
            KeyPair generated=generator.generateKeyPair();
            File temp=new File(context.getFilesDir(),"local-adb-key.tmp");
            try(FileOutputStream out=new FileOutputStream(temp)) {out.write(generated.getPrivate().getEncoded());out.getFD().sync();}
            if(!temp.renameTo(file))throw new IllegalStateException("无法保存本机调试密钥，未建立连接");
            pair=generated;
        }
        return pair;
    }
    String execute(String command,AdbWire.Notice notice) throws Exception {
        return PermissionPolicy.checkedOutput(AdbWire.localShell(key(),PermissionPolicy.guarded(command),notice));
    }
    String connect(AdbWire.Notice notice) throws Exception {
        String identity=execute("id",notice);
        if(!identity.contains("uid=2000(shell)"))throw new IllegalStateException("ADB 身份不是普通 shell，助手不执行提权操作");
        return identity;
    }
    String get(String pkg,String op,AdbWire.Notice notice)throws Exception {
        return PermissionPolicy.parseMode(execute(PermissionPolicy.query(pkg,op),notice),op);
    }
    void set(String pkg,String op,String mode,AdbWire.Notice notice)throws Exception {
        execute(PermissionPolicy.change(pkg,op,mode),notice);
        String actual=get(pkg,op,notice);
        if(!mode.equals(actual))throw new IllegalStateException("系统未确认目标模式（实际："+actual+"），请重新检查");
    }
}
