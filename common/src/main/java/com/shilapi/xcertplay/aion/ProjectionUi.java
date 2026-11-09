package com.shilapi.xcertplay.aion;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

/** Shared visual system for the AION settings panels. */
final class ProjectionUi {
    static final int GAP=16, PAD=24, HEIGHT=60, MAX_WIDTH=880;
    static final int BG=0xff0c131e, CARD=0xff182332, TEXT=0xffedf3fc, MUTED=0xffa6b6cd, ACCENT=0xffa7c8ff;
    static int dp(android.content.Context c,int n){return AionUi.dp(c,n);}
    static GradientDrawable shape(android.content.Context c,int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,HEIGHT/3));return d;
    }
    static LinearLayout page(Activity a,String title,String description){
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout outer=new LinearLayout(a);outer.setGravity(Gravity.CENTER_HORIZONTAL);outer.setOrientation(1);
        int pad=dp(a,PAD);outer.setPadding(pad,pad,pad,pad);
        LinearLayout page=new LinearLayout(a);page.setOrientation(1);
        int available=a.getResources().getDisplayMetrics().widthPixels-pad*2;
        outer.addView(page,new LinearLayout.LayoutParams(Math.min(available,dp(a,MAX_WIDTH)),-2));
        scroll.addView(outer);a.setContentView(scroll);
        text(page,"埃安设置  /  DiPlay",14).setTextColor(ACCENT);
        TextView heading=text(page,title,28);heading.setTypeface(null,Typeface.BOLD);
        text(page,description,16).setTextColor(MUTED);
        return page;
    }
    static void add(LinearLayout page,android.view.View view){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(page.getContext(),GAP);page.addView(view,lp);
    }
    static TextView text(LinearLayout page,String value,int size){
        TextView t=new TextView(page.getContext());t.setText(value);t.setTextColor(TEXT);t.setTextSize(size);t.setLineSpacing(dp(page.getContext(),4),1f);add(page,t);return t;
    }
    static Switch toggle(LinearLayout page,String label){
        Switch s=new Switch(page.getContext());s.setText(label);s.setTextColor(TEXT);s.setTextSize(19);
        s.setMinHeight(dp(page.getContext(),HEIGHT));int p=dp(page.getContext(),PAD);s.setPadding(p,p,p,p);
        s.setBackground(shape(page.getContext(),CARD));s.setSwitchPadding(dp(page.getContext(),GAP));
        s.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ACCENT,0xff8090a8}));
        add(page,s);return s;
    }
    static TextView status(LinearLayout page){
        text(page,"当前状态",14).setTextColor(MUTED);
        TextView t=text(page,"",18);int p=dp(page.getContext(),PAD);t.setPadding(p,p,p,p);t.setBackground(shape(page.getContext(),CARD));return t;
    }
    static Button makeButton(android.content.Context context,String title,android.view.View.OnClickListener action){
        Button b=new Button(context);b.setText(title);b.setTextSize(18);b.setTextColor(ACCENT);b.setAllCaps(false);
        b.setMinHeight(dp(context,HEIGHT));b.setPadding(dp(context,PAD),dp(context,GAP),dp(context,PAD),dp(context,GAP));
        b.setBackground(shape(context,0xff22334b));b.setOnClickListener(action);return b;
    }
    static void button(LinearLayout page,String title,Runnable action){add(page,makeButton(page.getContext(),title,v->action.run()));}
    static LinearLayout card(LinearLayout page,String title){
        LinearLayout card=new LinearLayout(page.getContext());card.setOrientation(1);
        int p=dp(page.getContext(),PAD);card.setPadding(p,p,p,p);card.setBackground(shape(page.getContext(),CARD));
        add(page,card);text(card,title,20).setTypeface(null,Typeface.BOLD);return card;
    }
}
