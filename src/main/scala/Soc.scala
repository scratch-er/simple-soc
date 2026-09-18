package simplesoc

import chisel3._
import chisel3.util._
import simplesoc.bus._
import simplesoc.mem._
import simplesoc.uart._
import simplesoc.gpio._

class SimTop extends Module {
    val externalPins = IO(new Bundle {
        val uart0 = new UartPins
        val mygpio = new Bundle {
            val out = Output(UInt(32.W))
            val in = Input(UInt(32.W))
            val seg = Output(Vec(8, UInt(8.W)))
        }
    })

    val core = Module(new SimCpuCore)
    val interConnect = Module(new Interconnect(2, Seq(
        0x10000000l.U(32.W) -> (31, 28),
        0x30000000l.U(32.W) -> (31, 28),
        0x80000000l.U(32.W) -> (31, 28),
    )))
    val mmioXbar = Module(new Crossbar(Seq(
        0x10000000l.U(32.W) -> (15, 12),
        0x10001000l.U(32.W) -> (15, 12),
        0x10002000l.U(32.W) -> (15, 12),
        0x10003000l.U(32.W) -> (15, 12),
    )))
    val romController = Module(new RomController)
    val ramController = Module(new SramController)
    val uart0 = Module(new Uart(true))
    val uart1 = Module(new Uart(true))
    val gpio0 = Module(new Gpio)
    val gpio1 = Module(new Gpio)
    val rom = Module(new SimulatedRom)
    val ram = Module(new SimulatedSram)
    val segmentDecoders = Seq.fill(8)(Module(new SegmentDecoder))

    core.io.ifu <> interConnect.io.masters(0).asIfu
    core.io.lsu <> interConnect.io.masters(1)
    interConnect.io.slaves(0) <> mmioXbar.io.master
    interConnect.io.slaves(1) <> romController.io.bus
    interConnect.io.slaves(2) <> ramController.io.bus
    mmioXbar.io.slaves(0) <> uart0.io.bus
    mmioXbar.io.slaves(1) <> uart1.io.bus
    mmioXbar.io.slaves(2) <> gpio0.io.bus
    mmioXbar.io.slaves(3) <> gpio1.io.bus

    romController.io.rom <> rom.io
    ramController.io.sram <> ram.io

    externalPins.uart0 <> uart0.io.pins
    uart1.io.pins.rx := true.B

    externalPins.mygpio.out := gpio0.io.pins.output
    gpio0.io.pins.input := externalPins.mygpio.in


    for (i <- 0 until 8) {
        segmentDecoders(i).io.x := gpio1.io.pins.output(i*4+3, i*4)
        externalPins.mygpio.seg(i) := segmentDecoders(i).io.y
    }
    gpio1.io.pins.input := 0.U(32.W)
}

class FpgaTop extends Module {
    val io = IO(new Bundle {
        val uart0 = new UartPins
        val uart1 = new UartPins
        val gpio0 = new GpioPins
        val gpio1 = new GpioPins
    })

    val rcu = Module(new FpgaRcu)

    withClockAndReset (rcu.io.clockOut, rcu.io.resetOut) {
        val core = Module(new FpgaCpuCore)
        val interConnect = Module(new Interconnect(2, Seq(
            0x10000000l.U(32.W) -> (31, 28),
            0x30000000l.U(32.W) -> (31, 28),
            0x80000000l.U(32.W) -> (31, 28),
        )))
        val mmioXbar = Module(new Crossbar(Seq(
            0x10000000l.U(32.W) -> (15, 12),
            0x10001000l.U(32.W) -> (15, 12),
            0x10002000l.U(32.W) -> (15, 12),
            0x10003000l.U(32.W) -> (15, 12),
        )))
        val romController = Module(new RomController)
        val ramController = Module(new SramController)
        val uart0 = Module(new Uart)
        val uart1 = Module(new Uart)
        val gpio0 = Module(new Gpio)
        val gpio1 = Module(new Gpio)
        val rom = Module(new FpgaRom)
        val ram = Module(new FpgaRam)

        core.io.ifu <> interConnect.io.masters(0).asIfu
        core.io.lsu <> interConnect.io.masters(1)
        interConnect.io.slaves(0) <> mmioXbar.io.master
        interConnect.io.slaves(1) <> romController.io.bus
        interConnect.io.slaves(2) <> ramController.io.bus
        mmioXbar.io.slaves(0) <> uart0.io.bus
        mmioXbar.io.slaves(1) <> uart1.io.bus
        mmioXbar.io.slaves(2) <> gpio0.io.bus
        mmioXbar.io.slaves(3) <> gpio1.io.bus

        romController.io.rom <> rom.io
        ramController.io.sram <> ram.io

        io.uart0 <> uart0.io.pins
        io.uart1 <> uart1.io.pins

        io.gpio0 <> gpio0.io.pins
        io.gpio1 <> gpio1.io.pins
    }
}
