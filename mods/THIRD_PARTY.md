# 开源代码来源

数字格展开的 Java 实现 `src/tametsimod/Chord.java` 移植并适配了：

- 项目：[TF0119/minesweeper](https://github.com/TF0119/minesweeper)
- 作者：Takeru Fukuda
- 许可：MIT，全文保留在 `vendor/tf0119-minesweeper/LICENSE`
- 固定提交：`d94bddadddf587e05017262ba1ab6e1924a69f83`
- 源函数：`internal/game/board.go` 的 `Board.CanChord` 和 `Board.Chord`
- 源测试：`internal/game/board_test.go` 的 `TestChordSuccess`、`TestChordWrongFlag`、`TestChordHitsMine`
- [固定版本源代码](https://github.com/TF0119/minesweeper/blob/d94bddadddf587e05017262ba1ab6e1924a69f83/internal/game/board.go)
- [固定版本测试](https://github.com/TF0119/minesweeper/blob/d94bddadddf587e05017262ba1ab6e1924a69f83/internal/game/board_test.go)

`vendor` 中保存对应源文件与许可，方便核对。没有引入整个 Go 游戏、UI、自动解题或生成器，也不需要安装 Go。

适配内容：

1. 将 Go 的单元格与坐标改为 Tametsi 的 `graph.Node`，通过 `getNeighbors()` 获取真正的邻居，支持非方形棋盘。
2. 保留“已揭开的正数格、周围旗数严格相等、只展开未揭开且未插旗的邻居”的条件。
3. 调用 Tametsi 的 `GameLogic.playerClick` 完成每次揭开，复用游戏已有的零区域展开。
4. 踩雷后停止本次展开，沿用 Tametsi 的失败显示与恢复行为，不移植上游的全部雷显示规则。
5. 正常揭开、胜利和失败通过 Tametsi 自己的接口记录进度，不修改存档格式。
6. 排除 Tametsi 的隐藏数字格、绘图与菜单操作，并尊重用户的揭开按键绑定。

启动器、进程检查、备份恢复与鼠标接入层是本项目为 Tametsi 编写的适配代码。它们不是上游游戏的一部分。

v2 自动插旗在同一邻居收集逻辑上增加互补条件：已有旗数 + 未揭开未插旗数 = 原始数字。没有通过读取邻居实际雷值来决定插旗。

## ASM 运行时绘制适配

- 项目：[OW2 ASM](https://asm.ow2.io/)，固定版本 9.7.1。
- 二进制：`lib/asm-9.7.1.jar`，来自 [Maven Central](https://repo.maven.apache.org/maven2/org/ow2/asm/asm/9.7.1/)。
- 许可：[BSD-3-Clause](https://asm.ow2.io/license.html)，全文位于 `vendor/asm/LICENSE`，发布元数据保存在相同目录。
- SHA-256：`8cadd43ac5eb6d09de05faecca38b917a040bb9139c7edeb4cc81c740b713281`。
- 下载后的 SHA-1 已与 Maven Central 提供的 `.sha1` 对照。

复用 ASM 修改内存中的 `draw.Drawer.drawHoverNeighbors`，将一个邻居查询替换为过滤已揭开格的绘制快照。填充和轮廓仍由原版多边形绘制代码完成。不修改磁盘上的游戏类，也不改变图的边、数字或其他邻居查询。v3 原版 exe 加载启动 Agent，只有主开关启用时才安装字节码转换。

v3 同时验证原版 `draw` 中的棋盘绘制位置，将中央旗标叠加在原版棋盘缓存上。行列展开复用同一个 MIT 数字格展开算法，将邻居集合改为原版 `ColumnHint.ids`，不引入自动求解器。

v4 的 P 键功能复用同一 ASM 库和 Tametsi 原版高亮：转换 `Game.keyPressed/keyReleased` 的入口、`MouseHandler.mouseMoved` 与 `Drawer.draw` 中的邻居显示读取。保持原版按住 Ctrl 的状态独立，增加当前会话的显示开关。开发前检索了现有扫雷项目的键盘模式切换和邻居高亮实现；没有找到可直接用于此 Java/Swing 游戏接入的实现，未复制新的上游代码。此部分为 Tametsi 所需的输入与绘制适配层，源码随包保留。菜单旗标修复只限制绘制状态，不修改菜单原版标记或进度。

## Twemoji 中央旗标

- 项目：[Twitter Twemoji](https://github.com/twitter/twemoji)，固定版本 `v14.0.2`。
- 资源：[Triangular flag U+1F6A9](https://github.com/twitter/twemoji/blob/v14.0.2/assets/72x72/1f6a9.png)。
- 创作者：Twitter, Inc. and other contributors。
- 图形许可：[Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/)，许可全文保存在 `vendor/twemoji/LICENSE-GRAPHICS`。
- 本项目保留原始 PNG，按格子大小缩放并裁剪到原版多边形中，没有重新绘制或修改源图。
- 文件：`resources/tametsimod/flag.png`，SHA-256：`c6762360f62f326615f9b03b018e6fd1ba9958336fef54c78b1e56f2cbbaa665`。

## 原版启动接入

复用原版 exe 自带的 Launch4j 3.10 运行时参数机制，参见 [Launch4j 官方说明](https://launch4j.sourceforge.net/docs.html#Additional_jvm_options)。管理器写入 `tametsi.l4j.ini` 的专用配置块，保留其他原有参数，exe 二进制保持原样。未复制 Launch4j 的源代码或二进制到 Mod 包中。

`Tametsi Mod Manager.exe` 是本项目的 C# 图形启动程序，源码位于 `launcher-src/ManagerLauncher.cs`，直接调用游戏附带的 `javaw.exe` 打开既有管理器。使用 Windows .NET Framework 4，不内嵌 Launch4j 或额外第三方库。Launch4j 3.50 包装方式曾在隔离测试中出现原生启动崩溃，因此未用于交付；实验产物仅保留在开发目录，不随 Mod 包发布。
