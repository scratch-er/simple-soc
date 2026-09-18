# UART

Simple 8-bit UART with hardware RX/TX shift registers and a programmable baud-rate divisor. Implemented in `src/main/scala/uart/Uart.scala` (top level), `UartTx.scala`, and `UartRx.scala`.

## Register Map

Register offsets within the peripheral:

| Offset | Size | Register | Description |
|--------|------|----------|-------------|
| 0x0    | 1    | data     | RX data (read) / TX data (write) |
| 0x1    | 1    | status   | Status flags |
| 0x2    | 2    | divisor  | Baud-rate divisor |

Byte 0 of any word access selects the data register (`addr(1,0)` is ignored except that bit 0 selects RX on reads), so the registers are most naturally accessed as individual bytes.

## Registers

### data (0x0)

- **Read:** returns the received byte. The read is only accepted (and the buffer freed) when a byte is present; the `valid` status bit indicates this.
- **Write:** loads the low byte (`wdata[7:0]`, gated by `wmask[0]`) into the TX buffer. Writes are only accepted when the buffer is empty; the `ready` status bit indicates this.

### status (0x1)

| Bit | Name  | Description |
|-----|-------|-------------|
| 0   | ready | TX buffer can accept another byte (space available) |
| 1   | valid | RX buffer contains a byte to read |

All other bits read as 0.

### divisor (0x2, 2 bytes)

16-bit baud-rate divisor, byte-writable via `wmask[2]` (low byte) and `wmask[3]` (high byte). A free-running 16-bit counter is reloaded with the divisor each time it reaches zero; the resulting tick (`ce`) clocks the TX and RX state machines. Bit time therefore equals `divisor` core-clock periods in the default configuration — set it to `clock_freq / baud_rate - 1` or `clock_freq / baud_rate` depending on the exact desired rate.

## Behavior

- TX and RX each have a single-entry 1-byte holding buffer (`CrossBuffer`): it holds one byte at a time, and a byte can only be written into it when empty and read out when full.
- Transfers between the buffers and the TX/RX shift logic happen on the baud-rate tick (`ce`), so each side of a buffer moves at its own clock domain's pace — the CPU side continuously, the serial side one tick per bit period.
- RX input is synchronized with a 2-stage shift register; idle is detected on the falling edge of a start bit, then 8 data bits are shifted in LSB-first.
- A read request (`reqValid && !wen`) pops the RX buffer only for address offset 0.
- `respValid` is asserted one cycle after `reqValid` — every read and write completes with a single-cycle-delayed response.

## Pins

| Pin | Direction | Description |
|-----|-----------|-------------|
| tx  | output    | serial transmit line |
| rx  | input     | serial receive line |
