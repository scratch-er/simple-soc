package simplesoc.gpio

import chisel3._
import chisel3.util._
import simplesoc.bus.DataInterface

class Gpio extends Module {
    val io = IO(new Bundle {
        val bus = new DataInterface
        val pins = new Bundle {
            val output = Output(UInt(32.W))
            val input = Input(UInt(32.W))
        }
    })

    private val wen = WireInit(io.bus.reqValid && io.bus.wen)
    private val outputSel = WireInit(io.bus.addr(2) === 0.U)
    private val inputSel = WireInit(io.bus.addr(2) === 1.U)

    private val outputBuffer = RegInit(0.U(32.W))
    private val inputBuffer = ShiftRegister(io.pins.input, 2)

    outputBuffer :=
        Mux(wen && outputSel && io.bus.wmask(3), io.bus.wdata(31, 24), outputBuffer(31, 24)) ##
        Mux(wen && outputSel && io.bus.wmask(2), io.bus.wdata(23, 16), outputBuffer(23, 16)) ##
        Mux(wen && outputSel && io.bus.wmask(1), io.bus.wdata(15, 8), outputBuffer(15, 8)) ##
        Mux(wen && outputSel && io.bus.wmask(0), io.bus.wdata(7, 0), outputBuffer(7, 0))

    io.pins.output := outputBuffer

    io.bus.rdata := Mux1H(Seq(
        outputSel -> outputBuffer,
        inputSel -> inputBuffer
    ))

    io.bus.respValid := ShiftRegister(io.bus.reqValid, 1)
}
