# SimpleSoc

SimpleSoc is a small 32-bit SoC library written in [Chisel](https://www.chisel-lang.org/). It provides a lightweight on-chip bus, interconnect and MMIO crossbar, ROM/SRAM controllers, UART, GPIO, and 7-segment display support, assembled into two example SoC tops (`SimTop`, `FpgaTop`). The CPU core is a swappable black box: plug in your own processor and get a working system.

One objective of this project is to enable quick simulation and FPGA implementation for processors designed in Stage D of the [One Student One Chip (一生一芯)](https://ysyx.oscc.cc/) program. Compared with [ysyxSoC](https://github.com/OSCPU/ysyxSoC), SimpleSoc is lightweight and simple to understand, simulates quickly, and achieves fewer cycles per instruction — its simple, low-latency memory has no wait states — at the cost of a much smaller memory (an FPGA only affords an on-chip SRAM of a few tens of KiB).

## Features

- **SimpleBus**: a custom, valid-only on-chip bus with no ready/back-pressure signals, following the One Student One Chip conventions.
  - `InstrInterface`: 32-bit read-only instruction fetch port.
  - `DataInterface`: read/write data port with size, write-enable, and byte-mask signals.
- **Interconnect**: multi-master, multi-slave interconnect with fixed-priority arbitration (master 0 has the highest priority) and per-master request buffering.
- **Crossbar**: single-master MMIO address decoder with both request and response registered.
- **RomController**: pass-through read-only ROM controller, 1-cycle response.
- **SramController**: SRAM controller, full-word writes in 1 cycle, partial writes via a 2-cycle read-modify-write.
- **UART**: 8-bit UART with programmable baud rate (clock-divisor register), 1-byte TX/RX buffers, 2-stage RX synchronizer.
- **GPIO**: 32-bit GPIO with byte-write strobes, 2-stage input synchronizer, and an optional active-low 7-segment display decoder.
- **Timer**: 16-bit dual-stage timer (prescaler + counter with wrap registers) with four PWM compare channels per timer.
- **Two ready-made SoC tops**: `SimTop` for simulation (pins compatible with ysyxSoC, DPI-C/VPI simulation memory hooks) and `FpgaTop` for Gowin FPGAs (PLL, on-chip RAM IP). `FpgaTop` uses a single memory — the program is loaded into initialized on-chip RAM; no separate ROM (see [docs/presents.md](docs/presents.md)).

## Memory map

The CPU's instruction-fetch port is interconnect master 0 and its load-store port is master 1. In the example tops, address decoding uses `addr[31:28]` (this layout is an example, not a requirement — decode bits and region sizes are yours to choose):

| Region    | Base address | Target | Notes |
|-----------|--------------|--------|-------|
| MMIO      | `0x1000_0000` | Crossbar | 4 KiB windows decoded by `addr[15:12]` |
| ROM       | `0x3000_0000` | RomController | SimTop only; size limited only by the address space |
| SRAM      | `0x8000_0000` | SramController | SimTop: size limited only by the address space; FpgaTop: 64 KiB |

`FpgaTop` decodes only MMIO and SRAM: its single memory is the on-chip RAM, initialized with the program in the FPGA vendor tooling. `SimTop` additionally has a ROM region; both tops work consistently as long as the program is loaded into RAM and runs from RAM without touching the ROM (see [docs/presents.md](docs/presents.md)).

MMIO devices behind the crossbar (same in both tops):

| Device | Base address |
|--------|--------------|
| uart0  | `0x1000_0000` |
| uart1  | `0x1000_1000` |
| gpio0  | `0x1000_2000` |
| gpio1  | `0x1000_3000` |
| timer0 | `0x1000_4000` |
| timer1 | `0x1000_5000` |

## Repository layout

```
src/main/scala/
  Soc.scala            SimTop and FpgaTop example SoCs
  Port.scala           CPU black boxes and simulation/FPGA memory wrappers
  Elaborate.scala      sbt entry point; emits SystemVerilog for both tops
  bus/                 SimpleBus interfaces, Interconnect, Crossbar
  mem/                 RomController, SramController
  uart/                Uart, UartTx, UartRx, CrossBuffer
  gpio/                Gpio, SegmentDecoder
  timer/               Timer (dual-stage counters, 4 PWM channels)
src/main/sv/           Verilog helpers: SimulatedMem (DPI-C/VPI memory), UartTxCapture
build/sim/             Pre-generated SystemVerilog and filelist for SimTop
build/fpga/            Pre-generated SystemVerilog and filelist for FpgaTop
docs/                  Detailed documentation of each component
```

## Getting started

Requirements: a JDK, and sbt (the project pins sbt 2.0.0, see `project/build.properties`). Chisel 7.15 on Scala 2.13 is used.

Generate SystemVerilog for both tops:

```
sbt "runMain simplesoc.Elaborate"
```

The output lands in `build/sim/` and `build/fpga/`, each with a `filelist.f`. The lowering options are chosen for compatibility with Yosys and Icarus Verilog (no local variables, no packed arrays, no `always_comb`).

### Simulation

`SimTop` connects to simulation memory through `src/main/sv/SimulatedMem.sv`: it uses DPI-C (`dpi_c_mem_read/write`) under Verilator and VPI (`$mem_read/$mem_write`) under Icarus Verilog. Bytes sent on uart0's TX line are printed by `UartTxCapture.sv` via `$write("%c")`. The repository does not ship a simulation runner — bring your own Verilator or Icarus harness and load `build/sim/filelist.f`.

### FPGA

`FpgaTop` targets Gowin FPGAs: clocking comes from a Gowin PLL (`GowinRPll`) with a reset controller, and the memory is a Gowin on-chip RAM IP (`GowinSP`, 64 KiB, instantiated as a black box). There is no separate ROM: most FPGAs support memory initialization, and an FPGA "ROM" is usually just initialized RAM with no write port, so a dedicated ROM buys nothing. The program image is loaded into the initialized RAM. This also keeps `SimTop` consistent with `FpgaTop`: if the program is loaded into RAM and reset to run from RAM, without touching the ROM region, both tops behave the same way. (In simulation the ROM/RAM sizes are not fixed — the simulated memories are limited only by the address space.) PLL configuration and IP generation are done in the Gowin vendor tooling, outside this repository.

## Building your own SoC

SimpleSoc is a library, not a finished chip: `SimTop`/`FpgaTop` are examples to copy from. To build your own SoC, follow the pattern in `Soc.scala` — instantiate an `Interconnect`, attach your CPU's fetch/load-store ports (see `Port.scala` for the expected black-box interfaces), hang peripherals behind a `Crossbar`, and add an `Elaborate` call for your top. See [docs/soc.md](docs/soc.md) for the full guide.

## Documentation

- [docs/bus.md](docs/bus.md) — SimpleBus protocol and interface signals
- [docs/interconnect.md](docs/interconnect.md) — multi-master interconnect and arbitration
- [docs/crossbar.md](docs/crossbar.md) — MMIO crossbar
- [docs/ROM.md](docs/ROM.md) — ROM controller
- [docs/SRAM.md](docs/SRAM.md) — SRAM controller and read-modify-write behavior
- [docs/UART.md](docs/UART.md) — UART registers and baud-rate programming
- [docs/GPIO.md](docs/GPIO.md) — GPIO registers and the 7-segment decoder
- [docs/timer.md](docs/timer.md) — timer registers and PWM channels
- [docs/soc.md](docs/soc.md) — how to assemble your own SoC
- [docs/presents.md](docs/presents.md) — SimTop vs FpgaTop and porting guide

## License

[MulanPSL v2](LICENSE)
