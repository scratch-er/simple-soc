package simplesoc.uart

import chisel3._
import chisel3.util._

class UartTx extends Module {
    val io = IO(new Bundle {
        val dataPort = Flipped(Decoupled(UInt(8.W)))
        val ce = Input(Bool())
        val tx = Output(UInt(1.W))
    })

    private val shiftRegNext = Wire(UInt(10.W))
    private val countRegNext = Wire(UInt(10.W))

    private val shiftReg = RegEnable(shiftRegNext, 1.U(1.W) ## 0xff.U(8.W) ## 1.U(1.W), io.ce)
    private val countReg = RegEnable(countRegNext, 1.U(1.W) ## 0xff.U(8.W) ## 1.U(1.W), io.ce)

    private val ready = countReg(0)
    private val valid = io.dataPort.valid

    shiftRegNext := Mux(ready&&valid, 1.U(1.W)##io.dataPort.bits##0.U(1.W), 1.U(1.W)##shiftReg(9, 1))
    countRegNext := Mux(ready&&valid, 1.U(1.W)##0.U(9.W),                   1.U(1.W)##countReg(9, 1))

    io.dataPort.ready := ready
    io.tx := shiftReg(0)
}
