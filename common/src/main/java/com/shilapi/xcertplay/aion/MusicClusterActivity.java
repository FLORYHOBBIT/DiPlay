package com.shilapi.xcertplay.aion;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

@android.annotation.TargetApi(27)
public final class MusicClusterActivity extends AionPanelActivity {
    private TextView state;
    private final Handler handler=new Handler();
    private final Runnable refresh=new Runnable(){public void run(){show();handler.postDelayed(this,2000);}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout page=AionUi.page(this,"埃安仪表歌曲（实验性）","在仪表显示当前 CarPlay 的歌名、歌词、播放状态和封面。歌词取决于 iPhone 音乐应用是否提供；无需通知使用权，开启后立即生效。");
        CheckBox enabled=new CheckBox(this);enabled.setText("开启仪表歌曲同步");enabled.setTextSize(21);
        enabled.setChecked(AionInstrumentMusic.prefs(this).getBoolean(AionInstrumentMusic.ENABLED,false));AionUi.add(page,enabled);
        enabled.setEnabled(com.shilapi.xcertplay.AionIntegration.supported());
        enabled.setOnCheckedChangeListener((v,on)->{
            AionInstrumentMusic.prefs(this).edit().putBoolean(AionInstrumentMusic.ENABLED,on)
                .putString(AionInstrumentMusic.STATUS,on?"等待 DiPlay 歌曲信息":"已停用仪表歌曲同步").commit();
            AionInstrumentMusic.changed(this,on);show();
        });
        state=AionUi.text(page,"",18);AionUi.button(page,"返回 DiPlay",()->finish());
    }
    private void show(){if(state!=null)state.setText((com.shilapi.xcertplay.AionIntegration.supported()?"":"车型或固件与实车记录不匹配，未启用同步。\n")+
        AionInstrumentMusic.prefs(this).getString(AionInstrumentMusic.STATUS,"尚未启用")+"\n当前歌名："+AionInstrumentMusic.prefs(this).getString("title","—"));}
    @Override public void onResume(){super.onResume();handler.post(refresh);}
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
