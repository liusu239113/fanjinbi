# 钓鱼人生:放置大师模拟 (Fishing Idle)

从 Godot 项目 [gamblers-table / fanjinbi](https://github.com/liusu239113/fanjinbi) 换皮重做的**原生 Android 游戏**。

- **语言**：Kotlin
- **渲染**：Android Canvas（自绘 2D）
- **UI**：Jetpack Compose
- **构建**：Gradle + AGP 8.5.2 / Kotlin 2.0.21 / JDK 17
- **包名**：`com.dshx.game.SU`

## 玩法

原版是「点击金币翻转得钱 → 买助手自动点 → 买升级」的放置点击玩法，本作换成钓鱼题材，数值曲线原样保留：

| 原版 | 本作 |
|------|------|
| 点击金币翻面（50% 成功） | 抛竿 → 等咬钩 → 点击收线 |
| 小/中/大金币 | 小鱼 / 鲤鱼 / 锦鲤 / 巨口鱼 |
| 翻面速度 | 收线速度 |
| 重翻概率 | 自动重抛 |
| 帮手（自动点击） | 自动钓手 |
| 金币落地冲击波 | 鱼群骚动（连锁收线） |
| 悬停自动翻 | 自动收线（咬钩即自动收线）+ 智能浮标（空闲自动抛竿，全程挂机） |

自动化升级是**两级**的，不要混：`auto_reel_unlock`（自动收线）只管「咬钩后不用点」，
`auto_cast_unlock`（智能浮标）才管「空闲时自己下竿」。两条都买了才是真挂机。
它们在 `World.autoCastOnce()` / `World.update()` 里推进，不再依赖鼠标悬停（手机上悬停根本不存在）。

核心循环：

1. 点击水面抛竿，浮标落水
2. 随机一条鱼游向浮标，浮标下沉 = 咬钩
3. 在反应窗口内点击收线，连续点击加速
4. 收线完成得金币；稀有鱼有概率在最后一刻挣脱
5. 金币买鱼苗扩充鱼群、买升级提升收益、雇钓手自动钓鱼

**鱼群是钓场的常驻居民**：被钓起只是播一段动画，随后重新入水，规模由已购鱼苗数量决定，不会越钓越少。

## 数值设计

价格曲线沿用原版 `floor(base^n × multiplier + flatOffset)`：

| 项目 | base | multiplier | 上限 |
|------|------|-----------|------|
| 小鱼 | 1.3 | 2 | 100 |
| 鲤鱼 | 1.3 | 200 | 50 |
| 锦鲤 | 1.3 | 5000 | 30 |
| 巨口鱼 | 1.3 | 80000 | 20 |
| 自动钓手 | 1.75 | 500 | 20 |
| 收益加成 | 1.8 | 40 / 200 / 800 / 12000 | 50 |
| 收益倍率 | 1.9 | 30 / 150 / 600 / 9000 | 20 |
| 收线速度 | 1.5 | 500 / 1500 / 4000 / 60000 | 20 |

单次渔获 = `(基础值 + 加成) × 倍率`，小鱼从 1 涨到满级 255。

钓手的门槛（起步价 500、曲线 1.75、上限 20 名，并且要先自己钓上 8 条鱼才会出现）是刻意抬高的：
钓手是挂机收益的来源，太早太便宜会让整个点击循环失去意义。
「并行作业」升级后一名钓手可以同时照看多条鱼，这是后期扩大挂机产出的正路。

### 自动化不能给太早

两条自动化都是**按进度解锁**的，不是有钱就能买：

| 升级 | 解锁条件 | 价格 | 作用 |
|------|---------|------|------|
| 自动收线 | 累计钓上 40 条 | 6000 | 咬钩后自动收线，手不用点 |
| 智能浮标 | 买过自动收线 + 累计钓上 150 条 | 25 万 | 空闲自动抛竿，船边没鱼还会自己开过去（`World.cruiseToFish`） |

前 40 条必须自己一条条钓 —— 那正是游戏的点击循环所在。`EconomyTest.自动化升级不该开局就买得到` 守着这条。

### 成就奖励的量级

成就是**奖金**不是暴发户：奖励按「达成它时玩家手头大概有多少钱」定，约等于那个阶段一两件升级的钱。
`Achievements` 里有详细说明，`EconomyTest.早期成就奖励不该买得起一整队钓手` 与
`WorldSimulationTest.开局十分钟的收入买不起一整队钓手` 两条测试一起守着这条底线。

## 后期内容

数值堆到中后期容易腻，所以后期给的是**换玩法**而不是继续堆数字（见 `Content.lateGame`）：

| 内容 | 解锁条件 | 价格 | 玩法 |
|------|---------|------|------|
| 鹈鹕 | 8 名钓手 + 累计钓上 300 条 | 80 万 | 在水面上方盘旋，锁定**最值钱**的那条鱼俯冲叼走，不分稀有度（`Pelican`） |
| 拖网 | 10 名钓手 + 累计钓上 600 条 | 180 万 | 每 45 秒撒一次网，把船附近最多 4 条鱼一起捞上来（`World.updateNetSweep`） |

两者都有独立的收益来源统计（`Source.PELICAN` / `Source.NET`）与专属音效。

## 转生

`Prestige.pearlsFor` = `floor(3 × √(累计收入 / 100 万))` —— **首次转生就有 3 颗珍珠**
（正好够「鱼饵精通」点两级），之后按平方根递增。
另外每次转生都永久 +10% 全局收益（`GameState.prestigeMultiplier`），
所以转生不是「清空换点小 buff」，而是实打实的长期加速。

## 水域环境

6 张水域各有独立的天空/水体配色、云朵图集（`cloud_<id>_anim`）、水草帧动画（`seaweed_<id>_anim`），
水体与河床底纹共用素材、按每张图的染色区分。解锁新水域后画面本身就会换一套，不是只换鱼。
配色表见 `Species.kt` 的 `ENV_*`，素材与摆位常量见 `BoatArt` 与 `GameRenderer`。

## 存档

**只保存购买次数**，所有属性靠读档时重放购买重建 —— 这是从原 Godot 工程继承的核心机制。
`GameState.loadFrom()` 会按 `Content.purchasables` 的顺序重放 `applyPurchase()`，保证读档后价格曲线、收益、解锁状态与存档前完全一致。`EconomyTest.存档重放能完整重建所有属性` 专门守这条不变量。

## 项目结构

```
app/src/main/java/com/dshx/game/SU/
├── MainActivity.kt          # 入口，Compose 接线与生命周期
├── game/
│   ├── Model.kt             # 鱼种 / 购买项 / 浮动文字 / 粒子 / 存档结构
│   ├── GameState.kt         # 全局状态、购买逻辑、存档重放
│   ├── Content.kt           # 全部可购买项与数值配置
│   ├── World.kt             # 游戏世界：鱼群、浮标状态机、钓手 AI
│   ├── GameView.kt          # 游戏 View（固定步长循环 + 触摸）
│   ├── GameRenderer.kt      # Canvas 渲染
│   ├── Assets.kt            # 位图预缩放加载
│   ├── Audio.kt             # SoundPool 音效 + MediaPlayer BGM
│   ├── SaveManager.kt       # SharedPreferences 存档
│   └── Palette.kt           # 配色与大数字格式化
└── ui/                      # Compose 界面（商店 / 菜单 / HUD）
```

## 美术资源

全部用 AI 重新生成，风格对齐原参考图（扁平矢量卡通 + 粗黑描边 + 低饱和暖色）：

- `tools/process_art.py` 负责后处理：精灵抠透明边距、木质面板切九宫格、应用图标导出多密度
- 木质面板九宫格切点由脚本自动探测四角金色护角的包围盒得到，保证拉伸时护角不变形

重新生成资源后执行：

```bash
python3 tools/process_art.py
```

## 构建

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_SDK_ROOT=/path/to/android-sdk

./gradlew :app:assembleRelease   # 签名 release APK
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:testDebugUnitTest # 单元测试
```

产物：`app/build/outputs/apk/release/app-release.apk`

签名配置见 `app/build.gradle.kts` 的 `signingConfigs`，密钥库 `keystore/fishingidle.jks`。

## 测试

26 个 JVM 单元测试，覆盖两块最容易出错的地方：

- **EconomyTest**：价格公式对齐原版、购买校验、可见性条件、存档重放不变量
- **WorldSimulationTest**：把游戏世界真跑起来推进数千帧，验证不卡死、鱼群不消失、钓手产出、连锁反应、粒子回收、长时间运行不出 NaN
