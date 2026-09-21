# Timer

16-bit dual-stage timer with four PWM compare channels. Implemented in `src/main/scala/timer/Timer.scala`.

## Overview

The timer consists of a prescaler stage (`cnt0`/`wrap0`) and a counter stage (`cnt1`/`wrap1`):

- `cnt0` increments every core-clock cycle and wraps to 0 when it reaches `wrap0`, producing a tick every `wrap0 + 1` cycles.
- `cnt1` increments on each `cnt0` wrap and wraps to 0 when it reaches `wrap1`.

The counter (`cnt1`) is continuously compared against four compare registers; each comparison drives one PWM output pin.

## Register Map

Register offsets within the peripheral:

| Offset | Size | Register | Description |
|--------|------|----------|-------------|
| 0x0    | 2    | cnt0     | Prescaler counter |
| 0x2    | 2    | cnt1     | Counter |
| 0x4    | 2    | wrap0    | Prescaler wrap value |
| 0x6    | 2    | wrap1    | Counter wrap value |
| 0x8    | 2    | cmp0     | Compare value for PWM 0 |
| 0xA    | 2    | cmp1     | Compare value for PWM 1 |
| 0xC    | 2    | cmp2     | Compare value for PWM 2 |
| 0xE    | 2    | cmp3     | Compare value for PWM 3 |

## Registers

### cnt0 / cnt1 (0x0 / 0x2)

Counter stages, both reset to 0. Each stage increments once per clock (cnt1 only on a cnt0 wrap) and wraps to 0 when it equals its wrap register. Writes load the register directly, so the counters can be initialized or reset by software. Because cnt0 may wrap between reading the two halves, read the counters as a pair and retry if `cnt1` changes.

### wrap0 / wrap1 (0x4 / 0x6)

Wrap values for cnt0 and cnt1, both reset to 0xffff. A stage wraps to 0 when its counter equals the wrap value, so each stage counts `wrap + 1` distinct values (0 through wrap).

### cmp0 – cmp3 (0x8 – 0xE)

Compare values for the four PWM channels, reset to 0.

## Behavior

- All registers read back the value last written (or the live counter value for cnt0/cnt1); there are no side effects on reads.
- Each PWM output is registered: `pwmN` is `cnt1 < cmpN` delayed by one cycle.
- `respValid` is asserted one cycle after `reqValid`, so every access completes in the following cycle.

## Pins

| Pin  | Direction | Description |
|------|-----------|-------------|
| pwm0 | output    | PWM output 0, `cnt1 < cmp0` (registered) |
| pwm1 | output    | PWM output 1, `cnt1 < cmp1` (registered) |
| pwm2 | output    | PWM output 2, `cnt1 < cmp2` (registered) |
| pwm3 | output    | PWM output 3, `cnt1 < cmp3` (registered) |
