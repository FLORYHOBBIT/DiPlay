package com.shilapi.xcertplay.aion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fixed allowlist: no arbitrary shell commands or secure-settings writes. */
@android.annotation.TargetApi(27)
public final class PermissionPolicy {
    public static final String WRITE = "WRITE_SETTINGS";
    public static final String OVERLAY = "SYSTEM_ALERT_WINDOW";
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+");
    private static final Pattern MODE = Pattern.compile("allow|ignore|deny|default|foreground|ask");
    private PermissionPolicy() {}

    public static String packageName(String value) {
        if (value == null || !PACKAGE.matcher(value).matches() || value.length() > 240)
            throw new IllegalArgumentException("应用包名无效，已停止操作");
        return value;
    }
    public static String operation(String value) {
        if (!WRITE.equals(value) && !OVERLAY.equals(value))
            throw new IllegalArgumentException("此权限不在助手允许修改的范围内");
        return value;
    }
    public static String mode(String value) {
        if (value == null || !MODE.matcher(value).matches())
            throw new IllegalArgumentException("无法识别授权模式，已停止操作");
        return value;
    }
    public static String query(String pkg, String op) {
        return "appops get " + packageName(pkg) + " " + operation(op);
    }
    public static String change(String pkg, String op, String mode) {
        return "appops set " + packageName(pkg) + " " + operation(op) + " " + mode(mode);
    }
    public static String parseMode(String text, String op) {
        operation(op);
        if (text != null && Pattern.compile("(?i)uid\\s+mode\\s*:").matcher(text).find())
            throw new IllegalArgumentException("存在 UID 级授权模式，助手不覆盖此类设置");
        if (text != null && text.trim().equals("No operations.")) return "default";
        Matcher m = Pattern.compile("(?m)^\\s*" + op + ":\\s*([a-z]+)(?:\\b)").matcher(text == null ? "" : text);
        if (!m.find()) throw new IllegalArgumentException("未取得明确的授权状态，未执行修改");
        String result = mode(m.group(1));
        if (m.find()) throw new IllegalArgumentException("授权状态有多条结果，未执行修改");
        return result;
    }
    public static String label(String mode) {
        if ("allow".equals(mode)) return "已允许";
        if ("default".equals(mode)) return "系统默认（可能仍需授权）";
        if ("ignore".equals(mode) || "deny".equals(mode)) return "未允许";
        return "受限制：" + mode;
    }
    public static String guarded(String command) {
        return "if [ \"$(id -u)\" != \"2000\" ]; then echo 'Unsupported ADB uid'; exit 91; fi; "
                + command + "; result=$?; echo __CAR_PERMISSION_EXIT_${result}";
    }
    public static String checkedOutput(String output) {
        Matcher m = Pattern.compile("(?s)^(.*?)(?:\\r?\\n)?__CAR_PERMISSION_EXIT_([0-9]+)\\s*$").matcher(output);
        if (!m.matches()) throw new IllegalStateException("未收到完整执行结果；请先检查状态，勿重复授权");
        if (!"0".equals(m.group(2)))
            throw new IllegalStateException("系统拒绝此操作（" + m.group(2) + "）：" + m.group(1).trim());
        return m.group(1).trim();
    }
}
