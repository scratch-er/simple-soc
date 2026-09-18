# Interconnect

The interconnect is a multi-master, multi-slave crossbar for SimpleBus
(see `docs/bus.md`). It connects `numMasters` master ports to one port per
slave in `slaveAddrMap`, arbitrating between masters that target the same
slave. Implemented in `src/main/scala/bus/Interconnect.scala`.

## Parameters

| Parameter      | Type                          | Description |
|----------------|-------------------------------|-------------|
| `numMasters`   | `Int`                         | Number of master ports. |
| `slaveAddrMap` | `Seq[(UInt, (Int, Int))]`     | One entry per slave, see below. |

### `slaveAddrMap`

One entry per slave, in slave index order:

```scala
Seq(
  (base0, (hi0, lo0)),
  (base1, (hi1, lo1)),
  ...
)
```

Each entry is a pair of:

- **`base: UInt`** — the slave's base address (32-bit).
- **`(hi, lo)`** — the inclusive bit range of `addr` used for decoding.

A request targets slave `s` when `addr(hi, lo)` equals the upper bits of
`s`'s base address, i.e. `base >> lo`. The decode range is chosen per slave
to cover that slave's size: the number of decoded bits `hi - lo + 1` should
match the slave's addressable span, and all address bits below `lo` are
passed through to the slave untouched. For example, a slave mapped at base
`0x8000_0000` that occupies a region selectable by `addr[31:28]` would have
entry `(0x8000_0000.U, (31, 28))`; any request whose top nibble is `0x8`
routes to it, regardless of the lower 28 bits.

The entries must not overlap in their decoded ranges, because every slave
that decodes a request sees its `reqValid` asserted, and overlapping entries
would produce undefined behavior.

## Ports

| Port    | Type                         | Description |
|---------|------------------------------|-------------|
| `masters` | `Vec(numMasters, DataInterface)` | Master ports, index 0 highest priority. |
| `slaves`  | `Vec(numSlaves, Flipped(DataInterface))` | One port per entry in `slaveAddrMap`, in order. |

Adding masters is possible but not recommended beyond a few: decode,
arbitration, and the response mux grow as `masters × slaves`, and the
grant/response logic forms a long combinational path across all channels.

## Address decoding

Each request is captured into a per-master register (`reqBuffer`) when
`reqValid` fires. One cycle later, `slaveSel(m)(s)` compares the buffered
address bits `addr(hi, lo)` against each slave's base address from the map.
The decode therefore operates on the *registered* request and is valid the
cycle after the request arrives.

## Arbitration

Arbitration is **fixed priority, master 0 highest**. A channel `(m, s)` is
granted when:

- slave `s` is not busy,
- master `m` has a pending request selecting slave `s`, and
- no higher-priority master (index `< m`) is also selecting slave `s`.

Two bookkeeping bit vectors implement this:

- `pendingBits(m)` — set when a request arrives at master `m`, cleared when
  any of its channels is granted.
- `busyBits(s)` — set while any granted master targets slave `s`, cleared on
  that slave's `respValid`. Only one transaction per slave is in flight at a
  time.

## Response routing

`respMuxBits(m)(s)` opens the response channel when `(m, s)` is granted and
closes it when slave `s` asserts `respValid`. Each master's `respValid` and
`rdata` are a `Mux1H` over its open channels.

Slave-side forwarding: a slave sees `reqValid` as the OR of all
`channelSel(m)(s)`, with `addr`, `size`, `wen`, `wdata`, and `wmask` muxed
from the granted master's `reqBuffer`.

## Timing

- A request is forwarded to the selected slave **at least one cycle** after
  it arrives.
- The response channel opens **at least two cycles** after the request
  arrives.
- Each master may have one outstanding request; each slave serves one
  transaction at a time.

## Buffering and combinational paths

The interconnect is designed for a small number of masters and a small
number of slaves, where the slaves are typically a few memories and a few
crossbars for MMIO (see `docs/Crossbar.md`). Both kinds of slave buffer
their own response. The interconnect deliberately **does not buffer the
response**: its latency is critical because every instruction fetch goes
through it, and with only a few slaves there is no need for response
muxing logic deep enough to require pipelining.

Request buffering, on the other hand, is present on both the interconnect
and the crossbar for a common reason: on the CPU side, generating the
address can already be a long combinational path, and feeding it into
slave-selection decode — which has a large fanout — would extend that path
critically. Buffering the request cuts it.

Details:

- Every master port has a full request buffer (`reqValid` plus all payload
  fields registered), so masters see only a register input, and the CPU-side
  address-generation path terminates at a register.
- Slave payload inputs come from a mux over the per-master buffers, not from
  the masters directly.
- The grant chain (`channelSel`) combines decode, busy, pending, and
  higher-priority master checks; the response mux adds another `Mux1H` level
  on top. Both scale with `numMasters` and are the main timing concern when
  adding masters.
- The response outputs are selected with **registered** mux bits
  (`respMuxBits`), not combinationally from the current decode. Since the
  request is buffered, a slave's response can arrive at the earliest two
  cycles after the request arrived at the interconnect — one cycle to enter
  the buffer, one to be forwarded — which leaves exactly enough cycles to
  register the mux selection before the response must be routed back.
