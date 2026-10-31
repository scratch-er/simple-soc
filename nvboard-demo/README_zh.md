# nvboard-demo

> **注意:** 本文档为 AI 翻译。

这是一个将 simple-soc 连接到 NVBoard 进行仿真的最小演示。

该演示在 `npc/` 目录下包含一个 rv32e_zicsr 处理器内核。因此你无需自己的处理器核心即可运行此演示。

## 运行此演示

你需要:

- 一份 NVBoard 副本,并将 `NVBOARD_HOME` 环境变量指向 NVBoard 目录,同时将 NVBoard 的 UART 分频系数设置为正确的值。
- 用于展开(elaborate)SoC 的 sbt。
- 当然是 Verilator 和 SDL2。

然后直接运行 `make` 构建此演示,并通过以下命令运行仿真:

```sh
make sim IMG=/path/to/workload/image.bin
```

其中 workload 需要是原始二进制格式。

## 构建 workload

为该演示构建 workload 最快捷的方式是使用 [Sonnet SDK](https://gitlink.org.cn/foobat/sonnet-sdk)。该 SDK 还提供了可直接构建运行的示例。

使用 Sonnet SDK 为该演示构建 workload 时,需要在运行 `make` 时指定:

- `BSP_PATH`:指向 `simplesoc` BSP 的路径。
- `TOOLCHAIN_FILE`:指向 `rv32e-clang.mk` 的路径。

另外请注意,`simplesoc` BSP 假设 SoC 运行在 50 MHz、UART 波特率为 115200,这意味着你需要将 NVBoard 的 UART 分频系数设置为 434。

## NVBoard 引脚绑定

仿真将 `SimTop` 的外部引脚绑定到 NVBoard 引脚,如下所示(见 `main.cc`):

| SoC 引脚(`externalPins_*`) | NVBoard 引脚 | 外设引脚 |
| -------------------------- | ------------ | -------------- |
| `uart0_tx`                 | `UART_TX`    | UART0 TX |
| `uart0_rx`                 | `UART_RX`    | UART0 RX |
| `mygpio_out[15:0]`         | `LD15` … `LD0` (LED) | GPIO0 `output[15:0]` |
| `mygpio_in[15:0]`          | `SW15` … `SW0` (开关) | GPIO0 `input[15:0]` |
| `mygpio_seg_0[7:0]`        | `SEG0A…SEG0G`, `DEC0P` | GPIO1 `output[3:0]` 经数码管译码器 0 |
| `mygpio_seg_1[7:0]`        | `SEG1A…SEG1G`, `DEC1P` | GPIO1 `output[7:4]` 经数码管译码器 1 |
| `mygpio_seg_2[7:0]`        | `SEG2A…SEG2G`, `DEC2P` | GPIO1 `output[11:8]` 经数码管译码器 2 |
| `mygpio_seg_3[7:0]`        | `SEG3A…SEG3G`, `DEC3P` | GPIO1 `output[15:12]` 经数码管译码器 3 |
| `mygpio_seg_4[7:0]`        | `SEG4A…SEG4G`, `DEC4P` | GPIO1 `output[19:16]` 经数码管译码器 4 |
| `mygpio_seg_5[7:0]`        | `SEG5A…SEG5G`, `DEC5P` | GPIO1 `output[23:20]` 经数码管译码器 5 |
| `mygpio_seg_6[7:0]`        | `SEG6A…SEG6G`, `DEC6P` | GPIO1 `output[27:24]` 经数码管译码器 6 |
| `mygpio_seg_7[7:0]`        | `SEG7A…SEG7G`, `DEC7P` | GPIO1 `output[31:28]` 经数码管译码器 7 |

UART1、GPIO0/GPIO1 的高位以及定时器没有外部引脚。GPIO0 以 16 位宽度接线:仅低 16 位 LED(`output[15:0]`)和开关(`input[15:0]`)被使用。每个七段数码管由 GPIO1 `output` 的 4 位半字节(nibble)驱动(`seg_N` 显示半字节 `[4N+3:4N]`)。

## 存储映射

此演示仅使用 `SimTop` 配置。
