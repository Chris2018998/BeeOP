# BeeOP
High Performance Object Pool


```xml
<dependency>
   <groupId>com.github.chris2018998</groupId>
   <artifactId>beeop</artifactId>
   <version>0.3</version>
</dependency>
```


| 测试环境 | 参数值|
| ---     | ---  |
| PC      | I5-4210M(2.6hz),12G内存  |
| JDK     | JAVA8_64 |
| Pool    | 初始10，最大10 |
| 测试项   | 借用/归还 （1000线程 x1000次） |
| 期待结果 | 获取时间分布，平均时间 |
| 时间单位 | 毫秒 |

源码位置：[https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/BeeOP_Test.rar](https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/BeeOP_Test.rar)

| 统计 | commons-pool2-2.9.0 | BeeOP0.3_F | BeeOP0.3_C |
| --- | --- | --- | --- |
| success Count | 1000000 | 1000000 | 1000000 |
| fail count | 0 | 0 | 0 |
| avr(ms) | 2.677456 | 0.000347 | 0.000187 |
| min(ms) | 0 | 0 | 0 |
| max(ms) | 1691 | 11 | 11 |
| time=0ms | 966517 | 999993 | 999998 |
| 1ms=<time<=10ms | 10410 | 6 | 1 |
| 10ms<time<=50ms | 11129 | 1 | 1 |
| 50ms<time<=100ms | 5855 | 0 | 0 |
| 100ms<time<=200ms | 3728 | 0 | 0 |
| 200ms<time<=500ms | 1415 | 0 | 0 |
| 500ms<time<=1000ms | 323 | 0 | 0 |
| 1000ms<time<=2000ms | 623 | 0 | 0 |
| 2000ms<time<=800ms | 0 | 0 | 0 |
| 8000ms<time | 0 | 0 | 0 |

日志文件：[https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/ObjectPool.log](https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/ObjectPool.log)
