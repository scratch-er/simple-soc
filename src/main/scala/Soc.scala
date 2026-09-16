package simplesoc

import chisel3._
import chisel3.util._
import simplesoc.bus._
import simplesoc.mem.RomController
import simplesoc.mem.SramController

class Npc extends ExtModule {
    val clock = IO(Input(Clock()))
    val reset = IO(Input(Bool()))
    val io = IO(new Bundle {
        val ifu = Flipped(new InstrInterface)
        val lsu = Flipped(new DataInterface)
    })
}

class SimulatedMem extends ExtModule {
    val clock = IO(Input(Clock()))
    val io = IO(new SramInterface)
}

class SimulatedRom extends Module {
    val io = IO(new RomInterface)

    val mem = Module(new SimulatedMem)
    mem.clock := clock
    mem.io.addr := io.addr
    mem.io.ren := io.ren
    io.rdata := mem.io.rdata
    mem.io.wen := false.B
    mem.io.wdata := 0.U(32.W)
}

class SimulatedSram extends Module {
    val io = IO(new SramInterface)

    val mem = Module(new SimulatedMem)
    mem.clock := clock
    mem.io <> io
}

class SimTop extends Module {
    val core = Module(new Npc)
    val romController = Module(new RomController)
    val ramController = Module(new SramController)
    val rom = Module(new SimulatedRom)
    val ram = Module(new SimulatedSram)

    core.clock := clock
    core.reset := reset
    romController.io.bus.reqValid := core.io.ifu.reqValid
    romController.io.bus.addr := core.io.ifu.addr
    romController.io.bus.size := 0b10.U(2.W)
    romController.io.bus.wen := false.B
    romController.io.bus.wmask := 0b1111.U(4.W)
    romController.io.bus.wdata := 0.U(32.W)
    core.io.ifu.respValid := romController.io.bus.respValid
    core.io.ifu.rdata := romController.io.bus.rdata
    core.io.lsu <> ramController.io.bus
    romController.io.rom <> rom.io
    ramController.io.sram <> ram.io
}
