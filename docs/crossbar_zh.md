# Crossbar（交叉开关）

Crossbar 将**单个** SimpleBus 主设备（见 [bus](bus_zh.md)）连接到
`slaveAddrMap` 中每个从设备各自对应的一个端口。由于只有一个主设备，
因此无需仲裁：它是一个纯地址译码器，并带寄存化的转发逻辑。实现位于
`src/main/scala/bus/Crossbar.scala`。

## 参数

| 参数           | 类型                      | 描述 |
|----------------|---------------------------|------|
| `slaveAddrMap` | `Seq[(UInt, (Int, Int))]` | 每个从设备一项，格式与 [interconnect](interconnect_zh.md) 中相同：`(base, (hi, lo))`。当 `addr(hi, lo)` 等于 `base >> lo` 时，请求即指向该从设备。各项的译码区间不得重叠；完整说明见 [interconnect](interconnect_zh.md)。 |

## 端口

| 端口     | 类型                                      | 描述 |
|----------|-------------------------------------------|------|
| master   | `DataInterface`                           | 单个主设备端口。 |
| `slaves` | `Vec(numSlaves, Flipped(DataInterface))` | 按顺序对应 `slaveAddrMap` 中的每一项，每个从设备一个端口。 |

## 地址译码

主设备的请求会被寄存一拍：`reqValid` 延迟一个周期（`RegNext`），
而负载（`addr`、`size`、`wen`、`wdata`、`wmask`）被捕获到
`RegEnable` 缓冲器中。`slaveSel(s)` 将缓冲后的地址位
`addr(hi, lo)` 与每个从设备的基地址进行比较。

## 行为

- 每个从设备的 `reqValid` 等于寄存后的 valid 与其译码结果的 AND。
- 负载字段从寄存缓冲器**广播**到所有从设备；只有被选中的从设备
  `reqValid` 为高，因此只有它会响应数据。
- 主设备的 `respValid` 是所有从设备 `respValid` 的 OR；`rdata`
  由对从设备译码位做 `Mux1H` 选择得到。
- 响应在到达主设备前会寄存一个周期。
- Crossbar 假定**任意请求最多只有一个从设备响应**。如果地址映射中的
  两项重叠，合并后的响应结果是不确定的。

## 时序

- 请求在到达后经过一个周期才转发给被选中的从设备，
  给主设备的流水线增加了一个停顿周期。
- 响应在从设备将其置起后经过一个周期才到达主设备
  （从设备延迟加上 crossbar 自身的寄存器）。

## 缓冲与组合路径

与 interconnect 不同，crossbar 可以接很多从设备，且通常用于对延迟
不敏感的 MMIO。因此，interconnect 将响应路径保持为组合逻辑（见
[interconnect](interconnect_zh.md)），而 crossbar 则将**响应寄存一拍**，
以吸收由庞大的从设备列表带来的巨大 `rdata` 多路选择器。

请求缓冲的原因与 interconnect 中相同：在 CPU 一侧，地址生成本身就
可能是一条很长的组合路径，而从设备选择的译码又有很大的扇出——
无论后面接多少个从设备，把这条路径终结在寄存器里都是低成本的保险。

细节：

- 主设备只看到一级寄存器（`reqValid` + 负载缓冲器）；
  从主设备到任何从设备之间不存在组合路径。
- 从设备的负载输入是寄存器输出，以广播方式给出，没有多路选择器。
- 剩余的组合逻辑只有响应 `Mux1H`（译码位 × `rdata`）和
  `respValid` 的 OR 树，两者都随 `numSlaves` 增长——而这两者都
  被主设备 `rdata` 输出端的响应寄存器切断。
