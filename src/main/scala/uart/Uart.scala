package simplesoc.uart

import chisel3._
import chisel3.util._
import simplesoc.bus.DataInterface

class UartTxCapture extends ExtModule {
    val clock = IO(Input(Clock()))
    val io = IO(new Bundle {
        val ce = Input(Bool())
        val ch = Input(UInt(8.W))
    })
}

class Uart(debug: Boolean=false) extends Module {
    val io = IO(new Bundle {
        val bus = new DataInterface
        val pins = new Bundle {
            val tx = Output(Bool())
            val rx = Input(Bool())
        }
    })

    val rx = Module(new UartRx)
    val tx = Module(new UartTx)
    private val rxBuffer = ShiftRegister(io.pins.rx, 2, true.B, true.B)
    rx.io.rx := rxBuffer
    io.pins.tx := tx.io.tx

    private val ren = WireInit(io.bus.reqValid && !io.bus.wen)
    private val wen = WireInit(io.bus.reqValid && io.bus.wen)

    private val counter = RegInit(0.U(16.W))
    private val divisor = RegInit(0.U(16.W))
    private val counterRefill = WireInit(counter === 0.U)
    counter := Mux(counterRefill, divisor, counter-1.U)

    val txXBuffer = Module(new CrossBuffer(8))
    val rxXBuffer = Module(new CrossBuffer(8))
    tx.io.dataPort <> txXBuffer.io.readPort
    tx.io.ce := counterRefill
    txXBuffer.io.readCe := counterRefill
    rx.io.dataPort <> rxXBuffer.io.writePort
    rx.io.ce := counterRefill
    rxXBuffer.io.writeCe := counterRefill

    txXBuffer.io.writePort.bits := io.bus.wdata(7, 0)
    txXBuffer.io.writePort.valid := wen && io.bus.wmask(0)
    txXBuffer.io.writeCe := true.B

    // as long as the address offset is 0, the lowest bit is selected
    rxXBuffer.io.readPort.ready := ren && io.bus.addr(1, 0)===0.U
    rxXBuffer.io.readCe := true.B

    divisor :=
        Mux(wen && io.bus.wmask(3), io.bus.wdata(31, 24), divisor(15, 8)) ##
        Mux(wen && io.bus.wmask(2), io.bus.wdata(23, 16), divisor(7, 0))

    io.bus.rdata :=
        divisor ## // byte 2, 3: divisor
        0.U(6.W) ## rxXBuffer.io.readPort.valid ## txXBuffer.io.writePort.ready ## // byte 1: status
        rxXBuffer.io.readPort.bits // byte 0: data

    io.bus.respValid := ShiftRegister(io.bus.reqValid, 1, false.B, true.B)

    if (debug) {
        val capture = Module(new UartTxCapture)
        capture.clock := clock
        capture.io.ce := tx.io.dataPort.valid && tx.io.dataPort.ready
        capture.io.ch := tx.io.dataPort.bits
    }
}
