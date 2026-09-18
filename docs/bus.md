# SimpleBus

SimpleBus is the on-chip bus protocol used between the CPU, the interconnect,
and the peripheral/memory controllers. It is defined in
`src/main/scala/bus/Interface.scala`. It originates from the "一生一芯"
(One Student One Chip) project; see the
[One Student One Chip SimpleBus lecture](https://ysyx.oscc.cc/docs/2607/e/7.html)
for background, waveform diagrams, and exercises.

SimpleBus is a valid-only protocol: there is **no `ready` signal and no
back-pressure**. A transfer is accepted unconditionally on every rising clock
edge where `reqValid` is high.

## Instruction Fetching Bus

### Request channel

| Signal    | Width | Direction | Description                        |
|-----------|-------|-----------|------------------------------------|
| `reqValid`| 1     | master → slave | Request strobe. High for one cycle per request. |
| `addr`    | 32    | master → slave | Byte address.                      |

### Response channel

| Signal     | Width | Direction | Description                        |
|------------|-------|-----------|------------------------------------|
| `respValid`| 1     | slave → master | Response strobe.                    |
| `rdata`    | 32    | slave → master | Read data.                          |

The instruction port (`InstrInterface`) consists of exactly these signals:
`reqValid`, `addr`, `respValid`, and `rdata`. Instruction fetches are
always full-word reads, so the IFU bus carries no write signals, no access
size, and no byte mask — it is the LSU bus with everything write-related
removed.

## Load and Store Bus

The data port (`DataInterface`) is used for load/store traffic. It extends
the same request/response channels with write support:

| Signal   | Width | Direction | Description                                   |
|----------|-------|-----------|-----------------------------------------------|
| `size`   | 2     | master → slave | Access size. `2` = 32-bit word.               |
| `wen`    | 1     | master → slave | 1 = write, 0 = read.                          |
| `wdata`  | 32    | master → slave | Write data.                                   |
| `wmask`  | 4     | master → slave | Byte-write strobes, one bit per byte of `wdata`. |

`DataInterface.asIfu` views a data port as an instruction port by tying
`size = 2`, `wen = 0`, and `wmask = 0xF`, so an instruction fetch unit can
plug into a `DataInterface` master port. This is the only relationship
between the two buses: the IFU bus is a read-only, word-only subset, while
the LSU bus additionally supports sub-word access sizes and byte-masked
writes.

> **Note:** the interconnect, the crossbar, and all peripheral/memory
> controllers in this project use the LSU bus (`DataInterface`) on every
> port. Only the CPU's instruction fetch port uses the IFU bus; it enters
> the interconnect through `asIfu`.

## Rules

- A request is accepted on every rising edge where `reqValid` is high. The
  master must not issue a new request until it has seen the response to the
  previous one (one outstanding request per master).
- A slave asserts `respValid` and `rdata` at the earliest one cycle after the
  request was accepted.
- A new request may be issued in the same cycle the previous response arrives.
  Exception: the ysyxSoC AXI bridge requires the next request at least one cycle
  *after* the response.
- A `respValid` strobe lasts exactly one cycle.
- Write mask and rdata/wdata byte layout follows the AXI convention.
- A write also completes with a `respValid` strobe (even though `rdata` is
  meaningless for a write), so the master can treat reads and writes uniformly.
- `size` follows the AXI convention: `2'b00` = 1 byte, `2'b01` = 2
  bytes, `2'b10` = 4 bytes.

## Module conventions

Masters drive a `DataInterface`; slaves receive a flipped
(`Flipped(DataInterface)`) copy. Internal request registers use the plain
bundles `LsuReq` / `LsuResp`.
