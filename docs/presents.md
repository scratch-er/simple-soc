# SimTop and FpgaTop

SimTop and FpgaTop are the two top-level designs of this project, both defined in `src/main/scala/Soc.scala`. They contain the same SoC (CPU + interconnect + peripherals + memories) but are assembled for two different environments:

- **SimTop** targets simulation (Verilator / iverilog). Its memories are behavioral simulation models and its UARTs include a debug capture module.
- **FpgaTop** targets a real FPGA board. It adds a clock/reset unit and uses FPGA vendor memory IP instead of the simulation models.

> **Note:** The external pins of `SimTop` are compatible with [ysyxSoC](https://github.com/OSCPU/ysyxSoC), but the peripherals are **not**. Do not assume ysyxSoC peripheral models or software written against ysyxSoC peripherals work here; only the pin-level interface matches.

## SimTop

Defined in `src/main/scala/Soc.scala:10`.

| Module | Source | Role |
|---|---|---|
| `SimCpuCore` | `src/main/scala/Port.scala` | CPU core (black box, see [Porting to your own CPU](#porting-to-your-own-cpu)) |
| `Interconnect(2, ...)` | `src/main/scala/bus/Interconnect.scala` | 2-master interconnect |
| `Crossbar(...)` | `src/main/scala/bus/Crossbar.scala` | MMIO address decoder |
| `RomController` / `SramController` | `src/main/scala/mem/` | Bus-to-memory bridges |
| `Uart(true)` ×2 | `src/main/scala/uart/Uart.scala` | UART, debug mode enabled |
| `Gpio` ×2 | `src/main/scala/gpio/Gpio.scala` | 32-bit GPIO |
| `Timer` ×2 | `src/main/scala/timer/Timer.scala` | Dual-stage timer, 4 PWM channels each |
| `SegmentDecoder` ×8 | `src/main/scala/gpio/SegmentDecoder.scala` | 7-segment decoders |
| `SimulatedRom` / `SimulatedSram` | `src/main/scala/Port.scala` | Wrap `SimulatedMem` (`src/main/sv/SimulatedMem.sv`) |

Simulation-only details:

- `uart0` and `uart1` are instantiated with `debug = true`, which includes a `UartTxCapture` module (`src/main/sv/UartTxCapture.sv`) that prints every transmitted byte to stdout with `$write("%c", ...)`.
- `uart1`'s `rx` is tied high (unused).
- `gpio0` drives `mygpio.out` / reads `mygpio.in`; `gpio1`'s low 32 bits are decoded by eight `SegmentDecoder`s onto `mygpio.seg`.
- `SimulatedMem` reads and writes through simulator-specific hooks, selected by `` `ifdef``: DPI-C functions `dpi_c_mem_read` / `dpi_c_mem_write` under Verilator, or the system tasks `$mem_read` / `$mem_write` under Icarus Verilog (`__ICARUS__`). Any simulation flow must provide these hooks (see [Porting the simulation](#porting-the-simulation)).

External pins:

| Pin | Direction | Description |
|---|---|---|
| `externalPins.uart0.tx` | output | UART0 transmit |
| `externalPins.uart0.rx` | input | UART0 receive |
| `externalPins.mygpio.out` | output | GPIO0 output, 32 bits |
| `externalPins.mygpio.in` | input | GPIO0 input, 32 bits |
| `externalPins.mygpio.seg` | output | 8 × 7-segment display codes |

## FpgaTop

Defined in `src/main/scala/Soc.scala:76`.

Differences from SimTop:

- The whole SoC is wrapped in `withClockAndReset(rcu.io.clockOut, rcu.io.resetOut)`; `FpgaRcu` (`src/main/scala/Port.scala`) generates the system clock (via a Gowin PLL) and an **active-high** reset from the board clock/reset.
- `FpgaCpuCore` wraps the FPGA CPU black box (`Npc`).
- UARTs are instantiated without debug mode (no `UartTxCapture`).
- There is no ROM region and no `RomController` — the only memory is `FpgaRam`, wrapping Gowin RAM IP (`GowinSP`, 64 KiB). The program is loaded into the initialized RAM. Most FPGAs support memory initialization, and an FPGA "ROM" is usually just initialized RAM with no write port, so a separate ROM buys nothing. This also keeps the simulation top consistent with the FPGA top: if the program is loaded into RAM and reset to run from RAM, without touching the ROM region, `SimTop` behaves exactly like `FpgaTop`.
- Both UARTs, both GPIOs, and both timers (PWM outputs) are exposed as pins.

External pins:

| Pin | Direction | Description |
|---|---|---|
| `io.uart0` / `io.uart1` | in/out | `UartPins` (tx, rx) |
| `io.gpio0` / `io.gpio1` | in/out | `GpioPins` (input, output), 32 bits each |
| `io.timer0` / `io.timer1` | output | `PwmOutput` (pwm0–pwm3), 4 channels each |

Pin constraints and PLL configuration live in the FPGA vendor tooling, outside this project.

## Address maps

`SimTop` uses:

| Region | Base address | Decode bits | Target |
|---|---|---|---|
| MMIO | `0x1000_0000` | (31, 28) | Crossbar |
| ROM | `0x3000_0000` | (31, 28) | RomController |
| SRAM | `0x8000_0000` | (31, 28) | SramController |

`FpgaTop` decodes only MMIO and SRAM (no ROM region); the program lives in the 64 KiB initialized RAM. In simulation, the ROM and RAM have no fixed size — the simulated memories are limited only by the address space. Both tops stay consistent as long as software is loaded into RAM and runs from RAM without touching the ROM.

MMIO crossbar slaves (same in both tops; decode on bits 15:12 in this example — the window size is not enforced by the project):

| Peripheral | Base address |
|---|---|
| uart0 | `0x1000_0000` |
| uart1 | `0x1000_1000` |
| gpio0 | `0x1000_2000` |
| gpio1 | `0x1000_3000` |
| timer0 | `0x1000_4000` |
| timer1 | `0x1000_5000` |

## Elaboration

Both tops are elaborated by a single entry point, `object Elaborate` in `src/main/scala/Elaborate.scala`:

```
sbt "runMain simplesoc.Elaborate"
```

Each elaboration writes a self-contained directory:

- `build/sim/` — every module of `SimTop` as a separate `.sv` file (`SimTop.sv`, `SimCpuCore.sv`, `Interconnect.sv`, ..., `SegmentDecoder.sv`), plus `filelist.f` listing them in compile order.
- `build/fpga/` — the same for `FpgaTop` (`FpgaTop.sv`, `FpgaCpuCore.sv`, `FpgaRcu.sv`, `FpgaRam.sv`, ...), plus its own `filelist.f`.

Both directories also get a `verification/` subdirectory. It too is generated by Chisel (verification collateral such as assert/assume/cover layers) and is of no concern to this project — nothing in the flow reads it. The generated SystemVerilog uses the firtool lowering options `disallowLocalVariables, disallowPackedArrays, locationInfoStyle=wrapInAtSquareBracket, noAlwaysComb`, chosen for compatibility with yosys and iverilog. `Elaborate` is currently the only `App`, so `sbt run` works too.

## Porting to your own CPU

The CPU is a black box; all porting hooks live in `src/main/scala/Port.scala` and are marked with "Modify these classes to suit your design".

A CPU plugs in through two standard interfaces (see `src/main/scala/bus/Interface.scala`):

- `InstrInterface` on `ifu` — instruction fetch
- `DataInterface` on `lsu` — load/store

**FPGA path:** describe your CPU in `class Npc extends ExtModule` (clock, reset, `io.ifu`, `io.lsu`). `FpgaCpuCore` already wraps it into the standard interface; provide the generated Verilog of your CPU alongside the output of `FpgaTop`.

**Simulation path:** same shape with `class NpcSocWrapper extends ExtModule` + `SimCpuCore`. Additionally, the simulation memories rely on simulator-specific hooks (see `src/main/sv/SimulatedMem.sv`), which your simulation harness must supply:

- **Verilator** — provide the DPI-C functions `dpi_c_mem_read` and `dpi_c_mem_write` (e.g. a C++ memory model).
- **iverilog** — the `$mem_read` / `$mem_write` system tasks must be implemented through VPI.

For other simulators, pick whichever mechanism the simulator supports — DPI-C, VPI, or bypassing the hooks entirely and modeling `SimulatedMem` as a plain Verilog behavioral memory. These hooks transfer aligned whole 32-bit words: the lower 2 bits of the address carry no byte-lane information and must be ignored, so your memory model should index by `addr >> 2`. Byte/halfword accesses are already handled by the SRAM controller. Also, the raw load/store address from the CPU is passed through, and all memory regions share these two hooks, so it is your C/C++ (or VPI) code that decodes which memory region is being accessed from the address.

## Porting to another FPGA board

Everything board-specific is concentrated in `src/main/scala/Port.scala`:

- **Clock/reset:** replace `GowinRPll` with a PLL of your vendor (or a plain clock buffer if the board clock is already the system clock) inside `FpgaRcu`. `FpgaRcu.io.resetOut` must be active high regardless of the board reset polarity; the current implementation inverts an active-low board reset (`!reset.asBool`).
- **Memories:** replace `GowinSP` with the RAM IP generated by your vendor's tools. The wrapper `FpgaRam` adapts `SramInterface` to the vendor IP; adjust the address slicing (`io.addr(15, 2)`, i.e. 64 KiB word-addressed) to match your IP's depth. The initial RAM content (program image) is generated in vendor tooling, outside this project. If you do want a read-only region, note that most FPGAs implement "ROM" as initialized RAM with no write port, so you can simply leave `wen` unused — a separate ROM primitive buys nothing.
- **Pins:** map the top-level pins (`io.uart*`, `io.gpio*`) and the board clock/reset to physical pins in your board's constraint file (vendor tooling). No constraints live in this repository.
