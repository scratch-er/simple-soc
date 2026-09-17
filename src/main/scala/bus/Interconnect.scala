package simplesoc.bus

import chisel3._
import chisel3.util._

/*
 * This inter connect assumes these timing request:
 *
 * - As long as `valid` is high on a rising edge, request or response is delivered, no back pressure.
 * - The response arrives on the next cycle of the request at earliest.
 * - The next request can be made the same cycle when current response arrives.
 *
 * Do note that in ysyxSoC, the AXI bridge assumes that the next request can be made only at
 * THE NEXT CYCLE AFTER current response arrives.
 */

class Interconnect(numMasters: Int, slaveAddrHi: Seq[(Int, Int)]) extends Module {
    val numSlaves = slaveAddrHi.length

    val io = IO(new Bundle {
        val masters = Vec(numMasters, new DataInterface)
        val slaves = Vec(numSlaves, Flipped(new DataInterface))
    })

    private val pendingBits = Seq.fill(numMasters)(RegInit(false.B))
    private val busyBits = Seq.fill(numSlaves)(RegInit(false.B))
    private val respMuxBits = Seq.fill(numMasters, numSlaves)(RegInit(false.B))

    private val reqBuffer = Seq.tabulate(numMasters)(i => {
        val req = Wire(new LsuReq)
        req.addr := io.masters(i).addr
        req.size := io.masters(i).size
        req.wen := io.masters(i).wen
        req.wdata := io.masters(i).wdata
        req.wmask := io.masters(i).wmask
        RegEnable(req, io.masters(i).reqValid)
    })

    // This is valid the next cycle a request is made
    private val slaveSel = Seq.tabulate(numMasters, numSlaves){case (m, s) => {
        val addrHiVal = slaveAddrHi(s)._1.U
        val addrHiLen = slaveAddrHi(s)._2
        reqBuffer(m).addr(31, 32-addrHiLen) === addrHiVal
    }}

    // This is combinationally dependent from `slaveSel`,
    // which is available in the next cycle after a request arrives at eariest
    val channelSel = Seq.tabulate(numMasters, numSlaves){case (m, s) => {
        // If the channel is not open, open it for a master when:
        // - This slave is not busy
        // - This master is pending and this master selects this slave
        // - No other masters with higher priority is selecting this slave
        !busyBits(s) && MuxCase(false.B,
            for (i <- 0 to m) yield {
                (pendingBits(i) && slaveSel(i)(s)) -> (if (i==m) true.B else false.B)
            }
        )
    }}

    for (m <- 0 until numMasters) {
        // If a request arrives, the master becomes pending
        // otherwise, it remains pending until the channel is granted
        val granted = (0 until numSlaves).map(s => channelSel(m)(s)).reduce(_ || _)
        pendingBits(m) := io.masters(m).reqValid || pendingBits(m) && !granted
    }

    for (i <- 0 until numSlaves) {
        busyBits(i) := Mux(busyBits(i),
            // If it is busy, it will be come not busy when a response arrives
            !io.slaves(i).respValid,
            // If it is not busy, it will become busy when any of the masters selects it
            (for (j <- 0 until numMasters) yield pendingBits(j) && slaveSel(j)(i)).reduce(_ || _)
        )
    }

    // Selects which slave's reponse channel is open for which master
    // Response forwarding channel opens the 2nd cycle after a request arrives at eariest
    for (m <- 0 until numMasters) {
        for (s <- 0 until numSlaves) {
            respMuxBits(m)(s) := Mux(respMuxBits(m)(s),
                // If the channel is already open, close it after the response arrives
                !io.slaves(s).respValid,
                // If the channel is closed, open it when a master selects this slave
                channelSel(m)(s)
            )
        }
        io.masters(m).respValid := Mux1H(for (s <- 0 until numSlaves) yield {
            respMuxBits(m)(s) -> io.slaves(s).respValid
        })
        io.masters(m).rdata := Mux1H(for (s <- 0 until numSlaves) yield {
            respMuxBits(m)(s) -> io.slaves(s).rdata
        })
    }

    // Forwards a request from a master to a slave
    // This happnes one cycle after request at eariest
    for (s <- 0 until numSlaves) {
        // The slave has a request if any master is selecting it
        io.slaves(s).reqValid := (for (m <- 0 until numMasters) yield channelSel(m)(s)).reduce(_ || _)
        val req = WireInit(Mux1H(for (m <- 0 until numMasters) yield {channelSel(m)(s) -> reqBuffer(m)}))
        io.slaves(s).addr := req.addr
        io.slaves(s).size := req.size
        io.slaves(s).wen := req.wen
        io.slaves(s).wdata := req.wdata
        io.slaves(s).wmask := req.wmask
    }
}
