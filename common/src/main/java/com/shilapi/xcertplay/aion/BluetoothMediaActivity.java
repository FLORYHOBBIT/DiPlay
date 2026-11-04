package com.shilapi.xcertplay.aion;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@android.annotation.TargetApi(27)
public final class BluetoothMediaActivity extends AionPanelActivity {
    private final Handler handler=new Handler();
    private final List<BluetoothDevice> devices=new ArrayList<>();
    private SharedPreferences prefs;
    private BluetoothProfiles profiles;
    private Button picker;
    private int selectedIndex;
    private final List<String> deviceNames=new ArrayList<>();
    private TextView live,history,launchStatus;
    private CheckBox autoDiPlay;
    private boolean renderingAuto;
    private Button enable,disable;
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,2000);}};
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xff173546);t.setPadding(0,dp(AionUi.BLOCK_GAP_DP),0,dp(AionUi.BLOCK_GAP_DP));return t;}
    private Button button(String title,View.OnClickListener action){Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(20);b.setMinHeight(dp(AionUi.CONTROL_HEIGHT_DP));b.setTextColor(0xff087f91);b.setOnClickListener(action);return b;}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(!com.shilapi.xcertplay.AionIntegration.supported()){new AlertDialog.Builder(this).setMessage("本页仅适用于已记录的埃安 Android 8.1 固件").setPositiveButton("返回",(d,w)->finish()).setOnCancelListener(d->finish()).show();return;}
        prefs=BluetoothPolicyService.preferences(this);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(AionUi.PAGE_PADDING_DP),dp(AionUi.PAGE_PADDING_DP),dp(AionUi.PAGE_PADDING_DP),dp(AionUi.PAGE_PADDING_DP));root.setBackgroundColor(0xffeaf4f8);scroll.addView(root);setContentView(scroll);
        TextView title=label("埃安蓝牙与自启动",29);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        root.addView(label("开机或手机重连后，自动关闭选定手机的蓝牙媒体连接。仅在该手机电话已连接时处理媒体；电话和通讯录设置保持由原车管理。无需电脑或网络 ADB。",20));
        profiles=new BluetoothProfiles(this,()->render());
        if(profiles.adapter!=null)devices.addAll(profiles.adapter.getBondedDevices());
        Collections.sort(devices,(a,c)->a.getAddress().compareTo(c.getAddress()));
        List<String> names=deviceNames;int selected=0;String saved=prefs.getString(BluetoothPolicyService.ADDRESS,"");
        for(int n=0;n<devices.size();n++){BluetoothDevice d=devices.get(n);names.add((d.getName()==null?"已配对设备":d.getName())+" · "+d.getAddress());if(d.getAddress().equals(saved))selected=n;}
        if(names.isEmpty())names.add("没有已配对设备，请先在原车设置配对手机");
        selectedIndex=selected;
        picker=button("iPhone · "+names.get(selectedIndex),v->new AlertDialog.Builder(this).setTitle("选择 iPhone")
            .setSingleChoiceItems(names.toArray(new String[0]),selectedIndex,(dialog,index)->{selectedIndex=index;render();dialog.dismiss();})
            .setNegativeButton("取消",null).show());
        root.addView(picker,new LinearLayout.LayoutParams(-1,-2));
        live=label("正在读取电话与媒体接口…",23);root.addView(live);
        history=label("",19);root.addView(history);
        autoDiPlay=new CheckBox(this);autoDiPlay.setText("蓝牙连接后直接启动 CarPlay");autoDiPlay.setTextSize(21);autoDiPlay.setTextColor(0xff173546);root.addView(autoDiPlay);
        launchStatus=label("",18);root.addView(launchStatus);
        autoDiPlay.setOnCheckedChangeListener((view,checked)->{
            if(renderingAuto)return;
            if(!checked){prefs.edit().putBoolean(BluetoothPolicyService.AUTO_DIPLAY,false).putString(BluetoothPolicyService.LAUNCH_STATUS,"已停用自动打开 DiPlay").commit();render();return;}
            renderingAuto=true;autoDiPlay.setChecked(false);renderingAuto=false;
            new AlertDialog.Builder(this).setTitle("自动打开 DiPlay？").setMessage("收到所选手机的蓝牙连接广播后，直接启动 CarPlay 连接界面，并沿用已保存的热点设置。自动关闭媒体可以独立开启或停用。").setNegativeButton("取消",(d,w)->render()).setPositiveButton("启用",(d,w)->{
                BluetoothDevice device=selected();if(device==null)return;
                com.shilapi.xcertplay.AionIntegration.savePhone(this,device.getAddress(),device.getName()==null?"iPhone":device.getName());
                prefs.edit().putString(BluetoothPolicyService.ADDRESS,device.getAddress()).putString(BluetoothPolicyService.NAME,device.getName()).apply();
                prefs.edit().putBoolean(BluetoothPolicyService.AUTO_DIPLAY,true).putString(BluetoothPolicyService.LAUNCH_STATUS,"已开启，等待所选手机的蓝牙连接广播").commit();BluetoothPolicyService.startIfEnabled(this);render();
            }).show();
        });
        enable=button("启用自动关闭媒体",v->confirmEnable());root.addView(enable);
        disable=button("停用自动处理",v->{prefs.edit().putBoolean(BluetoothPolicyService.ENABLED,false).putString(BluetoothPolicyService.STATUS,"已停用；需要媒体时可在原车设置重新勾选").commit();stopService(new Intent(this,BluetoothPolicyService.class));render();});root.addView(disable);
        root.addView(label("启用后会有后台常驻通知，并保存你选择的手机。停用后停止自动关闭媒体，不会擅自重新连接媒体。原车若限制开机自启动，需要在下次启动后确认此页状态。",18));
        root.addView(button("返回DiPlay",v->finish()));
        root.addView(label("原车蓝牙：手动同步媒体和通话。自动媒体优化开启时，请先停用自动处理再开启媒体。",18));
        root.addView(button("同步媒体 · 开启",v->manual(false,true)));
        root.addView(button("同步媒体 · 关闭",v->manual(false,false)));
        root.addView(button("同步通话 · 开启",v->manual(true,true)));
        root.addView(button("同步通话 · 关闭",v->manual(true,false)));
        root.addView(button("恢复原媒体同步设置",v->restore(false)));
        root.addView(button("恢复原通话同步设置",v->restore(true)));
        root.addView(label("手动同步设置会保存到原车蓝牙。修改前保留所选手机的原设置；恢复按钮只恢复同步设置，当前连接保持现状。",18));
        profiles.open();
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacksAndMessages(null);super.onPause();}
    @Override protected void onDestroy(){if(profiles!=null)profiles.close();super.onDestroy();}
    private void manual(boolean phone,boolean connect){
        BluetoothDevice device=selected();if(device==null)return;
        if(!phone && connect && prefs.getBoolean(BluetoothPolicyService.ENABLED,false)){new AlertDialog.Builder(this).setMessage("请先停用自动关闭媒体，再开启同步媒体。").setPositiveButton("知道了",null).show();return;}
        if(phone && !connect){new AlertDialog.Builder(this).setMessage("关闭同步通话会断开所选手机的原车蓝牙电话连接。是否继续？").setNegativeButton("取消",null).setPositiveButton("关闭同步通话",(d,w)->applyManual(phone,connect,device)).show();return;}
        applyManual(phone,connect,device);
    }
    private void applyManual(boolean phone,boolean connect,BluetoothDevice device){
        try{boolean accepted=profiles.setConnection(phone,connect,device);prefs.edit().putString(BluetoothPolicyService.STATUS,accepted?"已请求更改，等待系统回读确认":"系统未接受请求").apply();handler.postDelayed(()->render(),1500);}
        catch(Exception e){prefs.edit().putString(BluetoothPolicyService.STATUS,"未完成："+BluetoothProfiles.message(e)).apply();}render();
    }
    private void restore(boolean phone){
        BluetoothDevice device=selected();if(device==null)return;
        try{profiles.restoreSync(phone,device);prefs.edit().putString(BluetoothPolicyService.STATUS,"已回读确认原同步设置恢复；当前连接保持现状").apply();}
        catch(Exception e){prefs.edit().putString(BluetoothPolicyService.STATUS,"恢复未完成："+BluetoothProfiles.message(e)).apply();}render();
    }
    private BluetoothDevice selected(){int n=picker==null?-1:selectedIndex;return n>=0 && n<devices.size()?devices.get(n):null;}
    private void render(){
        if(live==null || prefs==null || isDestroyed())return;boolean active=prefs.getBoolean(BluetoothPolicyService.ENABLED,false);BluetoothDevice d=selected();
        String status=profiles.adapter==null?"车机没有蓝牙适配器":!profiles.adapter.isEnabled()?"车机蓝牙未开启":d==null?"尚未选择配对手机":"电话："+BluetoothProfiles.label(profiles.state(true,d))+"；媒体："+BluetoothProfiles.label(profiles.state(false,d));
        if(!profiles.error.isEmpty())status+="\n接口检查："+profiles.error;
        live.setText((active?"自动处理已启用\n":"自动处理未启用\n")+status);
        history.setText("最近状态："+prefs.getString(BluetoothPolicyService.STATUS,"尚未执行自动处理"));
        if(autoDiPlay!=null){renderingAuto=true;autoDiPlay.setChecked(prefs.getBoolean(BluetoothPolicyService.AUTO_DIPLAY,false));renderingAuto=false;autoDiPlay.setEnabled(d!=null);launchStatus.setText("DiPlay："+prefs.getString(BluetoothPolicyService.LAUNCH_STATUS,"自动打开未启用"));}
        if(enable!=null){enable.setEnabled(!active && d!=null && profiles.error.isEmpty());disable.setEnabled(active);picker.setEnabled(!active && !prefs.getBoolean(BluetoothPolicyService.AUTO_DIPLAY,false) && !devices.isEmpty());picker.setText("iPhone · "+deviceNames.get(selectedIndex));}
    }
    private void confirmEnable(){
        BluetoothDevice d=selected();if(d==null)return;
        if(profiles.state(true,d)<0 || profiles.state(false,d)<0){new AlertDialog.Builder(this).setTitle("接口尚未就绪").setMessage("等待电话和媒体状态均能读取后再启用，不会猜测操作。").setPositiveButton("知道了",null).show();return;}
        new AlertDialog.Builder(this).setTitle("对此手机启用自动关闭媒体？").setMessage((d.getName()==null?"已配对设备":d.getName())+"\n"+d.getAddress()+"\n\n手机电话连接后自动关闭媒体；蓝牙直接启动由上方独立开关控制。原车蓝牙音乐将无法通过这部手机播放；DiPlay 可继续走车机热点。可随时在此页或常驻通知停用。").setNegativeButton("取消",null).setPositiveButton("启用",(dialog,which)->{
            com.shilapi.xcertplay.AionIntegration.savePhone(this,d.getAddress(),d.getName()==null?"iPhone":d.getName());
            prefs.edit().putString(BluetoothPolicyService.ADDRESS,d.getAddress()).putString(BluetoothPolicyService.NAME,d.getName()).putBoolean(BluetoothPolicyService.ENABLED,true).putString(BluetoothPolicyService.STATUS,"已启用，正在检查连接").commit();BluetoothPolicyService.startIfEnabled(this);render();
        }).show();
    }
}
