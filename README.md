# Local FIFA

俯视角 2D 街机足球，支持**两台安卓手机本地蓝牙联机对战**（经典蓝牙 SPP），也可单人打 AI。

基于开源项目 [ssenegas/kickoff (Open Kick-Off)](https://github.com/ssenegas/kickoff)（MIT 协议）二次开发，
使用 Java + [libGDX](https://libgdx.badlogicgames.com/) 1.12.1。

## 玩法

- **单机**：主菜单 `SOLO MATCH`，操控红队（HOME），蓝队为 AI。
- **蓝牙对战**：`BLUETOOTH MATCH` →
  1. 先在两台手机的**系统蓝牙设置里完成配对**（游戏内不做扫描配对，避免权限纠纷）；
  2. 一台点 `HOST GAME` 等待；另一台在列表里选中对方手机名加入；
  3. 连接后自动进入比赛。主机（红队）跑权威模拟，客机（蓝队）上传操作、接收 30Hz 快照校正。
- **操作**：点按/拖动屏幕任意位置，你方当前控球队员（白圈标记，自动切换到离球最近者）会跑向该点。队员带球碰撞即触球（Kick Off 式），跑动方向即出球方向，朝球门方向冲即可射门。
- 比赛共 3 分钟，进球有开球权轮转，出界自动重开，终场显示比分后退回菜单。断线会有提示并退出。

## 构建

需要 JDK 17 与 Android SDK（platform 34 / build-tools 34.0.0）。macOS 示例：

```bash
brew install openjdk@17 android-commandlinetools
# 接受许可并安装组件
yes | sdkmanager --sdk_root=$HOME/Library/Android/sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0"
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties

export JAVA_HOME=$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home
./gradlew desktop:run             # 桌面版（开发用）
./gradlew android:assembleRelease # 出签名 APK → android/build/outputs/apk/release/
```

发布签名用仓库外自建的 `localfifa.keystore`（PKCS12，口令/别名均为 `localfifa`，见 `android/build.gradle`；密钥文件不入库）。

桌面版联机走 TCP 回环（同一台 Mac 开两个窗口即可对战）。

## 测试

```bash
./gradlew desktop:runSmoke        # 协议回环 + 场地规则几何（headless）
```

双实例自动化对战（真实 TCP 联机、机器人互踢、定时退出、遥测日志）：

```bash
./gradlew desktop:dist
cd assets
java -XstartOnFirstThread -Dlocalfifa.role=host -Dlocalfifa.bot=1 -Dlocalfifa.exitAfter=90 -jar ../desktop/build/libs/desktop-1.0.jar &
sleep 5
java -XstartOnFirstThread -Dlocalfifa.role=join -Dlocalfifa.bot=1 -Dlocalfifa.exitAfter=90 -jar ../desktop/build/libs/desktop-1.0.jar
# 观察 telemetry 行：双端状态镜像、客机 snap 延迟（本地回环 <30ms）
```

## 相对上游的改动

- 补完比赛规则：进球判定（门柱/横梁几何）、比分与开球权轮转、出界重开（边线+底线）、3 分钟计时与终场。
- 控球球员自动切换（离球最近 + 迟滞），客队同样参与触球碰撞（上游只有主队会碰球）。
- 蓝牙 P2P 联机：`net` 包（Link/StreamLink 分帧、Protocol 二进制协议、Host/Client 会话），主机权威 + 30Hz 快照 + 客机本地预测与校正；桌面 TCP 平台用于联调。
- 大厅 UI、比赛 HUD（比分/计时/进球横幅）、断线处理。
- 升级 AGP 8.2.2 / compileSdk 34，移除 iOS 模块。

## 已知限制

- 球员贴图（`assets/entities/style1*.png`、`player.png`）来自 Steve Screech 为上游项目供图，授权不明确——**自用没问题，公开发布前建议换素材**（可参考 MIT 协议的 [soccer-course-assets](https://github.com/nicolasbize/soccer-course-assets)）。
- UI 为英文（位图字体不含中文字形，中文化需引入 gdx-freetype + CJK 字体）。
- 客机视角未做镜像（双方同向观看），射门手感为 Kick Off 式"碰球即踢"，比较硬核。
- 蓝牙对战需系统先配对；未做游戏内设备发现。
