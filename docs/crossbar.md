# Crossbar

The crossbar connects a **single** SimpleBus master (see `docs/bus.md`) to
one port per slave in `slaveAddrMap`. Because there is only one master, no
arbitration is needed: it is a pure address decoder with registered
forwarding. Implemented in `src/main/scala/bus/Crossbar.scala`.

## Parameters

| Parameter      | Type                      | Description |
|----------------|---------------------------|-------------|
| `slaveAddrMap` | `Seq[(UInt, (Int, Int))]` | One entry per slave, same format as in `docs/Interconnect.md`: `(base, (hi, lo))`. A request targets this slave when `addr(hi, lo)` equals `base >> lo`. The entries must not overlap in their decoded ranges; see `docs/Interconnect.md` for the full explanation. |

## Ports

| Port   | Type                          | Description |
|--------|-------------------------------|-------------|
| master | `DataInterface`               | Single master port. |
| `slaves` | `Vec(numSlaves, Flipped(DataInterface))` | One port per entry in `slaveAddrMap`, in order. |

## Address decoding

The master request is registered: `reqValid` is delayed one cycle
(`RegNext`) and the payload (`addr`, `size`, `wen`, `wdata`, `wmask`) is
captured in a `RegEnable` buffer. `slaveSel(s)` compares the buffered
address bits `addr(hi, lo)` against each slave's base address.

## Behavior

- Each slave's `reqValid` is the registered valid ANDed with its decode.
- The payload fields are **broadcast** from the register buffer to all
  slaves; only the selected slave's `reqValid` is high, so only it acts on
  the data.
- The master's `respValid` is the OR of all slaves' `respValid`; `rdata`
  comes from a `Mux1H` over the slaves' decode bits.
- The response is registered one cycle before reaching the master.
- The crossbar assumes **at most one slave responds** to any request. If two
  address-map entries overlap, the merged response is undefined.

## Timing

- A request is forwarded to the selected slave one cycle after it arrives,
  adding one stall cycle to the master's pipeline.
- The response reaches the master one cycle after the slave asserts it
  (slave latency + the crossbar's own register).

## Buffering and combinational paths

Unlike the interconnect, the crossbar can have many slaves, and it is
usually used for MMIO where latency is not critical. So while the
interconnect leaves the response path combinational (see
`docs/Interconnect.md`), the crossbar **registers the response** to absorb
the large `rdata` mux that a wide slave list would otherwise create.

Requests are buffered for the same reason as in the interconnect: on the
CPU side, generating the address can already be a long combinational path,
and slave-selection decode has a large fanout — terminating that path in a
register is cheap insurance regardless of how many slaves follow.

Details:

- The master sees a single register stage (`reqValid` + payload buffer);
  there is no combinational path from the master into any slave.
- Slave payload inputs are register outputs, broadcast without a mux.
- The combinational logic that remains is the response `Mux1H` (decode bits
  × `rdata`) and the `respValid` OR tree, both of which scale with
  `numSlaves` — and both are cut by the response register at the master's
  `rdata` output.
