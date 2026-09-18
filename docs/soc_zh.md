# 构建自定义 SoC

本项目是一个用 Chisel 编写的小型 SoC 库:包含总线互连、存储控制器和外设,并由两个示例顶层模块 `SimTop` 和 `FpgaTop` 组装成完整的 SoC(`src/main/scala/Soc.scala`)。要构建你自己的 SoC,只需复制这种组装模式,复用库模块,并替换成你自己的 CPU、时钟/复位以及存储 IP。

> **注意:** 本项目没有做参数化——没有配置对象或开发板 trait。“配置”指的是在实例化处修改构造参数,必要时替换 `src/main/scala/Port.scala` 中的黑盒类。两个顶层模块的详细介绍以及如何移植 CPU / FPGA 开发板部分,请参见 [presents](presents_zh.md)。

## 模块库

| 模块 | 源码 | 说明 |
|---|---|---|
| `InstrInterface`、`DataInterface`、`RomInterface`、`SramInterface` | `src/main/scala/bus/Interface.scala` | 标准总线/存储接口 |
| `Interconnect` | `src/main/scala/bus/Interconnect.scala` | 多主设备总线,固定优先级仲裁 |
| `Crossbar` | `src/main/scala/bus/Crossbar.scala` | 单主设备地址译码器 |
| `RomController`、`SramController` | `src/main/scala/mem/RomController.scala`、`SramController.scala` | 将 `DataInterface` 桥接到 `RomInterface` / `SramInterface` |
| `Uart` | `src/main/scala/uart/Uart.scala` | UART(`debug` 变体在仿真中增加 TX 捕获) |
| `Gpio`、`SegmentDecoder` | `src/main/scala/gpio/` | 32 位 GPIO、数码管译码器 |
| CPU / 时钟 / 存储黑盒 | `src/main/scala/Port.scala` | 适配你的 CPU、PLL 和厂商存储 IP |

每个模块在 `docs/` 下都有各自的文档([bus](bus_zh.md)、[interconnect](interconnect_zh.md)、[crossbar](crossbar_zh.md)、[ROM](ROM_zh.md)、[SRAM](SRAM_zh.md)、[UART](UART_zh.md)、[GPIO](GPIO_zh.md));参数、端口和寄存器映射请阅读这些文档。

## 组装模式

`src/main/scala/Soc.scala` 展示了标准的结构:

1. **CPU** —— 实例化你的核封装(`SimCpuCore` / `FpgaCpuCore`)。其 `ifu` 和 `lsu` 端口就是总线主设备。如果取指侧需要 `DataInterface`,用 `asIfu` 进行适配(如同对 `core.io.ifu` 所做的那样)。
2. **Interconnect** —— `Interconnect(numMasters, slaveAddrMap)` 在若干粗粒度地址区域之间对主设备进行仲裁。第一个参数是主设备数量;第二个是粗粒度地址映射。
3. **MMIO crossbar** —— 将 `Crossbar(slaveAddrMap)` 挂在 interconnect 的一个从设备上,再扇出到外设(`Uart`、`Gpio` ……)。示例顶层给每个外设分配 4 KiB 对齐的基地址,但没有任何机制强制这样做——见下文。
4. **存储控制器** —— 将 interconnect 的其他从设备连接到 `RomController` / `SramController`,再把它们接到你的存储实现(仿真用 `SimulatedRom`/`SimulatedSram`,FPGA 用 `FpgaRom`/`FpgaRam`)。
5. **引脚连线** —— 将外设引脚连接到顶层 IO。

## 设计地址映射

地址译码条目是 `(baseAddress.U, (hiBit, loBit))` 形式的二元组;当 `addr(hiBit, loBit) == baseAddress >> loBit` 时选中该从设备。

- 用 interconnect 译码(粗粒度位,例如 `(31, 28)`)把地址空间划分为少数几个大区域(MMIO、ROM、SRAM)。
- 用 crossbar 译码(细粒度位,例如 `(15, 12)`)在 MMIO 区域内给每个外设一个小窗口。示例顶层每个外设使用 4 KiB,但本项目不强制任何内存布局,也不固定外设窗口大小——译码位由你自己选择。

推荐的拓扑是:用一个 interconnect 划分少数几个粗粒度区域(它的低延迟很关键,因为每条取指都要经过它),再把 crossbar 作为 interconnect 的一个从设备挂上去,扇出到小型外设。

- 选择译码位时要保证任意两个基地址在译码切片上不冲突。例如:`0x1000_0000`、`0x1000_1000`、`0x1000_2000`、`0x1000_3000` 用 `(15, 12)` 译码得到切片 0、1、2、3。

> **注意:** 各外设的寄存器映射按模块分别文档化(例如 UART 寄存器位于 `docs/UART_zh.md` 中 0x0–0x2 的偏移处)。软件必须针对本项目的外设来编写——`SimTop` 的引脚与 ysyxSoC 一致,但外设并不一致。

## 编写自己的顶层

1. 将 `Soc.scala` 复制为一个新的顶层模块(例如 `MyTop`),并编辑实例化列表:在 `Interconnect` 调用中增加主设备、增删 crossbar 从设备,或添加外设。
2. 在 `src/main/scala/Port.scala` 中提供你的 CPU(参见 [presents](presents_zh.md)——FPGA 路径只需要一个 `ExtModule` 桩;仿真路径额外需要为模拟存储实现 `dpi_c_mem_read` / `dpi_c_mem_write` 的 DPI-C 函数)。注意这些函数传输对齐的完整 32 位字——忽略地址低 2 位(按 `addr >> 2` 索引),子字访问请在 DPI-C 接口之外处理。
3. 在 `src/main/scala/Elaborate.scala` 中添加一个 elaborate 调用:

   ```scala
   ChiselStage.emitSystemVerilogFile(new MyTop, Array("Elaborate", "--target-dir", "build/mytop/"), firtoolOptions)
   ```

4. Chisel 会在输出目录中生成 `filelist.f`(每个 elaborate 目标目录各有一份,按编译顺序列出其模块)——把它交给你的仿真器 / 综合流程,以便编译所有生成的模块。
5. 运行 `sbt "runMain simplesoc.Elaborate"` 生成 SystemVerilog。

## 仿真与 FPGA

像 `SimTop` / `FpgaTop` 那样,每个环境各保留一个顶层:

- **仿真顶层** 可以使用调试外设(`Uart(true)` 会打印 TX 字节)、DPI-C 支持的模拟存储,并将未用的引脚绑死。
- **FPGA 顶层** 必须用 `withClockAndReset` 包裹整个设计,并由你的 `FpgaRcu` 驱动(高有效复位输出),使用常规外设和厂商存储 IP,并引出开发板需要的每一个引脚。约束文件和 IP 文件放在 FPGA 厂商工具中,不在本项目内。
