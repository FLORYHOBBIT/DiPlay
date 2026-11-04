package com.shilapi.xcertplay.aion;

import android.app.Activity;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared dp dimensions for the Chinese-only AION panels; controls expand for large text. */
@android.annotation.TargetApi(27)
final class AionUi {
    static final int BLOCK_GAP_DP=12, PAGE_PADDING_DP=20, CONTROL_HEIGHT_DP=56;
    static final int CARD_RADIUS_DP=18, APP_LIST_MAX_WIDTH_DP=360;
    static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static LinearLayout page(Activity a,String title,String description){
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setBackgroundColor(0xffeaf4f8);
        LinearLayout page=new LinearLayout(a);page.setOrientation(LinearLayout.VERTICAL);
        int pad=dp(a,PAGE_PADDING_DP);page.setPadding(pad,pad,pad,pad);page.setBackgroundColor(0xffeaf4f8);
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));a.setContentView(scroll);
        text(page,title,26);text(page,description,18);return page;
    }
    static TextView text(LinearLayout page,String value,int size){
        TextView t=new TextView(page.getContext());t.setText(value);t.setTextSize(size);t.setTextColor(0xff173546);
        add(page,t);return t;
    }
    static void add(LinearLayout page,android.view.View view){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(page.getContext(),BLOCK_GAP_DP);page.addView(view,p);
    }
    static void button(LinearLayout page,String title,Runnable action){
        Button b=new Button(page.getContext());b.setText(title);b.setTextSize(18);b.setAllCaps(false);
        b.setMinHeight(dp(page.getContext(),CONTROL_HEIGHT_DP));b.setOnClickListener(v->action.run());add(page,b);
    }
}
