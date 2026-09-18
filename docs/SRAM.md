# SRAM Controller

SRAM controller with byte-write (read-modify-write) support. Implemented in `src/main/scala/mem/SramController.scala`.

## Behavior

- **Full-word write** (`wen` and `wmask === 0b1111`): the write data is driven to the SRAM and `wen` asserted in the request cycle itself — a single-cycle write.
- **Partial write** (`wen` and `wmask !== 0b1111`): a read-modify-write. The request (address, wmask, wdata) is captured into a register and the SRAM is read in the request cycle; in the next cycle the read data is merged byte-wise with the write data according to `wmask` (each set mask byte selects `wdata`, otherwise the byte just read) and written back.
- **Read** (`reqValid && !wen`): address forwarded to the SRAM with `ren` asserted; read data returned from the SRAM.
- `respValid` is asserted one cycle after the request for reads and full-word writes, and one cycle after the read-modify-write completes for partial writes. The SRAM is expected to have synchronous read data output (1-cycle read latency).

## Pins

SramInterface (`src/main/scala/bus/Interface.scala:56`):

| Pin   | Direction (from controller) | Description |
|-------|-----------------------------|-------------|
| addr  | output                      | access address (held from the request register during read-modify-write) |
| ren   | output                      | read enable, asserted for reads and for the read phase of a partial write |
| wen   | output                      | write enable, asserted for full-word writes and the write-back phase of a partial write |
| rdata | input                       | read data |
| wdata | output                      | write data (merged with read data during read-modify-write) |
