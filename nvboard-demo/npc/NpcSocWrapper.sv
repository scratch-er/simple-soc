module NpcSocWrapper(
  input         clock,
  input         reset,
  output        io_ifu_reqValid,
  output [31:0] io_ifu_addr,
  input         io_ifu_respValid,
  input  [31:0] io_ifu_rdata,
  output        io_lsu_reqValid,
  output [31:0] io_lsu_addr,
  output [1:0]  io_lsu_size,
  output        io_lsu_wen,
  output [31:0] io_lsu_wdata,
  output [3:0]  io_lsu_wmask,
  input         io_lsu_respValid,
  input  [31:0] io_lsu_rdata
);

Npc npc (.*);

/* This wrapper file contains code for debugging.
 * Using seperate wrapper and helper modules for this makes debugging non-invasive.
 * For a minimal example, it only contains code to end simulation on an ebreak instruction.
 * But you can add more things like tracing and difftest support.
 */

always @(posedge(clock)) begin
  // end simulation on an ebreak instruction
  // this catches an M mode breakpoint exception being commited
  if (
    npc.lateExecStage_io_commit_valid_probe &&
    npc.lateExecStage_io_commit_isTrap_probe &&
    npc.lateExecStage_io_commit_trapCause_probe == 3
  ) begin
    $display("exiting simulation on ebreak");
    $finish;
  end
end

endmodule
