package com.shilapi.xcertplay.aion;

/** Interfaces measured on this car; an arbitrary passenger/virtual display is never a target. */
public final class AionDevicePolicy {
    public static final int WIDTH=1920, HEIGHT=720;
    public static final String MUSIC_PACKAGE="com.desay_sv.mutiscreen";
    public static boolean target(int api,String model,String firmware){
        return api==27 && "G6SA-r8a7796".equals(model) && "GAC_A29_AVNT_02_31.59_220908_R".equals(firmware);
    }
    public static boolean instrument(int id,String name,int width,int height){
        return id==1 && "HDMI 屏幕".equals(name) && width==WIDTH && height==HEIGHT;
    }
    public static boolean validTitle(String text){return text!=null && !text.trim().isEmpty() && text.length()<=256;}
    public static boolean project(boolean enabled,boolean paused,boolean sessionReady,boolean streamActive){
        return enabled && !paused && sessionReady && streamActive;
    }
}
