package com.shilapi.xcertplay.aion;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.shilapi.xcertplay.AionIntegration;

@android.annotation.TargetApi(27)
public final class InstrumentMapActivity extends AionPanelActivity {
    private TextView state;
    private final Handler handler=new Handler();
    private final Runnable refresh=new Runnable(){public void run(){show();handler.postDelayed(this,2000);}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout page=AionUi.page(this,"埃安仪表地图（实验性）","在仪表显示 CarPlay 地图，地图流或连接结束后恢复原车画面。启用或停用在下次连接生效，请返回 DiPlay 点击“立即重连”。首次验证请停车检查车速和挡位仍清楚可见。");
        CheckBox automatic=new CheckBox(this);automatic.setText("自动跟随 CarPlay 地图");automatic.setTextSize(21);
        automatic.setChecked(AionMapSettings.prefs(this).getBoolean(AionMapSettings.AUTO_ENABLED,false));AionUi.add(page,automatic);
        automatic.setEnabled(AionIntegration.supported());
        automatic.setOnCheckedChangeListener((v,on)->{
            AionMapSettings.setEnabled(this,on);show();
        });
        state=AionUi.text(page,"",18);
        AionUi.button(page,"立即恢复原车地图",()->{if(AionIntegration.supported())AionMapSettings.pause(this,true);});
        AionUi.button(page,"恢复自动跟随",()->{if(AionIntegration.supported() && AionMapSettings.prefs(this).getBoolean(AionMapSettings.AUTO_ENABLED,false))AionMapSettings.pause(this,false);});
        AionUi.button(page,"返回 DiPlay",()->finish());
    }
    private void show(){if(state!=null)state.setText((AionIntegration.supported()?"":"车型或固件与实车记录不匹配，未启用地图。\n")+
        AionMapSettings.prefs(this).getString("status","尚未启用仪表地图"));}
    @Override public void onResume(){super.onResume();handler.post(refresh);}
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
