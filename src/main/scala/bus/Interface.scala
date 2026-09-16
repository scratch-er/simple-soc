package simplesoc.bus

import chisel3._
import chisel3.util._

class InstrInterface extends Bundle {
    val reqValid  = Input(Bool())
    val addr      = Input(UInt(32.W))
    val respValid = Output(Bool())
    val rdata     = Output(UInt(32.W))
}

class DataInterface extends InstrInterface {
    val size      = Input(UInt(2.W))
    val wen       = Input(Bool())
    val wdata     = Input(UInt(32.W))
    val wmask     = Input(UInt(4.W))
}

class IfuReq extends Bundle {
    val addr = UInt(32.W)
}

class IfuResp extends Bundle {
    val rdata = UInt(32.W)
}

class LsuReq extends IfuReq {
    val size  = UInt(2.W)
    val wen   = Bool()
    val wdata = UInt(32.W)
    val wmask = UInt(4.W)
}

class LsuResp extends IfuResp { }

class RomInterface extends Bundle {
    val addr  = Input(UInt(32.W))
    val ren   = Input(Bool())
    val rdata = Output(UInt(32.W))
}

class SramInterface extends RomInterface {
    val wen   = Input(Bool())
    val wdata = Input(UInt(32.W))
}
