# ROM 控制器

只读存储器控制器,将总线读请求直接透传给 ROM 设备。实现在 `src/main/scala/mem/RomController.scala` 中。

## 行为

- 任何总线请求(`reqValid`)都会作为读请求(`ren`)连同完整的总线地址一起转发给 ROM。
- `rdata` 直接来自 ROM 的读数据输出。
- `respValid` 在 `reqValid` 之后一个周期拉高;ROM 预期在紧随其后的那个周期产生读数据(同步读)。

## 引脚

RomInterface(`src/main/scala/bus/Interface.scala:50`):

| 引脚  | 方向(相对于控制器)        | 描述                         |
|-------|-----------------------------|------------------------------|
| addr  | output                      | 读地址                       |
| ren   | output                      | 读使能,在任何总线请求时拉高 |
| rdata | input                       | 读数据                       |

## 备注

- 不支持写操作;控制器完全忽略 `wen` 和写选通信号。
