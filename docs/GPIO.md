# GPIO

32-bit general-purpose parallel I/O peripheral. Implemented in `src/main/scala/gpio/Gpio.scala`.

## Register Map

Register offsets within the peripheral (bit 2 of the address selects the register, other address bits are ignored):

| Offset | Size | Register | Description |
|--------|------|----------|-------------|
| 0x0    | 4    | output   | Output data register |
| 0x4    | 4    | input    | Input data register (read-only) |

## Registers

### output (0x0)

32-bit output latch, reset to 0. Byte-wise writable via the write strobe: each `wmask[n]` bit updates `wdata[8n+7:8n]`. Drives the `pins.output` pins continuously.

### input (0x4)

32-bit snapshot of the `pins.input` pins, synchronized through a 2-stage shift register. Writes have no effect.

## Behavior

- A write is only committed when `addr[2] === 0` (output selected); the input register cannot be written.
- Reads return the output register when `addr[2] === 0`, otherwise the input register.
- `respValid` is asserted one cycle after `reqValid`, so every access completes in the following cycle.
