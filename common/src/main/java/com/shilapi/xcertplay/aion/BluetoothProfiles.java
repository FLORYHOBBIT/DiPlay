package com.shilapi.xcertplay.aion;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import java.lang.reflect.InvocationTargetException;

@android.annotation.TargetApi(27)
final class BluetoothProfiles implements AutoCloseable {
    static final int MEDIA=11, PHONE=16;
    final BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
    private final Context context;
    private final Runnable changed;
    private BluetoothProfile media,phone;
    private boolean closed;
    String error="";
    private final BluetoothProfile.ServiceListener listener=new BluetoothProfile.ServiceListener(){
        @Override public void onServiceConnected(int id,BluetoothProfile proxy){
            if(closed){adapter.closeProfileProxy(id,proxy);return;}
            if(id==MEDIA)media=proxy;else if(id==PHONE)phone=proxy;
            changed.run();
        }
        @Override public void onServiceDisconnected(int id){if(id==MEDIA)media=null;else if(id==PHONE)phone=null;changed.run();}
    };
    BluetoothProfiles(Context value,Runnable callback){context=value.getApplicationContext();changed=callback;}
    void open(){
        if(closed || adapter==null || !adapter.isEnabled())return;
        try{
            if(media==null && !adapter.getProfileProxy(context,listener,MEDIA))error="车机未提供媒体接口";
            if(phone==null && !adapter.getProfileProxy(context,listener,PHONE))error="车机未提供电话接口";
        }catch(Exception e){error=message(e);}
    }
    BluetoothDevice bonded(String address){
        if(adapter==null || !BluetoothAdapter.checkBluetoothAddress(address))return null;
        for(BluetoothDevice d:adapter.getBondedDevices())if(address.equals(d.getAddress()))return d;
        return null;
    }
    int state(boolean forPhone,BluetoothDevice device){
        BluetoothProfile proxy=forPhone?phone:media;
        if(proxy==null || device==null)return -1;
        try{return proxy.getConnectionState(device);}catch(Exception e){error=message(e);return -1;}
    }
    boolean disconnectMedia(BluetoothDevice device)throws Exception{
        if(closed || media==null || device==null)throw new IllegalStateException("媒体接口尚未就绪");
        // Only A2DP sink is mutable here. Never call a telephone-profile mutator.
        try{return Boolean.TRUE.equals(media.getClass().getMethod("disconnect",BluetoothDevice.class).invoke(media,device));}
        catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof Exception)throw (Exception)cause;throw e;}
    }
    static String label(int state){switch(state){case 0:return "未连接";case 1:return "连接中";case 2:return "已连接";case 3:return "断开中";default:return "接口等待中";}}
    static String message(Exception e){String m=e.getMessage();return m==null?e.getClass().getSimpleName():m;}
    @Override public void close(){closed=true;if(adapter!=null){if(media!=null)adapter.closeProfileProxy(MEDIA,media);if(phone!=null)adapter.closeProfileProxy(PHONE,phone);}media=null;phone=null;}
}
