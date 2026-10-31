# SimpleSoc

> **注意:** 本 README 及各 `docs/*_zh.md` 文档均为 AI 翻译。

SimpleSoc 是一个用 [Chisel](https://www.chisel-lang.org/) 编写的小型 32 位 SoC 库。它提供了一条轻量级片上总线、互连(interconnect)与 MMIO 交叉开关(crossbar)、ROM/SRAM 控制器、UART、GPIO 以及七段数码管显示支持,并组装成两个示例 SoC 顶层(`SimTop`、`FpgaTop`)。CPU 内核是可替换的黑盒:接入你自己的处理器,即可得到一个可工作的系统。

本项目的一个目标是为[一生一芯](https://ysyx.oscc.cc/)计划 Stage D 中设计的处理器提供快速的仿真与 FPGA 实现。与 [ysyxSoC](https://github.com/OSCPU/ysyxSoC) 相比,SimpleSoc 轻量、易于理解、仿真速度快,并且每条指令的周期数更少——其简单、低延迟的存储器没有等待周期——代价是存储容量小得多(FPGA 上只能放得下几十 KiB 的片上 SRAM)。

## 特性

- **SimpleBus**:自定义的仅有效(valid-only)片上总线,没有 ready/反压信号,遵循一生一芯的约定。
  - `InstrInterface`:32 位只读取指端口。
  - `DataInterface`:读/写数据端口,带有 size、写使能和字节掩码信号。
- **Interconnect**:多主设备、多从设备互连,采用固定优先级仲裁(主设备 0 优先级最高),并为每个主设备缓冲请求。
- **Crossbar**:单主设备 MMIO 地址译码器,请求和响应均寄存一拍。
- **RomController**:直通式只读 ROM 控制器,响应延迟 1 拍。
- **SramController**:SRAM 控制器,整字写入 1 拍完成,部分写通过 2 拍的读-改-写完成。
- **UART**:8 位 UART,波特率可编程(时钟分频寄存器),TX/RX 各 1 字节缓冲,2 级 RX 同步器。
- **GPIO**:32 位 GPIO,带字节写选通、2 级输入同步器,以及可选的低有效七段数码管译码器。
- **Timer**:16 位双级定时器(预分频级 + 计数级,各带 wrap 寄存器),每个定时器有 4 路 PWM 比较通道。
- **两个现成的 SoC 顶层**:`SimTop` 用于仿真(引脚与 ysyxSoC 兼容,带 DPI-C/VPI 仿真存储器钩子);`FpgaTop` 面向 Gowin FPGA(PLL、片上 RAM IP)。`FpgaTop` 只使用单一的存储器——程序加载到可初始化的片上 RAM 中,不单独设 ROM(见 [docs/presents_zh.md](docs/presents_zh.md))。

## 快速上手

需要 sbt 来展开(elaborate)该 SoC。运行以下命令为两个顶层模块配置生成 SystemVerilog:

```
sbt "runMain simplesoc.Elaborate"
```

输出位于 `build/sim/` 和 `build/fpga/`,各自带一个 `filelist.f`。lowering 选项针对 Yosys 和 Icarus Verilog 的兼容性选取(无局部变量、无压缩数组、无 `always_comb`)。

在 [nvboard-demo/](nvboard-demo/) 目录下有一个最小演示,内含 RISC-V 内核并连接 NVBoard。为该演示构建 workload 最快捷的方式是使用 [Sonnet SDK](https://gitlink.org.cn/foobat/sonnet-sdk)。可以使用 `simplesoc` BSP 和 `simplesoc-sram` BSP 分别对应本 SoC 的两种配置。该 SDK 还提供了可直接构建运行的示例。

## 存储映射

CPU 的取指端口是互连的主设备 0, load-store 端口是主设备 1。在示例顶层中,地址译码使用 `addr[31:28]`(此布局只是示例,并非要求——译码位和区域大小由你自己选择):

| 区域   | 基地址        | 目标            | 说明 |
|--------|---------------|-----------------|------|
| MMIO   | `0x1000_0000` | Crossbar        | 4 KiB 窗口,由 `addr[15:12]` 译码 |
| ROM    | `0x3000_0000` | RomController   | 仅 SimTop;大小仅受地址空间限制 |
| SRAM   | `0x8000_0000` | SramController  | SimTop:大小仅受地址空间限制;FpgaTop:64 KiB |

`FpgaTop` 只译码 MMIO 和 SRAM:其唯一的存储器是片上 RAM,程序在 FPGA 厂商工具中初始化为 RAM 的内容。`SimTop` 额外有一个 ROM 区域;只要程序加载到 RAM 中并从 RAM 运行、不访问 ROM,两个顶层的行为就是一致的(见 [docs/presents_zh.md](docs/presents_zh.md))。

Crossbar 后的 MMIO 设备(两个顶层相同):

| 设备   | 基地址        |
|--------|---------------|
| uart0  | `0x1000_0000` |
| uart1  | `0x1000_1000` |
| gpio0  | `0x1000_2000` |
| gpio1  | `0x1000_3000` |
| timer0 | `0x1000_4000` |
| timer1 | `0x1000_5000` |

## 仓库结构

```
src/main/scala/
  Soc.scala            SimTop 与 FpgaTop 示例 SoC
  Port.scala           CPU 黑盒及仿真/FPGA 存储器封装
  Elaborate.scala      sbt 入口;为两个顶层生成 SystemVerilog
  bus/                 SimpleBus 接口、Interconnect、Crossbar
  mem/                 RomController、SramController
  uart/                Uart、UartTx、UartRx、CrossBuffer
  gpio/                Gpio、SegmentDecoder
  timer/               Timer(双级计数器,4 路 PWM 通道)
src/main/sv/           Verilog 辅助模块:SimulatedMem(DPI-C/VPI 存储器)、UartTxCapture
build/sim/             预生成的 SimTop SystemVerilog 及 filelist
build/fpga/            预生成的 FpgaTop SystemVerilog 及 filelist
docs/                  各组件的详细文档
```

### 仿真

`SimTop` 通过 `src/main/sv/SimulatedMem.sv` 连接仿真存储器:在 Verilator 下使用 DPI-C(`dpi_c_mem_read/write`),在 Icarus Verilog 下使用 VPI(`$mem_read/$mem_write`)。uart0 TX 线上发送的字节由 `UartTxCapture.sv` 通过 `$write("%c")` 打印。本仓库不提供仿真运行脚本——请自带 Verilator 或 Icarus 测试环境,并加载 `build/sim/filelist.f`。

### FPGA

`FpgaTop` 面向 Gowin FPGA:时钟来自 Gowin PLL(`GowinRPll`)及复位控制器;存储器为 Gowin 片上 RAM IP(`GowinSP`,64 KiB,以黑盒方式实例化)。这里没有单独的 ROM:绝大多数 FPGA 都支持存储器初始化,而 FPGA 中的 “ROM” 通常只是不带写端口的初始化 RAM,因此单独的 ROM 没有任何好处。程序镜像直接加载到初始化后的 RAM 中。这也保证了 `SimTop` 与 `FpgaTop` 的一致性:只要程序加载到 RAM、复位后从 RAM 运行、不访问 ROM 区域,两个顶层的工作方式就是一致的。(仿真中的 ROM/RAM 大小并不固定——仿真存储器只受地址空间限制。)PLL 配置与 IP 生成在 Gowin 厂商工具中完成,不属于本仓库的内容。

## 构建你自己的 SoC

SimpleSoc 是一个库,而不是一颗成品芯片:`SimTop`/`FpgaTop` 是供你参考复制的示例。要构建自己的 SoC,请遵循 `Soc.scala` 中的模式——实例化一个 `Interconnect`,接上你的 CPU 的取指/ load-store 端口(期望的黑盒接口见 `Port.scala`),把外设挂在 `Crossbar` 之后,并为你的顶层添加一次 `Elaborate` 调用。完整指南见 [docs/soc_zh.md](docs/soc_zh.md)。

## 文档

- [docs/bus_zh.md](docs/bus_zh.md) — SimpleBus 协议与接口信号
- [docs/interconnect_zh.md](docs/interconnect_zh.md) — 多主设备互连与仲裁
- [docs/crossbar_zh.md](docs/crossbar_zh.md) — MMIO 交叉开关
- [docs/ROM_zh.md](docs/ROM_zh.md) — ROM 控制器
- [docs/SRAM_zh.md](docs/SRAM_zh.md) — SRAM 控制器与读-改-写行为
- [docs/UART_zh.md](docs/UART_zh.md) — UART 寄存器与波特率编程
- [docs/GPIO_zh.md](docs/GPIO_zh.md) — GPIO 寄存器与七段数码管译码器
- [docs/timer_zh.md](docs/timer_zh.md) — 定时器寄存器与 PWM 通道
- [docs/soc_zh.md](docs/soc_zh.md) — 如何组装自己的 SoC
- [docs/presents_zh.md](docs/presents_zh.md) — SimTop 与 FpgaTop 对比及移植指南

## 许可证

[MulanPSL v2](LICENSE)
