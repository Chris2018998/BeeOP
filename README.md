<img height="50px" width="50px" src="https://github.com/Chris2018998/BeeCP/blob/master/doc/individual/bee.png"></img> <a href="https://github.com/Chris2018998/BeeOP/blob/main/README_ZH.md">中文</a>

BeeOP：A light high-performance java object pool

Maven artifactId（Java7)
```xml
<dependency>
   <groupId>com.github.chris2018998</groupId>
   <artifactId>beeop</artifactId>
   <version>0.3</version>
</dependency>
```
---

##### Performance 
1 million borrowing / returning (1000 threads x 1000 times)
|       Pool       |commons-pool2-2.9.0  | BeeOP0.3_Fair      | BeeOP0.3_Compet   |
| -----------------|----------------     | -------------------| ----------------- |  
| Average time(ms) | 2.677456            | 0.000347           |  0.000187         |

PC:I5-4210M(2.6Hz，dual core4threads),12G memory Java:JAVA8_64 Pool:init-size10,max-size:10

Test source：[https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/BeeOP_Test.rar](https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/BeeOP_Test.rar)

Test log file：[https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/ObjectPool.log](https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/ObjectPool.log)
 
---

##### Example

```java
class Book{
   private String name;
   private long number;
   public Book(String name,long number){
      this.name=name;
      this.number=number;
   }
   pulbic long getName(){
     return name;
   }
   pulbic String getNumber(){
     return number;
   }
}
```
 
```java
class BookFactory extends ObjectFactory {
     public Object create(Properties prop) throws ObjectException {
         return new Book("Java核心技术·卷1",System.currentTimeMillis());
     }
    public void setDefault(Object obj) throws ObjectException {}
    public void reset(Object obj) throws ObjectException {}
    public void destroy(Object obj) {}
    public boolean isAlive(Object obj, long timeout) {
        return true;
    }
 }
 ```
 
 ```java
 public class TestBookPool{
   public static void main(String[]){
     PoolConfig config = new PoolConfig();
     config.setObjectFactory(new StringFactory());
     config.setMaxActive(10);
     config.setInitialSize(10);
     config.setMaxWait(8000);
     ObjectPool pool= new ObjectPool(config);
     ProxyObject proxyObj= pool.getObject();
     String name=(String)proxyObj.call("getName",new Class[0],new Object[0]);
     proxyObj.close();
   }
 }
 
```
---
##### Features

1：Borrowing timeout

2：Fair mode and compete mode for borrowing 

3：Proxy object safe close when returning 

4：Pooled object cleared when network bad,pooled object recreate when network restore OK

5：Idle timeout and hold timeout(long time inactively hold by borrower)

6：Pooled object closed when exception,then create new one transfer to waiter

7：Pooled object reset when returning

8：Pool can be reset

9：Jmx used in pool with indicator

 
---
##### 配置项说明

|             配置项      |   描述                          |   备注                                                      |
| ---------------------  | ----------------------------    | -----------------------------------------------------------|
| fairMode               |是否公平模式                       |默认false,竞争模式                                            |
| initialSize            |池初始创建对象数                   | 默认为0                                                      |
| maxActive              |池最大创建对象数                   | 默认为10个                                                   | 
| borrowSemaphoreSize    |对象借线程最大并行数                |默认取最大对象数/2与cpu核心数的最小值                            |
| maxWait                |对象借用等待最大时间(毫秒)           |默认8秒，对象请求最大等待时间                                   |
| idleTimeout            |对象闲置最大时间(毫秒)              |默认3分钟，超时会被清理                                         |  
| holdTimeout            |对象被持有不用最大时间(毫秒)         |默认5分钟，超时会被清理                                         |  
| forceCloseObject       |是否需要暴力关闭对象                |池关闭或重置，使用，默认false;true:直接关闭使用中对象，false:等待处于使用中归还后再关闭|
| waitTimeToClearPool    |延迟清理的时候时间（秒）             |默认3秒，非暴力清理池下，还存在使用中的对象，延迟等待时间再清理     |      
| idleCheckTimeInterval  |对象闲置扫描线程间隔时间(毫秒)       |默认5分钟                                                   |
| objectFactoryClassName |自定义的对象工厂类名                |默认为空                                                    |
| enableJMX              |JMX监控支持开关                    |默认false                                                  |

