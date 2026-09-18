# SimpleBus

SimpleBus 是 CPU、互连(interconnect)以及外设/存储器控制器之间使用的片上总线协议。它定义在
`src/main/scala/bus/Interface.scala` 中。它源自"一生一芯"项目；背景介绍、波形图和练习可参见
[一生一芯 SimpleBus 讲义](https://ysyx.oscc.cc/docs/2607/e/7.html)。

SimpleBus 是一种仅有 valid 的协议:**没有 `ready` 信号,也没有反压(back-pressure)**。在每个
`reqValid` 为高电平的时钟上升沿,传输都会被无条件接受。

## 取指总线(Instruction Fetching Bus)

### 请求通道(Request channel)

| 信号      | 位宽 | 方向 | 描述                                          |
|-----------|------|------|-----------------------------------------------|
| `reqValid`| 1    | master → slave | 请求选通。每个请求有效一个周期。  |
| `addr`    | 32   | master → slave | 字节地址。                                    |

### 响应通道(Response channel)

| 信号       | 位宽 | 方向 | 描述                        |
|------------|------|------|-----------------------------|
| `respValid`| 1    | slave → master | 响应选通。    |
| `rdata`    | 32   | slave → master | 读数据。      |

指令端口(`InstrInterface`)恰好由这些信号构成:
`reqValid`、`addr`、`respValid` 和 `rdata`。指令取指总是整字读取,因此 IFU 总线
不携带任何写信号、访问尺寸和字节掩码——它就是去掉了所有写相关信号的 LSU 总线。

## 加载/存储总线(Load and Store Bus)

数据端口(`DataInterface`)用于 load/store 访存。它在相同的请求/响应通道基础上
扩展了写支持:

| 信号     | 位宽 | 方向 | 描述                                            |
|----------|------|------|-------------------------------------------------|
| `size`   | 2    | master → slave | 访问尺寸。`2` = 32 位字。       |
| `wen`    | 1    | master → slave | 1 = 写,0 = 读。                 |
| `wdata`  | 32   | master → slave | 写数据。                        |
| `wmask`  | 4    | master → slave | 写字节选通,`wdata` 每字节对应一位。 |

`DataInterface.asIfu` 通过固定 `size = 2`、`wen = 0`、`wmask = 0xF`,
将一个数据端口视为指令端口,这样取指单元(IFU)就可以接入 `DataInterface`
的 master 端口。这就是两条总线之间唯一的关系:IFU 总线是一个只读、仅整字的
子集,而 LSU 总线额外支持子字访问尺寸和带字节掩码的写操作。

> **注意:** 本项目的互连、交叉开关(crossbar)以及所有外设/存储器控制器在
> 每个端口上都使用 LSU 总线(`DataInterface`)。只有 CPU 的取指端口使用 IFU
> 总线;它通过 `asIfu` 进入互连。

## 规则

- 在每个 `reqValid` 为高电平的时钟上升沿,请求都会被接受。在 master 看到
  上一个请求的响应之前,不得发出新请求(每个 master 至多有一个未完成的请求)。
- 从设备(slave)最早在请求被接受的一个周期之后拉高 `respValid` 和 `rdata`。
- 可以在上一个响应到达的同一周期发出新请求。例外:ysyxSoC AXI 桥要求下一个
  请求至少在响应之后的一个周期才能发出。
- `respValid` 选通恰好持续一个周期。
- 写掩码以及 rdata/wdata 的字节布局遵循 AXI 惯例。
- 写操作同样以一次 `respValid` 选通完成(尽管写操作下 `rdata` 无意义),
  因此 master 可以统一对待读和写。
- `size` 遵循 AXI 惯例:`2'b00` = 1 字节,`2'b01` = 2 字节,`2'b10` = 4 字节。

## 模块约定

Master 驱动 `DataInterface`;slave 接收一个翻转
(`Flipped(DataInterface)`)的副本。内部请求寄存器使用普通 bundle
`LsuReq` / `LsuResp`。
