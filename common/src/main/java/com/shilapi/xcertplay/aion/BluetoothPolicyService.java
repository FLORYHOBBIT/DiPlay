package com.shilapi.xcertplay.aion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;

@android.annotation.TargetApi(27)
public final class BluetoothPolicyService extends Service {
    static final String PREF="bluetooth-phone-only",ENABLED="enabled",ADDRESS="address",NAME="name",STATUS="status";
    static final String AUTO_DIPLAY="auto-diplay",LAUNCH_STATUS="diplay-launch-status";
    private static final String CHANNEL="bluetooth-phone-only";
    private final Handler handler=new Handler();
    private BluetoothProfiles profiles;
    private SharedPreferences prefs;
    private long lastAttempt;
    private int attempts;
    private boolean registered,evaluating;
    private String lastStatus="";
    static SharedPreferences preferences(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    static void startIfEnabled(Context c){if(com.shilapi.xcertplay.AionIntegration.enabled(c) && com.shilapi.xcertplay.AionIntegration.supported() && preferences(c).getBoolean(ENABLED,false))c.startForegroundService(new Intent(c,BluetoothPolicyService.class));}
    private final Runnable periodic=new Runnable(){public void run(){evaluate();if(prefs.getBoolean(ENABLED,false))handler.postDelayed(this,15000);}};
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(i.getAction()) && i.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1)==BluetoothAdapter.STATE_ON)profiles.open();
        handler.postDelayed(()->evaluate(),1000);
    }};
    @Override public void onCreate(){
        super.onCreate();prefs=preferences(this);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"蓝牙媒体优化",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,BluetoothMediaActivity.class);
        PendingIntent content=PendingIntent.getActivity(this,31,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,32,new Intent(this,BluetoothPolicyService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(31,new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_silent_mode).setContentTitle("蓝牙媒体优化已开启").setContentText("保留所选手机电话连接；点击查看或停用").setContentIntent(content).setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"停用",stop).build());
        profiles=new BluetoothProfiles(this,()->evaluate());
        IntentFilter f=new IntentFilter();f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED");f.addAction("android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED");
        registerReceiver(receiver,f);registered=true;if(com.shilapi.xcertplay.AionIntegration.supported())profiles.open();
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i!=null && "stop".equals(i.getAction())){prefs.edit().putBoolean(ENABLED,false).putString(STATUS,"已停用自动处理，可在原车设置中重新勾选媒体").commit();stopSelf();return START_NOT_STICKY;}
        if(!prefs.getBoolean(ENABLED,false)){stopSelf();return START_NOT_STICKY;}
        handler.removeCallbacks(periodic);handler.post(periodic);return START_STICKY;
    }
    private void status(String value){if(!value.equals(lastStatus)){lastStatus=value;prefs.edit().putString(STATUS,value).apply();android.util.Log.i("CarBtOnly",value);}}
    private void fail(String value){prefs.edit().putBoolean(ENABLED,false).putString(STATUS,"已停止自动处理："+value).commit();android.util.Log.e("CarBtOnly",value);stopSelf();}
    private void evaluate(){
        if(evaluating || profiles==null || !com.shilapi.xcertplay.AionIntegration.supported() || !prefs.getBoolean(ENABLED,false))return;evaluating=true;
        try{
            if(profiles.adapter==null){fail("没有蓝牙适配器");return;}
            if(!profiles.adapter.isEnabled()){status("等待车机蓝牙开启");return;}
            BluetoothDevice target=profiles.bonded(prefs.getString(ADDRESS,""));
            if(target==null){fail("所选手机已不在配对列表，请重新选择");return;}
            int phone=profiles.state(true,target),media=profiles.state(false,target);
            if(!profiles.error.isEmpty()){fail(profiles.error);return;}
            if(!BluetoothRule.shouldDisconnect(true,true,phone,media)){
                if(media==0)attempts=0;
                status("电话："+BluetoothProfiles.label(phone)+"；媒体："+BluetoothProfiles.label(media)+(phone==2 && media==0?"。仅保留电话，处理完成":"。等待所选手机电话连接"));return;
            }
            long now=SystemClock.elapsedRealtime();if(lastAttempt!=0 && now-lastAttempt<15000)return;
            if(attempts>=3){fail("媒体多次未能断开，请查看原车设置");return;}
            lastAttempt=now;attempts++;
            boolean accepted=profiles.disconnectMedia(target);
            status(accepted?"已请求关闭所选手机媒体，正在确认电话与媒体状态":"系统未接受媒体关闭请求，将再次检查");
            handler.postDelayed(()->evaluate(),1800);
        }catch(Exception e){fail(BluetoothProfiles.message(e));}finally{evaluating=false;}
    }
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(registered)unregisterReceiver(receiver);if(profiles!=null)profiles.close();stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
