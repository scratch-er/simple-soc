# ROM Controller

Read-only memory controller that passes bus reads directly through to a ROM device. Implemented in `src/main/scala/mem/RomController.scala`.

## Behavior

- Any bus request (`reqValid`) is forwarded to the ROM as a read (`ren`), with the full bus address.
- `rdata` comes straight from the ROM's read data output.
- `respValid` is asserted one cycle after `reqValid`; the ROM is expected to produce read data in that following cycle (synchronous read).

## Pins

RomInterface (`src/main/scala/bus/Interface.scala:50`):

| Pin   | Direction (from controller) | Description |
|-------|-----------------------------|-------------|
| addr  | output                      | read address |
| ren   | output                      | read enable, asserted on any bus request |
| rdata | input                       | read data |

## Notes

- Writes are not supported; the controller ignores `wen` and the write strobes entirely.
