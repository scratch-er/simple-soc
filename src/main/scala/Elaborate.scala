package simplesoc

import chisel3._
import circt.stage.ChiselStage

object Elaborate extends App {
    // additional firrtl options
    // see https://github.com/OSCPU/chisel-playground
    // and https://github.com/chipsalliance/chisel/blob/main/src/test/scala-2/circtTests/stage/ChiselStageSpec.scala
    private val firtoolOptions = Array(
            "--lowering-options=" + List(
            // make yosys happy
            // see https://github.com/llvm/circt/blob/main/docs/VerilogGeneration.md
            "disallowLocalVariables",
            "disallowPackedArrays",
            "locationInfoStyle=wrapInAtSquareBracket",
            // make iverilog happy
            "noAlwaysComb"
        ).reduce(_ + "," + _)
    )

    ChiselStage.emitSystemVerilogFile(new SimTop, Array("Elaborate", "--target-dir", "build/sim/"), firtoolOptions)
    ChiselStage.emitSystemVerilogFile(new FpgaTop, Array("Elaborate", "--target-dir", "build/fpga"), firtoolOptions)
}
