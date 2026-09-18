# SimTop 与 FpgaTop

SimTop 和 FpgaTop 是本项目的两个顶层设计，均在 `src/main/scala/Soc.scala` 中定义。它们包含相同的 SoC(CPU + 互连 + 外设 + 存储器),但面向两种不同的环境进行组装:

- **SimTop** 面向仿真(Verilator / iverilog)。其存储器为行为级仿真模型,UART 带有调试捕获模块。
- **FpgaTop** 面向真实的 FPGA 开发板。它额外包含一个时钟/复位单元,并使用 FPGA 厂商的存储器 IP 替代仿真模型。

> **注意:** `SimTop` 的外部引脚与 [ysyxSoC](https://github.com/OSCPU/ysyxSoC) 兼容,但外设**并不**兼容。不要假设 ysyxSoC 的外设模型或针对 ysyxSoC 外设编写的软件可以在此工作;只有引脚级的接口是一致的。

## SimTop

定义于 `src/main/scala/Soc.scala:10`。

| 模块 | 源码 | 作用 |
|---|---|---|
| `SimCpuCore` | `src/main/scala/Port.scala` | CPU 核(黑盒,见[移植你自己的 CPU](#移植你自己的-cpu)) |
| `Interconnect(2, ...)` | `src/main/scala/bus/Interconnect.scala` | 2 主设备互连 |
| `Crossbar(...)` | `src/main/scala/bus/Crossbar.scala` | MMIO 地址译码器 |
| `RomController` / `SramController` | `src/main/scala/mem/` | 总线到存储器的桥 |
| `Uart(true)` ×2 | `src/main/scala/uart/Uart.scala` | UART,启用调试模式 |
| `Gpio` ×2 | `src/main/scala/gpio/Gpio.scala` | 32 位 GPIO |
| `SegmentDecoder` ×8 | `src/main/scala/gpio/SegmentDecoder.scala` | 7 段数码管译码器 |
| `SimulatedRom` / `SimulatedSram` | `src/main/scala/Port.scala` | 封装 `SimulatedMem`(`src/main/sv/SimulatedMem.sv`) |

仅仿真相关的细节:

- `uart0` 和 `uart1` 以 `debug = true` 例化,其中包含一个 `UartTxCapture` 模块(`src/main/sv/UartTxCapture.sv`),它用 `$write("%c", ...)` 把每个发送的字节打印到 stdout。
- `uart1` 的 `rx` 被拉高(未使用)。
- `gpio0` 驱动 `mygpio.out` / 读取 `mygpio.in`;`gpio1` 的低 32 位由八个 `SegmentDecoder` 译码后输出到 `mygpio.seg`。
- `SimulatedMem` 通过由 `` `ifdef `` 选择的、与具体仿真器相关的钩子进行读写:在 Verilator 下使用 DPI-C 函数 `dpi_c_mem_read` / `dpi_c_mem_write`,在 Icarus Verilog(`__ICARUS__`)下使用系统任务 `$mem_read` / `$mem_write`。任何仿真流程都必须提供这些钩子(见[移植仿真环境](#移植仿真环境))。

外部引脚:

| 引脚 | 方向 | 说明 |
|---|---|---|
| `externalPins.uart0.tx` | output | UART0 发送 |
| `externalPins.uart0.rx` | input | UART0 接收 |
| `externalPins.mygpio.out` | output | GPIO0 输出,32 位 |
| `externalPins.mygpio.in` | input | GPIO0 输入,32 位 |
| `externalPins.mygpio.seg` | output | 8 × 7 段数码管显示码 |

## FpgaTop

定义于 `src/main/scala/Soc.scala:69`。

与 SimTop 的差异:

- 整个 SoC 被包裹在 `withClockAndReset(rcu.io.clockOut, rcu.io.resetOut)` 中;`FpgaRcu`(`src/main/scala/Port.scala`)基于板载时钟/复位生成系统时钟(通过 Gowin PLL)和一个**高有效**的复位。
- `FpgaCpuCore` 封装了 FPGA CPU 黑盒(`Npc`)。
- UART 不以调试模式例化(没有 `UartTxCapture`)。
- 存储器为 `FpgaRom` / `FpgaRam`,它们封装了 Gowin IP(`GowinPRom` / `GowinSP`)。
- 两个 UART 和两个 GPIO 均引出为引脚。

外部引脚:

| 引脚 | 方向 | 说明 |
|---|---|---|
| `io.uart0` / `io.uart1` | in/out | `UartPins`(tx、rx) |
| `io.gpio0` / `io.gpio1` | in/out | `GpioPins`(input、output),各 32 位 |

引脚约束和 PLL 配置位于 FPGA 厂商工具中,不在本项目范围内。

## 共享地址映射

两个顶层使用相同的存储器映射:

| 区域 | 基地址 | 译码位 | 目标 |
|---|---|---|---|
| MMIO | `0x1000_0000` | (31, 28) | Crossbar |
| ROM | `0x3000_0000` | (31, 28) | RomController |
| SRAM | `0x8000_0000` | (31, 28) | SramController |

MMIO crossbar 的从设备(本例中按第 15:12 位译码——窗口大小不由项目强制约束):

| 外设 | 基地址 |
|---|---|
| uart0 | `0x1000_0000` |
| uart1 | `0x1000_1000` |
| gpio0 | `0x1000_2000` |
| gpio1 | `0x1000_3000` |

## Elaboration(展开生成)

两个顶层由同一个入口点展开,即 `src/main/scala/Elaborate.scala` 中的 `object Elaborate`:

```
sbt "runMain simplesoc.Elaborate"
```

每次展开会写出一个自包含的目录:

- `build/sim/` —— `SimTop` 的每个模块各为一个独立的 `.sv` 文件(`SimTop.sv`、`SimCpuCore.sv`、`Interconnect.sv`、……、`SegmentDecoder.sv`),外加按编译顺序列出它们的 `filelist.f`。
- `build/fpga/` —— `FpgaTop` 同理(`FpgaTop.sv`、`FpgaCpuCore.sv`、`FpgaRcu.sv`、`FpgaRom.sv`、`FpgaRam.sv`、……),外加其自己的 `filelist.f`。

两个目录还会各得到一个 `verification/` 子目录。它同样由 Chisel 生成(例如 assert/assume/cover 层等验证附属产物),与本项目无关——流程中没有任何环节会读取它。生成的 SystemVerilog 使用了 firtool 的 lowering 选项 `disallowLocalVariables, disallowPackedArrays, locationInfoStyle=wrapInAtSquareBracket, noAlwaysComb`,选择这些选项是为了兼容 yosys 和 iverilog。`Elaborate` 目前是唯一的 `App`,因此直接 `sbt run` 也可以。

## 移植你自己的 CPU

CPU 是一个黑盒;所有移植钩子都位于 `src/main/scala/Port.scala`,并标注有 "Modify these classes to suit your design"。

CPU 通过两个标准接口接入(见 `src/main/scala/bus/Interface.scala`):

- `ifu` 上的 `InstrInterface` —— 指令取指
- `lsu` 上的 `DataInterface` ——  load/store

**FPGA 路径:** 在 `class Npc extends ExtModule` 中描述你的 CPU(时钟、复位、`io.ifu`、`io.lsu`)。`FpgaCpuCore` 已经把它封装成标准接口;请把你的 CPU 生成的 Verilog 与 `FpgaTop` 的输出放在一起。

**仿真路径:** 同样的形式,使用 `class NpcSocWrapper extends ExtModule` + `SimCpuCore`。此外,仿真存储器依赖于与具体仿真器相关的钩子(见 `src/main/sv/SimulatedMem.sv`),你的仿真测试平台必须提供它们:

- **Verilator** —— 提供 DPI-C 函数 `dpi_c_mem_read` 和 `dpi_c_mem_write`(例如一个 C++ 存储器模型)。
- **iverilog** —— `$mem_read` / `$mem_write` 系统任务必须通过 VPI 实现。

对于其他仿真器,选用该仿真器所支持的任意机制——DPI-C、VPI,或者完全绕过这些钩子,把 `SimulatedMem` 建模为普通的 Verilog 行为级存储器。这些钩子按对齐的整个 32 位字传输:地址的低 2 位不携带字节通道信息,必须忽略,因此你的存储器模型应按 `addr >> 2` 索引。字节/半字访问已由 SRAM 控制器处理。另外,CPU 发出的原始 load/store 地址被原样传递,且所有存储器区域共用这两个钩子,因此由你的 C/C++(或 VPI)代码根据地址译码当前访问的是哪个存储器区域。

## 移植到另一块 FPGA 开发板

所有与板卡相关的部分都集中在 `src/main/scala/Port.scala`:

- **时钟/复位:** 在 `FpgaRcu` 内部把 `GowinRPll` 替换为你所用厂商的 PLL(如果板载时钟已是系统时钟,也可以用一个普通的时钟缓冲)。无论板载复位极性如何,`FpgaRcu.io.resetOut` 必须是高有效;当前实现是对低有效的板载复位取反(`!reset.asBool`)。
- **存储器:** 把 `GowinPRom` / `GowinSP` 替换为厂商工具生成的 ROM/RAM IP。封装层 `FpgaRom` / `FpgaRam` 把 `RomInterface` / `SramInterface` 适配到厂商 IP;请调整地址切片(`io.addr(14, 2)`,即 32 KiB、按字寻址)以匹配你的 IP 深度。预初始化的 ROM 内容由厂商工具生成,不在本项目范围内。
- **引脚:** 在你板卡的约束文件(厂商工具)中,把顶层引脚(`io.uart*`、`io.gpio*`)以及板载时钟/复位映射到物理引脚。本仓库中不包含任何约束文件。
