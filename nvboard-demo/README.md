# nvboard-demo

This is a minimal demo of simulating simple-soc connected to NVBoard.

This demo contains an rv32e_zicsr processor core under `npc/`. So you can run this demo without plugging in your own core into this SoC. SystemVerilog elaborated from Chisel is deliberately chosen to make it just work while preventing plagiarism.

## Running this demo

You will need:

- A copy of NVBoard, and making `NVBOARD_HOME` environment variable pointing at the NVBoard directory, and setting the UART divisor of NVBoard to a right value.
- Sbt to elaborate the SoC.
- Verilator and SDL2, of cause.

Then just run `make` to build this demo and run simulation with:

```sh
make sim IMG=/path/to/workload/image.bin
```

where the workload needs to be in a raw binary format.

## Building a workload

The quickest way for building a workload for this demo is using [Sonnet SDK](https://gitlink.org.cn/foobat/sonnet-sdk). This SDK also provides examples that are just ready to be built and run.

When building workloads using Sonnet SDK for this demo, you need to specify these when running `make`:

- `BSP_PATH` as path to the `simplesoc` BSP.
- `TOOLCHAIN_FILE` as path to `rv32e-clang.mk`.

Also note that, the `simplesoc` BSP assumes that the SoC runs at 50 MHz and the UART baudrate is 115200, which means that you need to set the UART divisor in NVBoard to 434.


## NVBoard pin bindings

The simulation binds the `SimTop` external pins to NVBoard pins as follows
(see `main.cc`):

| SoC pin (`externalPins_*`) | NVBoard pins | Peripheral pin |
| -------------------------- | ------------ | -------------- |
| `uart0_tx`                 | `UART_TX`    | UART0 TX |
| `uart0_rx`                 | `UART_RX`    | UART0 RX |
| `mygpio_out[15:0]`         | `LD15` … `LD0` (LEDs) | GPIO0 `output[15:0]` |
| `mygpio_in[15:0]`          | `SW15` … `SW0` (switches) | GPIO0 `input[15:0]` |
| `mygpio_seg_0[7:0]`        | `SEG0A…SEG0G`, `DEC0P` | GPIO1 `output[3:0]` via segment decoder 0 |
| `mygpio_seg_1[7:0]`        | `SEG1A…SEG1G`, `DEC1P` | GPIO1 `output[7:4]` via segment decoder 1 |
| `mygpio_seg_2[7:0]`        | `SEG2A…SEG2G`, `DEC2P` | GPIO1 `output[11:8]` via segment decoder 2 |
| `mygpio_seg_3[7:0]`        | `SEG3A…SEG3G`, `DEC3P` | GPIO1 `output[15:12]` via segment decoder 3 |
| `mygpio_seg_4[7:0]`        | `SEG4A…SEG4G`, `DEC4P` | GPIO1 `output[19:16]` via segment decoder 4 |
| `mygpio_seg_5[7:0]`        | `SEG5A…SEG5G`, `DEC5P` | GPIO1 `output[23:20]` via segment decoder 5 |
| `mygpio_seg_6[7:0]`        | `SEG6A…SEG6G`, `DEC6P` | GPIO1 `output[27:24]` via segment decoder 6 |
| `mygpio_seg_7[7:0]`        | `SEG7A…SEG7G`, `DEC7P` | GPIO1 `output[31:28]` via segment decoder 7 |

UART1, GPIO0/GPIO1 upper bits and the timers have no external pins. GPIO0 is
wired 16-bit wide: only the low 16 LEDs (`output[15:0]`) and switches
(`input[15:0]`) are used. Each seven-segment display is driven from a 4-bit
nibble of GPIO1 `output` (`seg_N` displays nibble `[4N+3:4N]`).

## Memory map

This demo just uses the `SimTop` configuration.
