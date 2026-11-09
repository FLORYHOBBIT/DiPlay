package com.shilapi.xcertplay.aion;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@android.annotation.TargetApi(27)
public final class BluetoothMediaActivity extends AionPanelActivity {
    private final Handler handler=new Handler();
    private final List<BluetoothDevice> devices=new ArrayList<>();
    private final List<String> deviceNames=new ArrayList<>();
    private final List<Button> deviceRows=new ArrayList<>();
    private SharedPreferences prefs;
    private BluetoothProfiles profiles;
    private int selectedIndex;
    private TextView live,history,launchStatus;
    private Switch autoDiPlay,autoMedia;
    private boolean rendering;
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,2000);}};
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(ProjectionUi.MUTED);t.setLineSpacing(dp(AionUi.BLOCK_GAP_DP/3),1f);t.setPadding(0,dp(AionUi.BLOCK_GAP_DP),0,dp(AionUi.BLOCK_GAP_DP));return t;}
    private Button button(String title,View.OnClickListener action){return ProjectionUi.makeButton(this,title,action);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        boolean supported=com.shilapi.xcertplay.AionIntegration.supported();
        LinearLayout page=ProjectionUi.page(this,"蓝牙与自启动",supported?"选择已配对的 iPhone，设置蓝牙连接后启动 CarPlay，以及蓝牙连接后自动关闭同步音频。":"本页仅适用于已记录的埃安 Android 8.1 固件。");
        if(!supported){ProjectionUi.button(page,"返回 DiPlay",()->finish());return;}
        prefs=BluetoothPolicyService.preferences(this);
        LinearLayout root=ProjectionUi.card(page,"连接的 iPhone");
        profiles=new BluetoothProfiles(this,()->render());
        if(profiles.adapter!=null)devices.addAll(profiles.adapter.getBondedDevices());
        Collections.sort(devices,(a,c)->a.getAddress().compareTo(c.getAddress()));
        String saved=prefs.getString(BluetoothPolicyService.ADDRESS,"");
        int selected=0;
        for(int n=0;n<devices.size();n++){BluetoothDevice d=devices.get(n);deviceNames.add((d.getName()==null?"已配对设备":d.getName())+" · "+d.getAddress());if(d.getAddress().equals(saved))selected=n;}
        if(deviceNames.isEmpty())deviceNames.add("没有已配对设备，请先在原车设置配对手机");
        selectedIndex=selected;
        for(int n=0;n<deviceNames.size();n++){
            final int index=n;
            Button row=button(deviceNames.get(n),v->{if(!devices.isEmpty()){selectedIndex=index;render();}});
            deviceRows.add(row);ProjectionUi.add(root,row);
        }
        root=ProjectionUi.card(page,"连接状态");
        live=label("正在读取电话与媒体接口…",23);ProjectionUi.add(root,live);
        history=label("",19);ProjectionUi.add(root,history);
        root=ProjectionUi.card(page,"随车启动");
        autoDiPlay=ProjectionUi.toggle(root,"蓝牙连接后启动 CarPlay");
        launchStatus=label("",18);ProjectionUi.add(root,launchStatus);
        ProjectionUi.add(root,label("开启后，收到所选手机的蓝牙连接广播即直接启动 CarPlay 连接界面，并沿用已保存的热点设置。与自动关闭媒体相互独立。",18));
        autoDiPlay.setOnCheckedChangeListener((view,checked)->{
            if(rendering)return;
            if(!checked){prefs.edit().putBoolean(BluetoothPolicyService.AUTO_DIPLAY,false).putString(BluetoothPolicyService.LAUNCH_STATUS,"已停用自动打开 DiPlay").commit();render();return;}
            BluetoothDevice device=selected();
            if(device==null){render();return;}
            com.shilapi.xcertplay.AionIntegration.savePhone(this,device.getAddress(),device.getName()==null?"iPhone":device.getName());
            prefs.edit().putString(BluetoothPolicyService.ADDRESS,device.getAddress()).putString(BluetoothPolicyService.NAME,device.getName()).apply();
            prefs.edit().putBoolean(BluetoothPolicyService.AUTO_DIPLAY,true).putString(BluetoothPolicyService.LAUNCH_STATUS,"已开启，等待所选手机的蓝牙连接广播").commit();
            BluetoothPolicyService.startIfEnabled(this);render();
        });
        root=ProjectionUi.card(page,"自动管理媒体连接");
        autoMedia=ProjectionUi.toggle(root,"蓝牙连接后自动关闭同步音频");
        ProjectionUi.add(root,label("启用后会有后台常驻通知，并保存你选择的手机。原车蓝牙电话连接后自动关闭媒体连接；原车蓝牙音乐将无法通过这部手机播放，DiPlay 可继续走车机热点。停用后不会擅自重新连接媒体。原车若限制开机自启动，需要在下次启动后确认此页状态。",18));
        autoMedia.setOnCheckedChangeListener((view,checked)->{
            if(rendering)return;
            if(!checked){prefs.edit().putBoolean(BluetoothPolicyService.ENABLED,false).putString(BluetoothPolicyService.STATUS,"已停用；需要媒体时可在原车设置重新勾选").commit();stopService(new Intent(this,BluetoothPolicyService.class));render();return;}
            BluetoothDevice d=selected();
            if(d==null){render();return;}
            if(profiles.state(true,d)<0 || profiles.state(false,d)<0){prefs.edit().putString(BluetoothPolicyService.STATUS,"接口尚未就绪，等电话和媒体状态均能读取后再启用，不会猜测操作。").apply();render();return;}
            com.shilapi.xcertplay.AionIntegration.savePhone(this,d.getAddress(),d.getName()==null?"iPhone":d.getName());
            prefs.edit().putString(BluetoothPolicyService.ADDRESS,d.getAddress()).putString(BluetoothPolicyService.NAME,d.getName()).putBoolean(BluetoothPolicyService.ENABLED,true).putString(BluetoothPolicyService.STATUS,"已启用，正在检查连接").commit();
            BluetoothPolicyService.startIfEnabled(this);render();
        });
        ProjectionUi.button(page,"返回 DiPlay",()->finish());
        profiles.open();
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacksAndMessages(null);super.onPause();}
    @Override protected void onDestroy(){if(profiles!=null)profiles.close();super.onDestroy();}
    private BluetoothDevice selected(){int n=selectedIndex;return n>=0 && n<devices.size()?devices.get(n):null;}
    private void render(){
        if(live==null || prefs==null || isDestroyed())return;
        boolean active=prefs.getBoolean(BluetoothPolicyService.ENABLED,false);
        boolean phoneAuto=prefs.getBoolean(BluetoothPolicyService.AUTO_DIPLAY,false);
        BluetoothDevice d=selected();
        String status=profiles.adapter==null?"车机没有蓝牙适配器":!profiles.adapter.isEnabled()?"车机蓝牙未开启":d==null?"尚未选择配对手机":"电话："+BluetoothProfiles.label(profiles.state(true,d))+"；媒体："+BluetoothProfiles.label(profiles.state(false,d));
        if(!profiles.error.isEmpty())status+="\n接口检查："+profiles.error;
        live.setText((active?"自动处理已启用\n":"自动处理未启用\n")+status);
        history.setText("最近状态："+prefs.getString(BluetoothPolicyService.STATUS,"尚未执行自动处理"));
        rendering=true;
        try{
            autoDiPlay.setChecked(phoneAuto);autoDiPlay.setEnabled(d!=null);
            autoMedia.setChecked(active);autoMedia.setEnabled(active || (d!=null && profiles.error.isEmpty()));
        } finally { rendering=false; }
        launchStatus.setText("DiPlay："+prefs.getString(BluetoothPolicyService.LAUNCH_STATUS,"自动打开未启用"));
        for(int n=0;n<deviceRows.size();n++){
            Button row=deviceRows.get(n);
            row.setText((!devices.isEmpty() && n==selectedIndex?"✓ ":"")+deviceNames.get(n));
            row.setEnabled(!active && !phoneAuto && !devices.isEmpty());
        }
    }
}
