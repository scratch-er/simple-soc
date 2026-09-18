package simplesoc.gpio

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode._

class SegmentDecoder extends Module {
    val io = IO(new Bundle {
        val x = Input(UInt(4.W))
        val y = Output(UInt(8.W))
    })

    io.y := decoder(
        io.x,
        TruthTable(
            Seq(
                BitPat(0x1.U(4.W)) -> BitPat((~0x60&0xFF).U(8.W)),
                BitPat(0x0.U(4.W)) -> BitPat((~0xFC&0xFF).U(8.W)),
                BitPat(0x2.U(4.W)) -> BitPat((~0xDA&0xFF).U(8.W)),
                BitPat(0x3.U(4.W)) -> BitPat((~0xF2&0xFF).U(8.W)),
                BitPat(0x4.U(4.W)) -> BitPat((~0x66&0xFF).U(8.W)),
                BitPat(0x5.U(4.W)) -> BitPat((~0xB6&0xFF).U(8.W)),
                BitPat(0x6.U(4.W)) -> BitPat((~0xBE&0xFF).U(8.W)),
                BitPat(0x7.U(4.W)) -> BitPat((~0xE0&0xFF).U(8.W)),
                BitPat(0x8.U(4.W)) -> BitPat((~0xFE&0xFF).U(8.W)),
                BitPat(0x9.U(4.W)) -> BitPat((~0xF6&0xFF).U(8.W)),
                BitPat(0xA.U(4.W)) -> BitPat((~0xEE&0xFF).U(8.W)),
                BitPat(0xB.U(4.W)) -> BitPat((~0x3E&0xFF).U(8.W)),
                BitPat(0xC.U(4.W)) -> BitPat((~0x9C&0xFF).U(8.W)),
                BitPat(0xD.U(4.W)) -> BitPat((~0x7A&0xFF).U(8.W)),
                BitPat(0xE.U(4.W)) -> BitPat((~0x8E&0xFF).U(8.W)),
                BitPat(0xF.U(4.W)) -> BitPat((~0x9E&0xFF).U(8.W))
            ),
            BitPat("b????????")
        )
    )
}
