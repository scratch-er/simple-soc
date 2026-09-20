package simplesoc.bus

import chisel3._
import chisel3.util._

object RegW {

    def apply(init: UInt, offset: UInt, bus: DataInterface): UInt = {
        val next = Wire(UInt(init.getWidth.W))
        val reg = RegW(next, init, offset, bus)
        next := reg
        reg
    }

    def apply(next: UInt, init: UInt, offset: UInt, bus: DataInterface): UInt = {
        assert(offset.isLit)
        assert(offset.getWidth >= 2)
        assert(init.getWidth <= 32)

        val wen = bus.reqValid && bus.wen
        val wordSel = if (offset.getWidth == 2) {
            true.B
        } else {
            offset(offset.getWidth-1, 2) === bus.addr(offset.getWidth-1, 2)
        }

        val numBytes = (init.getWidth + 7) / 8
        val firstByte = offset(1, 0).litValue.toInt
        val lastByte = firstByte + numBytes - 1
        assert(lastByte < 4)

        val byteSel = Seq.tabulate(4)(i => wen && wordSel && bus.wmask(i))
        val nextAfterWrite = (for (i <- lastByte to firstByte by -1) yield {
            val hi = math.min((i-firstByte)*8+7, init.getWidth-1)
            Mux(byteSel(i), bus.wdata(i*8+7, i*8), next(hi, (i-firstByte)*8))
        }).reduce(_ ## _)

        RegNext(nextAfterWrite, init)
    }
}

object ReadWordSel {
    def apply(n: Int, bus: DataInterface): Seq[Bool] = {
        assert(n > 1)
        val nBits = log2Ceil(n)
        Seq.tabulate(n)(i => RegEnable(bus.addr(nBits+1, 2)===i.U, false.B, bus.reqValid))
    }
}

object ReadWord {
    def apply(sel: Seq[Bool], words: Seq[UInt]): UInt = {
        assert(sel.length == words.length)
        Mux1H(sel.zip(words))
    }

    def apply(n: Int, bus: DataInterface, words: Seq[UInt]): UInt = {
        ReadWord(ReadWordSel(n, bus), words)
    }
}
