[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

一：介绍 <img height="50px" width="50px" src="https://github.com/Chris2018998/BeeCP/blob/master/doc/individual/bee.png"></img>
---
小蜜蜂对象池：一款轻量级高性能Java对象池

<a href="https://github.com/Chris2018998/BeeOP/blob/master/README.md">English</a>


二：性能
---

| 测试环境  | 参数值|
| ---      | ---  |
| PC       | I5-4210M(2.6hz),12G内存  |
| JDK      | JAVA8_64 |
| Pool     | 初始10，最大10 |
| 测试目标   100万次借用/归还1000线程 x1000次）|
| 期待结果  | 获取时间分布，平均时间 |
| 时间单位  | 毫秒 |

结果

| 时间(ms)     | commons-pool2-2.9.0 | BeeOP0.3_F         | BeeOP0.3_C  |
| ----------- |----------------     | -------------------| -------------|  
| 平均时间     | 2.677456            | 0.000347           |  0.000187    |

源码位置：[https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/BeeOP_Test.rar](https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/BeeOP_Test.rar)

日志文件：[https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/ObjectPool.log](https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/ObjectPool.log)
 

三：参考Demo
---

```java
class StringFactory extends ObjectFactory {
     //create object instance
     public Object create(Properties prop) throws ObjectException {
         return new String(""+System.currentTimeMillis());
     }
     
    //@Override method
    public void setDefault(Object obj) throws ObjectException {}
    //@Override method
    public void reset(Object obj) throws ObjectException {}
    //@Override method
    public void destroy(Object obj) {}
    //@Override method
    public boolean isAlive(Object obj, long timeout) {
        return true;
    }
 }
....

```java
  PoolConfig config = new PoolConfig();
  config.setObjectFactory(new StringFactory());
  config.setMaxActive(10);
  config.setInitialSize(10);
  config.setMaxWait(8000);
  ObjectPool pool= new ObjectPool(config);
  ProxyObject proxyObj = pool.getObject();
  .........
  proxyObj.close();
}
```


五：版本下载
---
**Java7**
```xml
<dependency>
   <groupId>com.github.chris2018998</groupId>
   <artifactId>beeop</artifactId>
   <version>0.3</version>
</dependency>
```
*友情提示：建议使用最新版本*


六：功能支持
---
```java
1：请求超时支持

2：两种模式：公平与竞争

3：支持连接安全关闭

4：断网连接池自动恢复

5：闲置超时和持有超时处理

6:若连接出现问题，池自动补充

7：支持连接池重置

8：支持连接回收前，属性重置

9：支持JMX

```

七：配置项说明
---
|  配置项          |   描述                        |   备注                            |
| ----------------| ---------------------------  | ------------------------          |
| poolName        | 连接池名                       |                                   |
| fairMode        | 连接池是否公平模式               | 公平锁,等待者优先获取连接            |
| initialSize     | 连接池初始大小                  |                                   |
| maxActive       | 连接池最大个数                  |                                   | 
| borrowConcurrentSize  | 信号量请求并发数（借用者线程数）| 不允许大于对象最大数                 |
| maxWait           |对象借用等待最大时间(毫秒)       | 默认8秒，对象请求最大等待时间           |
| idleTimeout       |对象闲置最大时间(毫秒)          | 默认3分钟，超时会被清理                 |  
| holdTimeout       |对象被持有不用的最大时间(毫秒)    | 默认5分钟，超时会被清理                 |  
| forceCloseConnection   |是否需要暴力关闭对象         |默认false;true:直接关闭使用中对象，false:等待处于使用中归还后再关闭|
| waitTimeToClearPool    |延迟清理的时候时间（秒）      |默认3秒，非暴力清理池下，还存在使用中的对象，延迟等待时间再清理|      
| idleCheckTimeInterval  |闲置扫描线程间隔时间(毫秒)             |                     |
| idleCheckTimeInitDelay |闲置扫描线程延迟时间再执行第一次扫描(毫秒)|                    |
| connectionFactoryClassName|自定义的JDBC连接工作类名            | 默认为空             |
| enableJMX                 |JMX监控支持开关                    |                    | |

