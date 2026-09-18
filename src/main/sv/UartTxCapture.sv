module UartTxCapture (
    input clock,
    input io_ce,
    input [7:0] io_ch
);

always @(posedge clock) begin
    if (io_ce) begin
        $write("%c", io_ch);
        $fflush(1);
    end
end

endmodule
