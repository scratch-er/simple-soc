package simplesoc.uart

import chisel3._
import chisel3.util._

class UartRx extends Module {
    val io = IO(new Bundle {
        val dataPort = Decoupled(UInt(8.W))
        val ce = Input(Bool())
        val rx = Input(UInt(1.W))
    })

    private val shiftRegNext = Wire(UInt(8.W))
    private val countRegNext = Wire(UInt(10.W))

    private val countRegIdleValue = 0x200.U(10.W)

    private val shiftReg = RegEnable(shiftRegNext, io.ce)
    private val countReg = RegEnable(countRegNext, countRegIdleValue, io.ce)

    private val idle = countReg(9)
    private val valid = countReg(0)
    private val ready = io.dataPort.ready

    private val startBit = idle && io.rx===0.U
    private val busy = !idle && !valid

    shiftRegNext := Mux(busy, io.rx##shiftReg(7, 1), shiftReg)
    countRegNext := MuxCase(countReg, Seq(
        (ready && valid) -> countRegIdleValue, // set to idle after read
        (startBit || busy) -> (0.U(1.W)##countReg(9, 1))
    ))

    io.dataPort.valid := valid
    io.dataPort.bits := shiftReg
}
