# DiPlay 埃安 Android 8.1 精简版

版本码 4203，版本名 `0.2.22-aion-lite`，包名 `com.shihab.diplay.aion`。使用与之前埃安测试包相同的本机签名，可以保留配对、热点和权限配置更新安装。原桌面 `DiPlay-0.2.22.apk` 只读，之前交付的两个 APK 也保留。

## 体积处理

上一版是未开启 R8 的 debug 构建，调试依赖和未压缩的程序代码使包体积超过原版。此次新增独立 `aionSlim` 构建，关闭调试并开启 R8 代码优化和资源裁剪；通过产品常量移除埃安不使用的分支，而不止隐藏设置入口。官方构建机制参考：[Android R8 优化](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)。

- 固定简体中文及 ARM 32/64 位，兼容 Android 8.1/API 27。
- 删除 APK 中的比亚迪仪表 Activity、外部地图嵌入服务、桌面导航小部件、比亚迪方向盘无障碍服务、停车视频播放器入口。未使用的播放器依赖由 R8 裁剪。
- 设置构造时直接跳过比亚迪、氛围灯、实验显示、额外桌面浮窗等功能，供 R8 删除相关代码和资源。
- 不初始化比亚迪导航输出、蓝牙挂起恢复、仪表监控及每秒转向覆盖层刷新。
- 删除不使用的自动更新 WorkManager、在线 Emoji 字体和 API 28+ profile 安装器自动初始化；保留应用生命周期初始化与系统字库显示。
- 排除未使用的后量子密码资源；保留 CarPlay 实际使用的加密算法、认证资源、JNI 入口及权限管理 shell 入口。
- 保留原埃安返回图标、无线/USB CarPlay、麦克风与通话、音乐封面、物理仪表地图、定位、显示/音频调整及权限管理。

最终体积、压缩前后分类统计、APK 校验和和被裁剪类清单见交付目录的 `验证结果.json`、`体积对比.json`。资源和 DEX 库存是静态检查，不代表这些功能已在车上运行通过。

## 启动优化

1. 蓝牙系统广播直接启动 CarPlayHostActivity，不打开助手或先打开设置首页，不等待媒体连接策略处理完成。
   重复广播只在当前进程内去重，不用上次行程保存的开机计时值拦截本次启动。
2. 埃安首次取得屏幕尺寸后立即进入连接检查，移除原先 500 毫秒的冷启动尺寸防抖；运行后的尺寸变化仍防抖，避免反复重连。
3. Android 8.1 连接 RFCOMM 前取消系统蓝牙扫描，即使扫描由原车蓝牙页面发起，也不让扫描继续争用连接过程。取消失败仍继续尝试连接，不增加等待。参考：[Android 蓝牙连接说明](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices)。
4. 不启动比亚迪仪表工作线程、SDK 探测和不适用的后台轮询。
5. 保留协议握手的正常超时与重试；没有把连接超时缩短来制造表面提速。iPhone 进入热点和发起 CarPlay 握手的真实时间只能接车验证。

新增同一进程内的启动计时：从接收到匹配手机的蓝牙启动请求，到实际活动会话回报主屏首帧。成功后“埃安蓝牙与自启动”页的 DiPlay 状态显示本次秒数；同时保存 host、controller、hotspot、bluetooth、first_frame 各阶段相对时间，日志标签为 `AionStartup`。没有触发、启动抛错或没有首帧时不生成成功耗时，不使用固定估算。

“开机到首帧”的总时间还包含原车系统自身启动和手机蓝牙连上的时间；应用内计时从收到蓝牙事件开始，不能替代整车开机计时。

## 使用

更新之前的埃安包，保留数据即可。在“埃安蓝牙与自启动”选择已经配对的 iPhone，开启“蓝牙连接后直接启动 CarPlay”。热点使用原先保存的原车热点参数。先关闭旧助手及原版 DiPlay 的自动功能，避免其他包同时争用仪表或手机连接。

地图和音乐均由本 App 内部数据直出，无 HTTP 封面服务器、额外歌曲监听服务或地图虚拟屏幕。封面仍需原车仪表进程读取本 App 授权的只读 content URI；这一兼容性需要实车确认。原车删减的权限页面通过已经授权的本机 ADB 设置，不需要电脑常驻。

本次没有连接车机，不能承诺从原先一分钟降低到某个秒数。蓝牙冷进程唤醒、热点联通、仪表地图、封面读取和实际连接时间均未实车验证。

## AION 音乐播放稳定性

AION Android 8.1 的音乐 AudioTrack 不启用自适应播放采样率校正，避免追随估计时钟而动态变速导致的音高/听感波动。AAC/PCM 解码、立体声输出、当前媒体缓冲设置保持原值；其他车机继续使用原来的时钟校正。该修复针对 CarPlay 音乐输出链路，仍需在埃安实车用 QQ 音乐验证，暂不能确认“挤压”现象已完全消失。

## 构建与恢复

```powershell
$env:JAVA_HOME='E:\orca\DiPlay\.private\jdk25\jdk-25.0.3+9'
$env:ANDROID_HOME='E:\orca\DiPlay\.android-sdk'
$env:DIPLAY_AUTH_ASSETS_DIR='E:\orca\DiPlay\.private\runtime-assets'
.\gradlew.bat :mobile:assembleStandaloneAionSlim
```

交付的是 `mobile/build/outputs/apk/aionSlim/mobile-aionSlim.apk` 的校验副本，不能把体积较大的 `assembleDebug` 产物误当成本次精简包。R8 mapping 随验证资料保留，用于还原异常堆栈。

本次修改前备份：`.private/backups/aion-lite-20261104/`。最初原版与上一版直连实现的备份继续保留。没有提交、推送或修改 Windows 权限。
