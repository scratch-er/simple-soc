package simplesoc.bus

import chisel3._
import chisel3.util._

class Crossbar(slaveAddrMap: Seq[(UInt, (Int, Int))]) extends Module {
    val numSlaves = slaveAddrMap.length

    val io = IO(new Bundle {
        val master = new DataInterface
        val slaves = Vec(numSlaves, Flipped(new DataInterface))
    })

    val reqValid = RegNext(io.master.reqValid, false.B)
    val reqBuffer = RegEnable({
        val req = Wire(new LsuReq)
        req.addr := io.master.addr
        req.size := io.master.size
        req.wen := io.master.wen
        req.wdata := io.master.wdata
        req.wmask := io.master.wmask
        req
    }, io.master.reqValid)

    val slaveSel = Seq.tabulate(numSlaves)(s => {
        val baseAddr = slaveAddrMap(s)._1
        val hi = slaveAddrMap(s)._2._1
        val lo = slaveAddrMap(s)._2._2
        reqBuffer.addr(hi, lo) === baseAddr(hi, lo)
    })

    val hasResp = WireInit((for (s <- 0 until numSlaves) yield {io.slaves(s).respValid}).reduce(_ || _))

    val respValid = RegNext(hasResp, false.B)
    val respBuffer = RegEnable(
        Mux1H(for (s <- 0 until numSlaves) yield {io.slaves(s).respValid -> io.slaves(s).rdata}),
        hasResp
    )

    for (s <- 0 until numSlaves) {
        io.slaves(s).reqValid := reqValid && slaveSel(s)
        io.slaves(s).addr := reqBuffer.addr
        io.slaves(s).size := reqBuffer.size
        io.slaves(s).wen := reqBuffer.wen
        io.slaves(s).wdata := reqBuffer.wdata
        io.slaves(s).wmask := reqBuffer.wmask
    }

    io.master.respValid := respValid
    io.master.rdata := respBuffer
}
