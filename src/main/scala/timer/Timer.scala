package simplesoc.timer

import chisel3._
import chisel3.util._
import simplesoc.bus._

class PwmOutput extends Bundle {
    val pwm0 = Output(Bool())
    val pwm1 = Output(Bool())
    val pwm2 = Output(Bool())
    val pwm3 = Output(Bool())
}

class Timer extends Module {
    val io = IO(new Bundle {
        val bus = new DataInterface
        val pins = new PwmOutput
    })

    private val wrap0 = RegW(0xffff.U(16.W), 4.U(4.W), io.bus)
    private val wrap1 = RegW(0xffff.U(16.W), 6.U(4.W), io.bus)

    private val nextCnt0 = Wire(UInt(16.W))
    private val cnt0 = RegW(nextCnt0, 0.U(16.W), 0.U(4.W), io.bus)
    nextCnt0 := Mux(cnt0===wrap0, 0.U(16.W), cnt0 + 1.U)

    private val cnt0Wrap = RegNext(cnt0===wrap0, false.B)
    private val nextCnt1 = Wire(UInt(16.W))
    private val cnt1 = RegW(nextCnt1, 0.U(16.W), 2.U(4.W), io.bus)
    nextCnt1 := Mux(cnt0Wrap,
        Mux(cnt1===wrap1, 0.U(16.W), cnt1 + 1.U),
        cnt1
    )

    private val cmp = Seq.tabulate(4)(i => RegW(0.U(16.W), (8+2*i).U(4.W), io.bus))
    private val pwm = Seq.tabulate(4)(i => RegNext(cnt1 < cmp(i)))
    io.pins.pwm0 := pwm(0)
    io.pins.pwm1 := pwm(1)
    io.pins.pwm2 := pwm(2)
    io.pins.pwm3 := pwm(3)

    io.bus.respValid := ShiftRegister(io.bus.reqValid, 1)
    io.bus.rdata := ReadWord(4, io.bus, Seq(
        cnt1 ## cnt0,
        wrap1 ## wrap0,
        cmp(1) ## cmp(0),
        cmp(3) ## cmp(2)
    ))
}
