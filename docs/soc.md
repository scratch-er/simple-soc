# Building a custom SoC

This project is a small SoC library written in Chisel: a bus fabric, memory controllers, and peripherals, assembled into a complete SoC by two example tops, `SimTop` and `FpgaTop` (`src/main/scala/Soc.scala`). To build your own SoC you copy the assembly pattern, reuse the library modules, and swap in your own CPU, clock/reset, and memory IP.

> **Note:** This project is not parameterized — there are no config objects or board traits. "Configuration" means editing constructor arguments at the instantiation site and, when needed, replacing the black-box classes in `src/main/scala/Port.scala`. See `docs/presents.md` for details on the two tops and how to port the CPU / FPGA board pieces.

## Module library

| Module | Source | Description |
|---|---|---|
| `InstrInterface`, `DataInterface`, `RomInterface`, `SramInterface` | `src/main/scala/bus/Interface.scala` | Standard bus/memory interfaces |
| `Interconnect` | `src/main/scala/bus/Interconnect.scala` | Multi-master bus, fixed-priority arbitration |
| `Crossbar` | `src/main/scala/bus/Crossbar.scala` | Single-master address decoder |
| `RomController`, `SramController` | `src/main/scala/mem/RomController.scala`, `SramController.scala` | Bridge `DataInterface` to `RomInterface` / `SramInterface` |
| `Uart` | `src/main/scala/uart/Uart.scala` | UART (`debug` variant adds TX capture for simulation) |
| `Gpio`, `SegmentDecoder` | `src/main/scala/gpio/` | 32-bit GPIO, 7-segment decoder |
| `Timer` | `src/main/scala/timer/Timer.scala` | Dual-stage timer with 4 PWM channels |
| CPU / clock / memory black boxes | `src/main/scala/Port.scala` | Adapt your CPU, PLL, and vendor memory IP |

Each module has its own document under `docs/` (`bus.md`, `interconnect.md`, `crossbar.md`, `ROM.md`, `SRAM.md`, `UART.md`, `GPIO.md`, `timer.md`); read those for parameters, ports, and register maps.

## The assembly pattern

`src/main/scala/Soc.scala` shows the canonical structure:

1. **CPU** — instantiate your core wrapper (`SimCpuCore` / `FpgaCpuCore`). Its `ifu` and `lsu` ports are the bus masters. If the instruction-fetch side needs a `DataInterface`, use `asIfu` to adapt it (as done for `core.io.ifu`).
2. **Interconnect** — `Interconnect(numMasters, slaveAddrMap)` arbitrates the masters between coarse address regions. The first argument is the master count; the second is the coarse address map.
3. **MMIO crossbar** — hang a `Crossbar(slaveAddrMap)` off one interconnect slave to fan out to peripherals (`Uart`, `Gpio`, ...). The example tops give each peripheral a 4 KiB-aligned base, but nothing enforces this — see below.
4. **Memory controllers** — connect the other interconnect slaves to `RomController` / `SramController`, and connect those to your memory implementation (`SimulatedRom`/`SimulatedSram` for simulation; for FPGA, a single initialized RAM such as `FpgaRam` — most FPGAs implement "ROM" as initialized RAM with no write port, so a separate ROM is unnecessary, and the program simply lives in RAM).
5. **Pin wiring** — connect peripheral pins to your top-level IO.

## Designing an address map

Address decode entries are `(baseAddress.U, (hiBit, loBit))` pairs; a slave is selected when `addr(hiBit, loBit) == baseAddress >> loBit`.

- Use the interconnect decode (coarse bits, e.g. `(31, 28)`) to split the address space into a few large regions (MMIO, ROM, SRAM).
- Use the crossbar decode (fine bits, e.g. `(15, 12)`) to give each peripheral a small window inside the MMIO region. The example tops use 4 KiB per peripheral, but this project enforces no memory layout and no fixed peripheral window size — the decode bits are yours to choose.

The recommended topology is an interconnect for a few coarse regions (its low latency matters, since every instruction fetch goes through it), with a crossbar attached as one interconnect slave to fan out to small peripherals.

- Choose decode bits so that no two bases match on the decoded slice. Example: `0x1000_0000`, `0x1000_1000`, `0x1000_2000`, `0x1000_3000` with `(15, 12)` decode to slices 0, 1, 2, 3.

> **Note:** Peripheral register maps are documented per module (e.g. UART registers at offsets 0x0–0x2 in `docs/UART.md`). Software must be written against this project's peripherals — the pins of `SimTop` match ysyxSoC, but the peripherals do not.

## Writing your own top

1. Copy `Soc.scala` to a new top module (e.g. `MyTop`) and edit the instantiation list: add masters to the `Interconnect` call, add/remove crossbar slaves, or add peripherals.
2. Provide your CPU in `src/main/scala/Port.scala` (see `docs/presents.md` — FPGA path needs only an `ExtModule` stub; the simulation path additionally requires DPI-C implementations of `dpi_c_mem_read` / `dpi_c_mem_write` for the simulated memories). Note these functions transfer aligned whole 32-bit words — ignore the lower 2 address bits (index by `addr >> 2`) and handle sub-word accesses outside the DPI-C interface.
3. Add an elaboration call in `src/main/scala/Elaborate.scala`:

   ```scala
   ChiselStage.emitSystemVerilogFile(new MyTop, Array("Elaborate", "--target-dir", "build/mytop/"), firtoolOptions)
   ```

4. Chisel generates a `filelist.f` in your output directory (each elaboration target dir gets its own, listing its modules in compile order) — feed it to your simulator / synthesis flow so all generated modules are compiled.
5. Run `sbt "runMain simplesoc.Elaborate"` to generate the SystemVerilog.

## Simulation vs FPGA

Keep one top per environment, as `SimTop` / `FpgaTop` do:

- **Simulation tops** can use debug peripherals (`Uart(true)` prints TX bytes), simulated memories (DPI-C backed), and tie off unused pins.
- **FPGA tops** must wrap the whole design in `withClockAndReset` driven by your `FpgaRcu` (active-high reset out), use plain peripherals and vendor memory IP, and expose every pin the board needs. Constraint and IP files live in the FPGA vendor tooling, outside this project.
