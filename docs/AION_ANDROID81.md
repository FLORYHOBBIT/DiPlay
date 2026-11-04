# DiPlay 埃安 Android 8.1 直连版

本版替代上一版“把助手代码搬进 APK”的实现。应用包名仍为 `com.shihab.diplay.aion`，版本 `0.2.22-aion-direct-test`，版本码 `4202`，可以保留数据更新第一版埃安测试包。

## 实际数据路径

| 功能 | 本版调用路径 |
| --- | --- |
| 蓝牙启动 | 系统受保护的 ACL_CONNECTED 广播 → AionBluetoothReceiver 校验选定手机 → 直接启动 CarPlayHostActivity |
| 歌名/歌词/播放状态 | CarPlay 控制器的内部 iAP2 数据 → AionInstrumentMusic → 原车仪表音乐接口 |
| 封面 | 控制器已收到的封面字节 → 准备本机 PNG 文件 → 授权原车仪表通过 content URI 读取 |
| 地图 | CarPlay stream 111 → DiPlay 原有解码器 → 物理仪表屏的 TextureView Surface |

没有助手应用启动链，没有额外歌曲监听服务，没有 MediaController 读回自身媒体会话，没有本地 HTTP 封面服务器，没有中间虚拟屏幕、ImageReader、截图采样和二次地图转发。Android 标准媒体会话仍供原有媒体按键和系统媒体控制使用，不参与仪表歌曲输出。

原车仪表是系统进程，因此调用它提供的音乐接口及授权它读取封面文件属于必要的原车接口交互；这不经过助手或本机转发服务器。

## 蓝牙自启动

原车开机广播可能被固件拦截，因此实际入口是此前助手能收到的系统蓝牙连接广播。新的接收器属于 DiPlay 自己，不需要先创建媒体策略服务、等电话连接或等媒体断开。

“蓝牙连接后直接启动 CarPlay”与“自动关闭媒体”是独立开关。即使音频优化停用或失败，也不会阻塞蓝牙启动。选定手机匹配后直接进入 CarPlay 连接界面，使用已保存的车机热点配置。同一组重复广播在 15 秒内去重；已有 CarPlay 会话时不反复抢前台。

音频优化后台服务只负责已选手机的原车媒体连接策略，不承担启动或数据转发。同步媒体、同步通话的手动设置继续保留原配置备份和回读确认。

## 地图与歌曲

地图只允许已记录固件上 `display 1 / HDMI 屏幕 / 1920×720`。地图流、会话或屏幕结束时关闭 Presentation，露出原车画面；也可手动立即恢复。首帧前窗口保持透明，不用黑色等待画面覆盖原车。启用或停用地图能力仍需司机主动重连协商，不擅自断开当前会话。

歌曲直接读取控制器持有的最新状态和原始封面字节；歌词变化时保持同一封面 URI，避免反复刷新。图片读取仅支持指定缓存目录下以 SHA-256 命名的 PNG，拒绝写入和目录越界。

## 目标与安装

- Android 8.1 / API 27，Build.MODEL 为 `G6SA-r8a7796`。
- 固件 `GAC_A29_AVNT_02_31.59_220908_R`；原车仪表 `com.desay_sv.mutiscreen`，UID 1000，版本 `V_20220814_1359_CONFOUND`。
- 对应记录为线程 `01a1452c-d2b7-70c3-bc65-e5beeeb04af0`；电脑本次没有连接车机。
- 仅 ARM 32/64 位，固定简体中文，保留通用显示/音频/USB/热点/权限管理；比亚迪专用入口和调试组件已移除或禁用。

先停用旧助手及原版 DiPlay 的自动功能，避免其他包重复占用蓝牙或仪表。安装此 APK 更新第一版埃安包后，选择已配对 iPhone，保存热点名称/密码及权限，开启“蓝牙连接后直接启动 CarPlay”。歌曲与地图在各自类别中开启。

原车删减授权页时，权限管理仍需要已经开启并授权的本机 ADB；普通 APK 无法自行开启调试端口、取得系统/root 权限或绕过固件限制。蓝牙启动、歌曲输出和地图直出本身不依赖电脑常驻或助手。

## 验证边界与备份

本机使用单元测试检查直接启动的目标 Activity、音频策略停用时的启动行为、重复/错误手机事件、歌曲内部数据和只读封面 URI、物理屏 Surface 与无虚拟屏创建，并运行项目 CI、lint 和 APK 构建。布局截图使用 Robolectric 原生图形后端，硬件匹配仅为布局测试替身，不是实车截图。

尚未实车验证：车机冷进程蓝牙唤醒、热点连接、仪表地图、原车对 content URI 封面的读取。旧 CarPlay 麦克风静音/8 声道问题不属于本次已修复范围。原车开机名单未修改，不承诺普通开机广播能绕过它。

完整 CI 中 Windows 缺少符号链接权限的上游测试按实际结果记录，不更改系统配置来绕过。交付目录保留本次验证结果与日志。

本次完整检查：common 1750 项中 1 项会话替换测试失败，随后同一测试类的 16 项和本版直出测试 4 项全部通过；首次失败记录保留，不能据此声称完整 CI 全通过。shared 1798 项中 3 项因 Windows 符号链接权限失败、2 项跳过；home 11 项通过。三个应用的 lint 与 debug 构建完成，最后的 standalone 构建及 mobile lint 通过。

上一版实现的修改前备份在 `.private/backups/aion-direct-20261104/`。最初原配置备份在 `.private/backups/aion-20261104/`。源码没有提交或推送。

构建环境与运行认证资源继续使用项目私有目录：

```powershell
$env:JAVA_HOME='E:\orca\DiPlay\.private\jdk25\jdk-25.0.3+9'
$env:ANDROID_HOME='E:\orca\DiPlay\.android-sdk'
$env:DIPLAY_AUTH_ASSETS_DIR='E:\orca\DiPlay\.private\runtime-assets'
.\gradlew.bat :mobile:assembleStandaloneDebug
```

认证资源沿用原车之前使用的 0.2.22 APK，保存在 `.private/runtime-assets`，不写入 Git。
