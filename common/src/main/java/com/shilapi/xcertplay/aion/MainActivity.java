package com.shilapi.xcertplay.aion;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@android.annotation.TargetApi(27)
public final class MainActivity extends AionPanelActivity {
    private static final int INK=0xff173546, TEAL=0xff087f91, MUTED=0xff526d7d;
    private static final String PREF="permission-helper-state";
    private static final String[] OPS={PermissionPolicy.WRITE,PermissionPolicy.OVERLAY};
    private final List<App> all=new ArrayList<>(), filtered=new ArrayList<>();
    private final List<View> interactive=new ArrayList<>();
    private final Button[] grantButtons=new Button[2],restoreButtons=new Button[2];
    private final TextView[] stateViews=new TextView[2];
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Button picker; private EditText search; private CheckBox includeSystem;
    private TextView packageView,connectionView,noticeView; private volatile boolean ready; private boolean busy;
    private App selected; private LocalAdb adb; private SharedPreferences prefs;

    private static final class App {
        final String pkg,name; final boolean system; final int uid;
        App(String p,String n,boolean s,int u){pkg=p;name=n;system=s;uid=u;}
        @Override public String toString(){return name+(system?"［系统，仅查看］":"")+" · "+pkg;}
    }
    interface Work { void run() throws Exception; }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);if(!com.shilapi.xcertplay.AionIntegration.supported()){finish();return;}prefs=getSharedPreferences(PREF,MODE_PRIVATE);adb=new LocalAdb(this);
        build();loadApps();
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(AionUi.BLOCK_GAP_DP),0,dp(AionUi.BLOCK_GAP_DP));return v;}
    private LinearLayout.LayoutParams rowParams(){return new LinearLayout.LayoutParams(-1,-2);}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private Button button(String title,View.OnClickListener listener){Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(18);b.setTextColor(TEAL);b.setMinHeight(dp(AionUi.CONTROL_HEIGHT_DP));b.setOnClickListener(listener);interactive.add(b);return b;}
    private LinearLayout card(LinearLayout parent,String title){LinearLayout c=column();c.setPadding(dp(AionUi.PAGE_PADDING_DP),dp(AionUi.BLOCK_GAP_DP),dp(AionUi.PAGE_PADDING_DP),dp(AionUi.BLOCK_GAP_DP));c.setBackground(background(Color.WHITE,AionUi.CARD_RADIUS_DP));LinearLayout.LayoutParams p=rowParams();p.setMargins(0,0,0,dp(AionUi.BLOCK_GAP_DP));parent.addView(c,p);TextView h=text(title,23,INK);h.setTypeface(null,Typeface.BOLD);c.addView(h);return c;}
    private LinearLayout horizontal(LinearLayout parent){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setGravity(Gravity.CENTER_VERTICAL);parent.addView(r,rowParams());return r;}
    private void equal(LinearLayout row,View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(AionUi.BLOCK_GAP_DP),dp(AionUi.BLOCK_GAP_DP),dp(AionUi.BLOCK_GAP_DP),dp(AionUi.BLOCK_GAP_DP));row.addView(v,p);}

    private void build(){
        boolean compact=getResources().getConfiguration().screenWidthDp < 900 || getResources().getConfiguration().fontScale>1.2f;
        LinearLayout root=column();root.setPadding(dp(AionUi.PAGE_PADDING_DP),dp(AionUi.BLOCK_GAP_DP),dp(AionUi.PAGE_PADDING_DP),dp(AionUi.BLOCK_GAP_DP));root.setBackgroundColor(0xffeaf4f8);
        if(compact){ScrollView whole=new ScrollView(this);whole.setFillViewport(true);whole.setBackgroundColor(0xffeaf4f8);whole.addView(root);setContentView(whole);}else setContentView(root);
        LinearLayout header=horizontal(root);TextView title=text("DiPlay 权限管理",26,INK);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(-1,-2));
        header.addView(text("埃安适配版 · Android "+Build.VERSION.RELEASE,15,MUTED));
        root.addView(text("集中管理授权入口。缺失的授权页面可通过已授权的本机 ADB 处理；系统签名权限与 root 不在管理范围。",17,MUTED));
        LinearLayout body=new LinearLayout(this);body.setOrientation(compact?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);root.addView(body,compact?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(-1,0,1));
        ScrollView leftScroll=new ScrollView(this);LinearLayout left=column();left.setPadding(0,0,dp(AionUi.BLOCK_GAP_DP),0);leftScroll.addView(left);body.addView(leftScroll,compact?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(dp(Math.min(AionUi.APP_LIST_MAX_WIDTH_DP,getResources().getConfiguration().screenWidthDp/3)),-1));
        LinearLayout appCard=card(left,"选择应用");search=new EditText(this);search.setSingleLine(true);search.setTextSize(18);search.setHint("按名称或包名搜索");appCard.addView(search,rowParams());interactive.add(search);
        includeSystem=new CheckBox(this);includeSystem.setText("显示系统应用（只查看）");includeSystem.setTextSize(16);appCard.addView(includeSystem);interactive.add(includeSystem);
        picker=button("应用 · 选择",v->chooseApp());appCard.addView(picker,rowParams());
        packageView=text("加载应用列表…",15,MUTED);appCard.addView(packageView);
        Button refreshApps=button("刷新应用列表",v->loadApps());appCard.addView(refreshApps,rowParams());
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){filter();}public void afterTextChanged(Editable e){}});
        includeSystem.setOnCheckedChangeListener((v,checked)->filter());
        LinearLayout connection=card(left,"本机 ADB");connectionView=text("尚未连接",19,MUTED);connection.addView(connectionView);
        connection.addView(text("仅连接 127.0.0.1:5555。首次使用需电脑开启网络 ADB，并在车机弹窗中允许DiPlay。",16,MUTED));
        connection.addView(button("连接并检查授权",v->connect()),rowParams());
        connection.addView(button("首次连接说明",v->setup()),rowParams());
        LinearLayout restoreInfo=card(left,"可恢复的操作");restoreInfo.addView(text("修改前保存所选应用的原授权模式。恢复按钮只撤回DiPlay记录的这一项变更，不清空应用数据。卸载DiPlay不会自动撤回已授予的权限。",16,MUTED));
        restoreInfo.addView(button("查看本次操作记录",v->showHistory()),rowParams());
        ScrollView rightScroll=new ScrollView(this);LinearLayout right=column();rightScroll.addView(right);body.addView(rightScroll,compact?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-1,1));
        addPermission(right,0,"修改系统设置","允许应用调整受此权限控制的系统设置，例如兼容固件上的热点控制。授予后不代表厂商接口一定支持。",Settings.ACTION_MANAGE_WRITE_SETTINGS);
        addPermission(right,1,"悬浮窗","允许应用在其他应用上方显示窗口。仅为你信任且确有需要的应用开启。",Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        LinearLayout entries=card(right,"其他系统授权入口");entries.addView(text("以下入口由原车系统提供。系统删减了相应页面时，DiPlay 会明确提示；不会静默替换成其他页面。",16,MUTED));
        LinearLayout r1=horizontal(entries);equal(r1,button("应用信息 / 普通权限",v->openForSelected(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)));
        equal(r1,button("应用通知",v->{if(selected!=null){Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);i.putExtra(Settings.EXTRA_APP_PACKAGE,selected.pkg);open(i);}}));
        LinearLayout r2=horizontal(entries);equal(r2,button("无障碍服务",v->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));equal(r2,button("通知使用权",v->open(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))));
        LinearLayout r3=horizontal(entries);equal(r3,button("安装未知应用",v->openForSelected(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)));equal(r3,button("车机系统设置",v->open(new Intent(Settings.ACTION_SETTINGS))));
        noticeView=text("请选择应用。系统入口可直接使用；ADB 修改需要先连接并授权。",17,MUTED);noticeView.setPadding(dp(AionUi.BLOCK_GAP_DP),dp(AionUi.BLOCK_GAP_DP),dp(AionUi.BLOCK_GAP_DP),0);root.addView(noticeView,rowParams());
    }
    private void addPermission(LinearLayout parent,int index,String title,String description,String action){
        LinearLayout c=card(parent,title);c.addView(text(description,17,MUTED));stateViews[index]=text("尚未检查",19,MUTED);c.addView(stateViews[index]);
        LinearLayout r=horizontal(c);equal(r,button("打开系统授权页",v->openForSelected(action)));
        grantButtons[index]=button("通过本机 ADB 允许",v->confirmChange(index,false));equal(r,grantButtons[index]);
        restoreButtons[index]=button("恢复原授权",v->confirmChange(index,true));equal(r,restoreButtons[index]);
    }
    private void loadApps(){
        if(busy)return;all.clear();
        for(ApplicationInfo info:getPackageManager().getInstalledApplications(0)){
            all.add(new App(info.packageName,String.valueOf(info.loadLabel(getPackageManager())),(info.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0,info.uid));
        }
        Collections.sort(all,Comparator.comparing((App a)->a.system).thenComparing(a->a.name));
        filter();
    }
    private void filter(){
        if(picker==null||busy)return;String keep=selected==null?getPackageName():selected.pkg;
        String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);filtered.clear();
        for(App a:all)if((includeSystem.isChecked()||!a.system)&&(a.name.toLowerCase(Locale.ROOT).contains(q)||a.pkg.toLowerCase(Locale.ROOT).contains(q)))filtered.add(a);
        int target=0;for(int i=0;i<filtered.size();i++)if(filtered.get(i).pkg.equals(keep))target=i;
        if(filtered.isEmpty()){selected=null;selectionChanged();}else{selected=filtered.get(target);selectionChanged();}
    }
    private void chooseApp(){
        if(filtered.isEmpty())return;
        String[] names=new String[filtered.size()];for(int i=0;i<names.length;i++)names[i]=filtered.get(i).toString();
        new AlertDialog.Builder(this).setTitle("选择应用").setSingleChoiceItems(names,filtered.indexOf(selected),(dialog,index)->{
            selected=filtered.get(index);selectionChanged();dialog.dismiss();
        }).setNegativeButton("取消",null).show();
    }
    private void selectionChanged(){
        if(packageView==null)return;
        picker.setText("应用 · "+(selected==null?"选择":selected.name));
        packageView.setText(selected==null?"没有匹配的应用":selected.name+"\n"+selected.pkg+"\n"+(selected.system?"系统应用：仅查看和打开设置":"用户应用")+" · UID "+selected.uid);
        for(TextView v:stateViews)if(v!=null)v.setText("尚未检查；点击左侧“连接并检查授权”");
        controls();
    }
    private boolean declared(App app,String op){try{PackageInfo p=getPackageManager().getPackageInfo(app.pkg,PackageManager.GET_PERMISSIONS);String needed="android.permission."+op;if(p.requestedPermissions!=null)for(String s:p.requestedPermissions)if(needed.equals(s))return true;}catch(Exception ignored){}return false;}
    private String journalKey(App app,int index){return app.pkg+"|"+app.uid+"|"+OPS[index];}
    private void controls(){
        for(View v:interactive)v.setEnabled(!busy);
        for(int i=0;i<2;i++){
            boolean eligible=!busy&&ready&&selected!=null&&!selected.system;
            grantButtons[i].setEnabled(eligible&&declared(selected,OPS[i]));
            restoreButtons[i].setEnabled(eligible&&prefs.contains("before:"+journalKey(selected,i)));
        }
    }
    private void postNotice(String value){runOnUiThread(()->{if(!isDestroyed())noticeView.setText(value);});}
    private void task(String message,Work operation){
        if(busy)return;busy=true;controls();noticeView.setText(message);
        worker.execute(()->{try{operation.run();}catch(Exception e){ready=false;postNotice(error(e));runOnUiThread(()->{if(!isDestroyed()){connectionView.setText("连接或操作未完成，请检查后重试");connectionView.setTextColor(0xffa14f26);}});}finally{runOnUiThread(()->{if(!isDestroyed()){busy=false;controls();}});}});
    }
    private String error(Exception e){String m=e.getMessage();if(e instanceof java.net.ConnectException)return "未连接本机 ADB。请先用电脑开启网络 ADB；普通应用不能自行获得 shell 权限。";if(e instanceof java.net.SocketTimeoutException)return "等待 ADB 超时。若车机有调试授权弹窗，请允许后重新连接；修改操作结果不明确时先检查状态。";return "操作未完成："+(m==null?e.getClass().getSimpleName():m);}
    private void connect(){
        final App app=selected;
        task("正在连接本机 ADB；首次连接可能弹出车机调试授权。",()->{
            adb.connect(this::postNotice);ready=true;
            runOnUiThread(()->{if(!isDestroyed()){connectionView.setText("已连接 · 普通 shell");connectionView.setTextColor(TEAL);}});
            if(app!=null)readStates(app);
            postNotice("已完成授权状态检查。只有点击允许并确认后，才会修改所选应用。");
        });
    }
    private void readStates(App app)throws Exception{
        final String[] values=new String[2];
        for(int i=0;i<2;i++)values[i]=declared(app,OPS[i])?PermissionPolicy.label(adb.get(app.pkg,OPS[i],this::postNotice)):"应用未声明此权限";
        runOnUiThread(()->{if(!isDestroyed()&&selected!=null&&selected.pkg.equals(app.pkg))for(int i=0;i<2;i++){stateViews[i].setText(values[i]);stateViews[i].setTextColor(values[i].equals("已允许")?TEAL:MUTED);}});
    }
    private void confirmChange(int index,boolean restore){
        if(busy||!ready||selected==null)return;final App app=selected;if(app.system)return;
        final String op=OPS[index],key=journalKey(app,index);
        if(restore&&!prefs.contains("before:"+key)){alert("无恢复记录","助手没有这项权限的原始状态，不会猜测后覆盖。");return;}
        String mode=restore?prefs.getString("before:"+key,"default"):"allow";
        String description=index==0?"允许该应用修改受此权限控制的系统设置（例如兼容固件上的热点控制）。":"允许该应用在其他应用上方显示窗口。";
        String text=app.name+"\n"+app.pkg+"\n\n"+(restore?"恢复到DiPlay首次修改前保存的模式："+PermissionPolicy.label(mode):description)+"\n\n只处理这一项授权；系统应用不会通过助手修改。";
        new AlertDialog.Builder(this).setTitle(restore?"恢复原授权？":"允许这项权限？").setMessage(text).setNegativeButton("取消",null).setPositiveButton(restore?"恢复":"确认允许",(d,w)->change(app,index,restore)).show();
    }
    private void assertSameApp(App app)throws Exception{
        ApplicationInfo actual=getPackageManager().getApplicationInfo(app.pkg,0);
        if(actual.uid!=app.uid||(actual.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)throw new IllegalStateException("所选应用的身份已变化，已停止修改");
    }
    private void change(App app,int index,boolean restore){
        task("正在处理 "+app.name+" 的这一项授权…",()->{
            assertSameApp(app);String key=journalKey(app,index);String before=adb.get(app.pkg,OPS[index],this::postNotice);
            String last=prefs.getString("applied:"+key,null);
            if(last!=null&&!last.equals(before))throw new IllegalStateException("授权已在助手之外发生变化；本次未覆盖，请先核对当前状态");
            String target=restore?prefs.getString("before:"+key,null):"allow";PermissionPolicy.mode(target);
            if(!restore&&!prefs.contains("before:"+key)&&!prefs.edit().putString("before:"+key,before).commit())throw new IllegalStateException("无法保存原授权状态，未执行修改");
            assertSameApp(app);adb.set(app.pkg,OPS[index],target,this::postNotice);
            if(restore)prefs.edit().remove("before:"+key).remove("applied:"+key).commit();
            else prefs.edit().putString("applied:"+key,target).commit();
            String record=(restore?"恢复":"允许")+" · "+app.name+" · "+OPS[index]+" · "+before+" → "+target;
            String history=prefs.getString("history","");history=record+"\n"+history;if(history.length()>6000)history=history.substring(0,6000);prefs.edit().putString("history",history).commit();
            readStates(app);postNotice("已验证系统返回的授权模式。应用的实际功能是否可用，还取决于厂商固件与应用本身。");
        });
    }
    private void openForSelected(String action){if(selected==null){alert("先选择应用","请在左侧选择要查看的应用。");return;}open(new Intent(action,Uri.parse("package:"+selected.pkg)));}
    private void open(Intent intent){
        if(intent.resolveActivity(getPackageManager())==null){alert("原车系统没有这个入口","未找到对应的系统设置页面。修改系统设置与悬浮窗可尝试本机 ADB；其他项目需要厂商提供页面，助手不会替你猜测或开启。");return;}
        try{startActivity(intent);}catch(Exception e){alert("系统未打开此页面",e.getClass().getSimpleName()+"。可返回DiPlay查看其他可用入口。");}
    }
    private void alert(String title,String content){new AlertDialog.Builder(this).setTitle(title).setMessage(content).setPositiveButton("知道了",null).show();}
    private void setup(){
        String explanation="DiPlay是普通应用，不能自行开启 ADB，也不能获得系统签名或 root 权限。\n\n1. USB 连接可信电脑并开启车机 USB 调试。\n2. 电脑执行：adb tcpip 5555\n3. 返回助手，点“连接并检查授权”，核对后允许车机弹出的调试请求。\n\n助手只连接本机地址，但开启网络 ADB 后，车机调试端口可能同时对同一网络可见。请使用可信热点；操作完成后可由电脑执行 adb usb 恢复 USB 模式，之后助手的 ADB 修改功能将暂时不可用。\n\n开启后只进行你确认的两类 appops 操作，没有任意命令输入框。";
        new AlertDialog.Builder(this).setTitle("首次连接说明").setMessage(explanation).setNegativeButton("关闭",null).setPositiveButton("复制开启命令",(d,w)->{ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("ADB 开启命令","adb tcpip 5555"));noticeView.setText("已复制命令，需在电脑执行；助手没有自行开启网络 ADB。");}).show();
    }
    private void showHistory(){alert("本机操作记录",prefs.getString("history","尚无已验证成功的修改记录。"));}
    @Override public void onDestroy(){worker.shutdown();super.onDestroy();}
}
