<p align="center">
  <a href="https://cleanyour.tv"><img src="https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/banner.png" alt="CleanYourTV。你的电视，去掉广告。" width="800"></a>
</p>

<p align="center">
  <a href="README.md">English</a> · <b>简体中文</b>
</p>

<p align="center">
  <a href="https://cleanyour.tv">cleanyour.tv</a> ·
  <a href="PROMPT.md">提示词</a> ·
  <a href="examples/">54 份电视清单</a> ·
  <a href="SHOWCASE.md">12 个衍生项目</a> ·
  <a href="https://x.com/gathint/status/2111379569030939102">那条推文</a>
</p>

<p align="center">
  <a href="https://github.com/Gathin23/cleanyourtv/stargazers"><img src="https://img.shields.io/github/stars/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Stars"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/pulls?q=is%3Amerged"><img src="https://img.shields.io/github/issues-pr-closed/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&label=merged%20PRs&color=cfffcf&labelColor=15181e" alt="Merged PRs"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/network/members"><img src="https://img.shields.io/github/forks/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Forks"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://img.shields.io/github/contributors/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Contributors"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/licence-MIT-cfffcf?style=flat&labelColor=15181e" alt="MIT"></a>
  <a href="https://buymeacoffee.com/gathint"><img src="https://img.shields.io/badge/support-%E2%9D%A4-ff5e8a?style=flat&labelColor=15181e" alt="Support, buy me a coffee"></a>
</p>

通过 Wi-Fi 给任意 Android TV 或 Google TV 去掉预装臃肿软件，再把满是广告的桌面换成干净的、长得像 Apple TV 的启动器。**不用 root，不用接线，不卸载任何东西，反悔了有一行命令还原。**

<h3 align="center">54 台已验证清单的电视 &nbsp;·&nbsp; 23 个品牌 &nbsp;·&nbsp; 12 种语言 &nbsp;·&nbsp; 280 KB 启动器</h3>

![改造前：原厂 Google TV 桌面，带赞助广告。改造后：Clean Your TV 启动器](docs/stock-vs-new-side.jpg)

> 今天下午用 vibe coding 给我爸妈的电视做了一遍！还没进 Netflix 就先看到一个赞助广告。把型号丢给 Claude Code，它自己在 Wi-Fi 上找到了电视，删掉 25 个小米包，还写了个新启动器。现在看起来像 Apple TV 了哈哈。
>
> [@gathint，2026 年 10 月 17 日](https://x.com/gathint/status/2111379569030939102)。这里的电视清单大多来自那条推文的回复。仓库 19 号之前叫 tv-vibehack，旧链接仍然可用。

## 工作原理

Android 电视和 Android 手机用的是同一套调试桥。在电视的开发者选项里打开 USB 调试，在电脑上允许一次，剩下的都是标准的 Android 工具链：

1. **备份**已安装软件包和设置的清单。
2. **禁用**厂商的遥测、广告服务、内容推荐位和预装垃圾，用 `pm disable-user`。不卸载任何东西，所以还原脚本可以撤销每一步。
3. **安装**一个小巧的原生启动器，并把它设为默认桌面。

各家品牌的臃肿软件不一样，所以提示词让你的编程助手在电视上自己发现它们，并在动到你可能要用的东西之前先问你。

## 快速开始

你需要一台与电视处于同一 Wi-Fi 的电脑，以及一个能执行 shell 命令的编程助手（Claude Code 或类似工具）。

| | |
|---|---|
| **1. 在电视上** | 设置 → 设备偏好设置 → 关于 → 在 **版本号** 上连按 OK 键七次 → 返回 → 开发者选项 → 打开 **USB 调试**。如果看到“网络调试”或“ADB 调试”开关，也一并打开。让电视保持开机。 |
| **2. 在电脑上** | 把 [`PROMPT.md`](PROMPT.md) 粘贴给助手，填上你的电视型号。 |
| **3. 跟着走** | 在电视上接受“允许 USB 调试？”，然后回答清单里的问题：你用哪些应用、哪四个放进 Dock、是否保留语音助手、Chromecast 和系统更新。 |

助手会备份、去臃肿、编译并安装启动器，并从电视上截图核对每一步。以本仓库作为上下文，整个过程大约十五分钟。

## 你会得到什么

- **纯黑桌面**，Dock 里放你最常用的四个应用，位置就是 Apple TV 放 Dock 的地方，圆角条带。
- **顶部信息条**用大字号显示当前聚焦应用的名字，并以取自其封面的颜色做环境光晕。
- **其余应用排在网格里**，按向下键时向上滑出。
- **一个漂浮的玻璃高亮框**在图标之间弹跳。聚焦的图标会抬起并显示名称。
- **三个磨砂按钮**：输入源（HDMI 接口、AV、Live TV）、显示网络名称的 Wi-Fi 或以太网、设置。
- **[Outfit](https://fonts.google.com/specimen/Outfit) 字体的问候语、细线时钟和日期**，以及配套的时钟屏保。
- **主题**可以更换配色、壁纸、Dock 和列数，无需重新编译；**模块卡片**让其他应用插入自己的图标，且必须经你同意。

以及一台不再跟你作对的电视：没有赞助推荐位，没有“发现”弹窗，没有偷偷回传的分析数据，没有你从没要过的应用。

| | |
|---|---|
| ![顶部信息条在环境光晕上显示聚焦应用名](docs/focus-2-up-netflix.png) | ![输入源弹窗：HDMI 接口及连接状态、AV 和 Live TV](docs/inputs-menu.jpg) |
| *顶部信息条与环境光晕* | *输入源：HDMI 及连接状态、AV、Live TV* |
| ![网格、落在图标上的玻璃高亮框及其下方名称](docs/focus-1-fancode.png) | ![时钟屏保：细线时钟在光晕上漂移](docs/clock-screensaver.png) |
| *网格、一个玻璃高亮框、图标名称* | *时钟屏保，会漂移，避免烧屏* |
| ![极光主题：壁纸与染色胶囊按钮，无需重新编译即可推送](docs/theme-aurora.jpg) | ![主题 config.json 里的六列布局与六应用 Dock](docs/theme-six-columns.jpg) |
| *极光主题，用 `tools/theme.sh` 推送* | *六列与六应用 Dock，同一个启动器* |

## 支持的电视

任何运行 **Android TV** 或 **Google TV** 的设备。三星（Tizen）和 LG（webOS）是另一套系统；webOS 移植见 [SHOWCASE.md](SHOWCASE.md)。**Fire TV** 只能做去臃肿那一半，因为 Fire OS 不会把桌面交给第三方启动器。少数中国市场的 TCL 机型完全拒绝安装任何启动器；对应清单的文件头里写明了。

来自贡献者的已验证清单，每台机型一个文件夹，位于 [`examples/`](examples/)：

**Xiaomi** 12 · **TCL** 11 · **Sony** 8 · **CHiQ** 2 · **Hisense** 2 · **Philips** 2 · Amazon Fire TV Stick · BenQ ·
Chromecast with Google TV · Hitachi · Insignia Fire TV · NVIDIA Shield · OnePlus · Panasonic · RCA · Redmi ·
SANYO · Skyworth · Thomson · Toshiba Fire TV · Vu · Stellamore 与 XGIMI 投影仪

<details>
<summary><strong>完整清单</strong></summary>

| 电视 | 系统 |
|---|---|
| Amazon Fire TV Stick 4K，2018 年第一代（AFTMM，mantis） | Fire OS 6.7.1.1 / Android 7.1 |
| BenQ E55-735（长虹 AI PONT，DVB96BQ） | Google TV 14 |
| CHiQ AI PONT（长虹 GTVU65，dangni，联发科 mt5867） | Google TV 14 |
| CHiQ AI PONT（DVB93CQ） | Android TV 11 |
| Chromecast with Google TV 4K（sabrina） | Google TV 14 |
| Hisense HiSmartTV A4（xiaoyushan，MStar m7322），印度 | Android TV 9 |
| Hisense SmartTV 4K FFM（huangshan，联发科） | Android TV 10 |
| Hitachi 65QLEDSM20（MTC，bangbae，联发科 m7322），澳大利亚 | Android TV 11 |
| Insignia 55" F30 4K Fire TV（NS-55F301NA25，AFTDCT31，duckie） | Fire OS 7 / Android 9 |
| NVIDIA SHIELD TV Pro 2019（P2897，mdarcy） | Android TV 11 |
| OnePlus TV（D00，Dubai） | Google TV 14 |
| Panasonic 4KTV-JUP（Jupiter_PA，联发科） | Android TV 11 |
| Philips 50PUS8517/12（TP Vision，PH1M_EA_9970A，联发科 MT5895），欧洲 | Android TV 11 |
| Philips Smart 4K TV（创维，eleonas，联发科），印度 | Google TV 14 |
| RCA AI PONT SA（长虹，redwood，ISDB92RC；也以 realme 40 英寸销售） | Android 14 |
| Redmi TV（MiTV-ANTL0，starwars） | Android 5.1.1（不是 Android TV） |
| SANYO ATV R2（SCBC，瑞昱 RTD285O） | Android TV 9 |
| Skyworth SWTV-21AE（sunnyvale，联发科） | Android TV 14 |
| Sony BRAVIA 4K AE2（BRAVIA_AE2，联发科 MT5897），北美 | Google TV 14 |
| Sony BRAVIA 4K GB（BRAVIA_ATV2_EU，BRAVIA_ATV2） | Android TV 9 |
| Sony BRAVIA 4K UR2（BRAVIA_UR2_4K，联发科） | Android TV 10 |
| Sony BRAVIA 4K VH2（KD-55X80J，BRAVIA_VH2，联发科），欧盟 | Google TV 12 |
| Sony BRAVIA 9 II（K-65XR90M2，BRAVIA_AE3，联发科 MT5838） | Google TV 14 |
| Sony BRAVIA BF1（BRAVIA 2 II 系列，BRAVIA_BF1_4K_A，联发科） | Google TV 12 |
| Sony BRAVIA KD-65X80J（2021，BRAVIA_VH2，联发科），印度 | Google TV 10 |
| Sony BRAVIA XBR-65X900E（2017，BRAVIA_ATV2） | Android TV 9 |
| Stellamore T950S 投影仪（晶晨 be30a3，Amlogic txhd2） | Android 13 |
| TCL 40S5400A（C06，C06_2K_GB，联发科 MT9221，1 GB） | Android TV 11 |
| TCL 50EP680（欧洲，BeyondTV，瑞昱 rtd285o） | Android TV 11 |
| TCL 55" 4K Google TV，2023（G10，G10_4K_US_NF） | Google TV 12 |
| TCL 55Q10G Pro（中国，tcl_mt5879_cn，联发科 MT9952） | Android TV 11 |
| TCL 65C725（Smart TV Pro，BeyondTV4） | Android TV 11 |
| TCL ak30a5（中国，晶晨） | Android TV 9 |
| TCL BeyondTV（印度） | Android TV 9 |
| TCL BeyondTV，欧区/北欧（瑞昱 RTD2851，V8-R851T02-LF1V606） | Android TV 9 |
| TCL BeyondTV2（拉美/巴西，瑞昱 RTD2851） | Android TV 9 |
| TCL QM6K Pro，2025（G08，联发科 tcl9618） | Google TV 14 |
| TCL Smart TV Pro（G05，G05_4K_US，联发科 MT5889） | Google TV 11 |
| Thomson 32HE5800（TCL 代工 UnionTV，瑞昱 RT2841），西班牙 | Android TV 9 |
| Toshiba Fire TV（AFTHA004，hazel） | Fire OS 7 / Android 9 |
| Vu 4K（HVCL，lushan，联发科），印度 | Google TV 12 |
| XGIMI Horizon S Pro 投影仪（XK03H，xgimigalileo，联发科） | Android TV 11 |
| Xiaomi Mi Box S（MIBOX4，oneday），电视盒子 | Android TV 9 |
| Xiaomi Mi Box S 第二代（MiTV-AFKR0，jaws），电视盒子 | Google TV 11 |
| Xiaomi Mi TV（MiTV-MOOQ0，croods） | Android TV 10 |
| Xiaomi Mi TV 4A（MiTV-MOOQ1） | Android TV 10 |
| Xiaomi MiTV-AESP0 电视棒（aquaman，1 GB） | Android TV 9 |
| Xiaomi MiTV-ASTP1（maverick），中国固件 2.40.641.0514.cn，1 GB 内存，720p | Android TV 9（中国版） |
| Xiaomi MiTV-AXSO0（dangal；Mi TV 4C Pro 2019 / 4A Pro） | Android TV 9 |
| Xiaomi MiTV-MOOR2（venom，联发科） | Google TV 11 |
| Xiaomi MiTV-MOSR4（irobot，联发科） | Google TV 11 |
| Xiaomi MiTV-MSSP3（machuca，联发科） | Android TV 11 |
| Xiaomi TV A Pro 75" 2026（MiTV-MZTU0，river，联发科） | Google TV 14 |
| Xiaomi TV Box S 第三代（MiTV-AFMU0，twilight，晶晨），电视盒子 | Google TV 14 |

</details>

没有你的电视？提示词会自己去发现臃肿软件。之后把你的清单加进来，下一个用同型号的人就能跳过这一步：[CONTRIBUTING.md](CONTRIBUTING.md)。

## 手动操作

助手做的每件事，你都可以自己跑。这些工具只需要 Python 3。

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # 你电视的 IP 地址

tools/venv/bin/python -I tools/tv.py --probe                 # 型号、Android 版本、固件
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # 在电视上执行一条 shell 命令
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # 应用一份去臃肿清单
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # 撤销，原厂桌面回来
TV_HOST=192.168.1.50 launcher/build-and-install.sh           # 编译启动器并设为桌面
tools/theme.sh themes/aurora                                 # 推送主题；--reset 移除
```

| 工具 | 作用 |
|---|---|
| [`tools/tv.py`](tools/tv.py) | 纯 Python 的 ADB 客户端：shell、批量、推送、拉取。在原版 `adb` 二进制被拦截的环境里也能用。 |
| [`tools/b64push.py`](tools/b64push.py) | 以 base64 分块上传 APK 等大文件，并校验校验和。 |
| [`tools/disable.sh`](tools/disable.sh)、[`restore.sh`](tools/restore.sh) | 应用或撤销一份去臃肿清单。 |
| [`tools/theme.sh`](tools/theme.sh) | 把 [`themes/`](themes/) 里的一个文件夹推送到启动器并重启桌面。 |
| [`launcher/build-and-install.sh`](launcher/build-and-install.sh) | 编译精简版（约 280 KB）、安装、切换桌面，如果桌面没落到它上面就回滚。 |

传给 `disable.sh` 和 `restore.sh` 的清单路径相对于你执行命令时所在的目录，所以自己的清单可以放在仓库之外。绝对路径同样可用。

每个脚本旁边都有一个 PowerShell 版本。在装了 Android Studio 的 Windows 上不需要别的：

```powershell
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
```

安装脚本会先问电视现在哪个启动器占着桌面，只禁用它能识别的（`tvlauncher` 或 `launcherx`）；用 `STOCK_LAUNCHER=<package>`（`-StockLauncher`）覆盖，或传 `none` 表示不动它。

## 启动器

一个小巧的原生 Android TV 应用（`com.gathin.tvhome`，安装后约 280 KB），纯 Java，只用 AndroidX RecyclerView，没有更重的东西。为 1 GB 内存的机型而做：没有模糊、没有视频、没有网络、没有后台服务。编译需要 JDK 17 和 Android SDK platform 34；在 macOS 上 `brew install openjdk@17 android-commandlinetools`，再 `sdkmanager "platforms;android-34" "build-tools;34.0.0"`。在 Linux 上安装脚本会自己找 `~/Android/Sdk` 和 PATH 里的 `java`。

- **配置**在 [`config.xml`](launcher/app/src/main/res/values/config.xml)：Dock 顺序、隐藏应用、Live TV 包名、列数（图标从 5 列起缩小）、Dock 下方行数、网络胶囊按钮可选显示的 VPN 应用，以及给 GPU 跟不上的电视用的 `reduced_motion`。
- **主题**设置同样的键，外加背景色和壁纸，通过推送到电视的 `config.json` 生效，无需重新编译。[`themes/`](themes/) 自带三套，文档也在那里。
- **语言**跟随电视：英语、德语、西班牙语、加泰罗尼亚语、法语、俄语、塞尔维亚语（拉丁）、波兰语、阿拉伯语、简体中文、泰语、土耳其语。Android 13+ 可以只给启动器设置：`tools/tv.py 'cmd locale set-app-locales com.gathin.tvhome --locales ca'`。
  俄语包含模块卡片审批与管理界面，以及 VPN 关闭徽标。
- **模块卡片**让任意电视应用声明自己的图标（标签、说明、光晕）。启动器对每个应用只问一次，默认保持隐藏，并按该应用的签名密钥和卡片名记住答案。在“设置”上长按 OK 可以改主意。制作方法：[`docs/modules.md`](docs/modules.md)；接口约定：[`docs/specs/card.md`](docs/specs/card.md)。
- **时钟屏保**：在光晕上漂移的时钟，避免烧屏。在电视的屏保设置里选 “Clock”，或用 ADB：
  `tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream; settings put secure screensaver_enabled 1'`
- **遥控按键**：一个可选的无障碍服务让 Google TV 遥控器上的 Input 键继续可用（安装时 `INPUT_KEY_SERVICE=1`）；另一个修好那些 Home 键发送 ALL_APPS 的电视棒，并让遥控器齿轮键打开设置，即使 Android 没有映射该键（`HOME_KEY_SERVICE=1`，扫描码在 `config.xml`）。
- **导航**是刻意设计的：上下键移动一行，顶部按钮不会把焦点漏到图标上，从它们按向下会回到你来的那个 Dock 图标，记住最后聚焦的应用，安装或移除应用时桌面保持打开并刷新图标。
- **封面**：应用横幅以 xhdpi 或更高分辨率加载，低密度电视界面不会在聚焦放大前就先把它降采样。

## 还原你的电视

留好你应用过的清单。还原脚本会精确地重新启用这些包，把原厂启动器恢复为桌面，并重置动画缩放：

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt                                    # Android TV
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt com.google.android.apps.tv.launcherx  # Google TV
```

它会把新启动器留在电视上（想删掉就去电视的 设置 → 应用 里卸载），也不知道你在清单之外禁用过哪些包。以后想切回去，再跑一次安装脚本；即使桌面已经回落到 Android 的选择器，它也能判断该禁用哪个启动器。如果电视连不上，电视自己的 设置 → 应用 页面也能重新启用包。

## 出问题时

完整的踩坑记录在 [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)。最要紧的三个：

- **macOS 在“本地网络”隐私限制下会挡住 `adb connect`**（报 “No route to host”）。`tools/tv.py` 绕开了这个二进制。
- **只在新启动器装好并运行之后再禁用原厂启动器**，否则桌面会落到工厂测试应用上。安装脚本已经替你排好了顺序。
- **在 Google TV 上，设置向导**（`com.google.android.tungsten.setupwraith`）可能抢走桌面，并在原厂启动器被禁用后让系统崩溃重启。把它也禁用，并写进你的 `debloat.txt`，这样还原时能恢复。已经卡住了？`tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`。

## 常见问题

**安全吗？会不会把电视变砖？** 不刷机、不 root、不删除任何东西；包只是在用户配置里被禁用。风险在于禁用了电视依赖的东西，所以清单会小批量应用，并随时备好还原脚本。即使没有这些工具，电视自己的 设置 → 应用 页面也能重新启用包。

**固件更新怎么办？** 大多数清单会禁用更新服务，所以只有你重新启用它时更新才会来。真来了，最坏情况是某个包回来了，重跑一次脚本即可。

**应用会不会坏？** 流媒体应用仍然通过 Play 商店更新，商店没有被碰。启动器只是列出已安装的应用，用十年没变过的 intent 启动它们。

**ADB 访问不是安全漏洞吗？** 它是电视上一次明确的“允许这台电脑”，和开发 Android 应用一样。用完把 USB 调试关掉；启动器不需要它。

**能顺便拦掉应用内的广告吗？** 部分可以，用 Android 的私人 DNS（Android 9 及以上）。指向一个带广告拦截的解析器，比如 [AdGuard DNS](https://adguard-dns.io)，所有应用的广告和追踪域名都不再解析，系统级生效，重启后仍然有效。代价是那个解析器能看到电视发出的每一次 DNS 查询。

```bash
tools/tv.py 'settings put global private_dns_specifier dns.adguard-dns.com'
tools/tv.py 'settings put global private_dns_mode hostname'
```

它去不掉 YouTube 的广告，那些广告和视频来自同一批服务器。如果某个应用停止播放，或电视提示“无网络”（解析器挂了或不可达），用 `tools/tv.py 'settings put global private_dns_mode opportunistic'` 切回去。

**AirPlay 呢？** 本仓库不提供。Android 手机可以继续用内置的 Chromecast。iPhone 用户可以用 [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server)；它的安装脚本见 [PR #84](https://github.com/Gathin23/cleanyourtv/pull/84)。

**能直接下载 APK 吗？** 用那个一行脚本自己编，一分钟就好，而且你清楚自己电视上装了什么。不提供预编译二进制。

## 参与贡献

最有用的贡献是你那台电视的去臃肿清单，这样下一个用同型号的人就不必重新摸索。启动器改进和更准确的提示词措辞也欢迎。[CONTRIBUTING.md](CONTRIBUTING.md) 写了格式和我们要求的测试。基于它做了东西——启动器、移植、主题？加到 [SHOWCASE.md](SHOWCASE.md)，那里已经有十一个项目：LG 的 webOS 移植、Stremio 货架、海信改版、Fire TV 启动器、AirPlay 安装器。

这些工作属于这些人：

<a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://contrib.rocks/image?repo=Gathin23/cleanyourtv&columns=16" alt="贡献者"></a>

<details>
<summary><strong>Star 历史</strong></summary>

<a href="https://star-history.com/#Gathin23/cleanyourtv&Date"><img src="https://api.star-history.com/svg?repos=Gathin23/cleanyourtv&type=Date" alt="Star 历史图表" width="640"></a>

</details>

## 许可

MIT，见 [`LICENSE`](LICENSE)。Outfit 字体使用 SIL Open Font License，见 `launcher/FONT-LICENSE-OFL.txt`。

---

英文版 [`README.md`](README.md) 是源文件；两边内容不一致时以英文为准。
