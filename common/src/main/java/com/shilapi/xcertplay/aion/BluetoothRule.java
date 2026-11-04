package com.shilapi.xcertplay.aion;

@android.annotation.TargetApi(27)
final class BluetoothRule {
    private BluetoothRule() {}
    static boolean shouldDisconnect(boolean enabled,boolean selectedBonded,int phone,int media) {
        return enabled && selectedBonded && phone==2 && (media==1 || media==2);
    }
}
