package simplesoc.mem

import chisel3._
import chisel3.util._
import simplesoc.bus._

class SramController extends Module {
    val io = IO(new Bundle{
        val bus  = new DataInterface
        val sram = Flipped(new SramInterface)
    })

    private val busReq = Wire(new LsuReq)
    busReq.addr  := io.bus.addr
    busReq.size  := io.bus.size
    busReq.wen   := io.bus.wen
    busReq.wmask := io.bus.wmask
    busReq.wdata := io.bus.wdata
    private val reqBuffer = RegEnable(busReq, io.bus.reqValid)
    private val req = WireInit(Mux(io.bus.reqValid, busReq, reqBuffer))

    io.sram.addr := req.addr
    io.bus.rdata := io.sram.rdata

    private val isRead = WireInit(io.bus.reqValid && !io.bus.wen)
    private val isFullWrite = WireInit(io.bus.reqValid && io.bus.wen && io.bus.wmask===0b1111.U(4.W))
    private val isPartialWrite = WireInit(io.bus.reqValid && io.bus.wen && io.bus.wmask=/=0b1111.U(4.W))
    val readAndModify = RegNext(isPartialWrite, false.B)
    private val sramWen = WireInit(isFullWrite || readAndModify)
    private val partialWriteData = WireInit(
        Mux(reqBuffer.wmask(3), reqBuffer.wdata(31, 24), io.sram.rdata(31, 24)) ##
        Mux(reqBuffer.wmask(2), reqBuffer.wdata(23, 16), io.sram.rdata(23, 16)) ##
        Mux(reqBuffer.wmask(1), reqBuffer.wdata(15, 8), io.sram.rdata(15, 8)) ##
        Mux(reqBuffer.wmask(0), reqBuffer.wdata(7, 0), io.sram.rdata(7, 0))
    )
    io.sram.ren := isRead || isPartialWrite
    io.sram.wen := sramWen
    io.sram.wdata := Mux(readAndModify, partialWriteData, io.bus.wdata)

    private val respValid = RegNext(isRead || sramWen)
    io.bus.respValid := respValid
}
