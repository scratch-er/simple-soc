module SimulatedMem(
    input clock,
    input [31:0] io_addr,
    input io_ren,
    input io_wen,
    input [31:0] io_wdata,
    output [31:0] io_rdata
);

`ifdef VERILATOR
import "DPI-C" context function void dpi_c_mem_read(input bit [31:0] addr, output bit [31:0] rdata);
import "DPI-C" context function void dpi_c_mem_write(input bit [31:0] addr, input bit [31:0] wdata);
`endif

logic [31:0] rdata;
logic [31:0] rdata_avoid_race;
assign io_rdata = rdata;

always @(posedge clock) begin
    if (io_ren) begin
        dpi_c_mem_read(io_addr, rdata_avoid_race);
        rdata <= rdata_avoid_race;
    end
    if (io_wen) begin
        dpi_c_mem_write(io_addr, io_wdata);
    end
end

endmodule
