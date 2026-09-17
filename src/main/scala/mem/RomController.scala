package simplesoc.mem

import chisel3._
import chisel3.util._
import simplesoc.bus._

class RomController extends Module {
    val io = IO(new Bundle{
        val bus = new DataInterface
        val rom = Flipped(new RomInterface)
    })

    io.rom.addr := io.bus.addr
    io.rom.ren := io.bus.reqValid
    io.bus.respValid := ShiftRegister(io.bus.reqValid, 1)
    io.bus.rdata := io.rom.rdata
}
