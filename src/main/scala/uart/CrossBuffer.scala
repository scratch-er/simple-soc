package simplesoc.uart

import chisel3._
import chisel3.util._

class CrossBuffer(xlen: Int) extends Module {
    val io = IO(new Bundle {
        val writePort = Flipped(Decoupled(UInt(xlen.W)))
        val readPort = Decoupled(UInt(xlen.W))
        val writeCe = Input(Bool())
        val readCe = Input(Bool())
    })

    private val buffer = Reg(UInt(xlen.W))
    private val full = RegInit(false.B)

    io.writePort.ready := !full && io.writeCe
    io.readPort.valid := full && io.readCe
    io.readPort.bits := buffer

    private val writeActive = !full && io.writePort.valid && io.writeCe
    private val readActive = full && io.readPort.ready && io.readCe

    buffer := Mux(writeActive, io.writePort.bits, buffer)
    full := MuxCase(full, Seq(
        writeActive -> true.B,
        readActive -> false.B,
    ))
}
