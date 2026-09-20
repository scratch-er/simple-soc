package simplesoc.gpio

import chisel3._
import chisel3.util._
import simplesoc.bus._

class GpioPins extends Bundle {
    val output = Output(UInt(32.W))
    val input = Input(UInt(32.W))
}

class Gpio extends Module {
    val io = IO(new Bundle {
        val bus = new DataInterface
        val pins = new GpioPins
    })

    private val outputBuffer = RegW(0.U(32.W), 0.U(3.W), io.bus)
    private val inputBuffer = ShiftRegister(io.pins.input, 2)

    io.pins.output := outputBuffer

    io.bus.respValid := ShiftRegister(io.bus.reqValid, 1)
    io.bus.rdata := ReadWord(2, io.bus, Seq(
        outputBuffer,
        inputBuffer
    ))
}
