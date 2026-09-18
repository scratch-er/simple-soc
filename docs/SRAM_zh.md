# SRAM 控制器

支持按字节写入（read-modify-write，读-改-写）的 SRAM 控制器。实现在 `src/main/scala/mem/SramController.scala` 中。

## 行为

- **整字写入**（`wen` 且 `wmask === 0b1111`）：写入数据在请求周期本身即驱动到 SRAM 并置位 `wen` —— 单周期写入。
- **部分写入**（`wen` 且 `wmask !== 0b1111`）：执行读-改-写。请求（地址、wmask、wdata）被捕获到寄存器中，并在请求周期对 SRAM 发起读操作；下一个周期，将读回的数据与写入数据按 `wmask` 逐字节合并（掩码置位的字节取 `wdata`，否则取刚读出的字节），然后写回。
- **读**（`reqValid && !wen`）：地址转发到 SRAM 并置位 `ren`；读数据由 SRAM 返回。
- 对于读和整字写入，`respValid` 在请求之后的一个周期置位；对于部分写入，则在读-改-写完成之后的一个周期置位。SRAM 预期具有同步读数据输出（1 个周期的读延迟）。

## 引脚

SramInterface（`src/main/scala/bus/Interface.scala:56`）：

| 引脚  | 方向（相对于控制器） | 说明 |
|-------|-----------------------------|------|
| addr  | output                      | 访问地址（读-改-写期间由请求寄存器保持） |
| ren   | output                      | 读使能，在读操作以及部分写入的读阶段置位 |
| wen   | output                      | 写使能，在整字写入以及部分写入的写回阶段置位 |
| rdata | input                       | 读数据 |
| wdata | output                      | 写数据（读-改-写期间与读数据合并） |
