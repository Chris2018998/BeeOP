<img height="50px" width="50px" src="https://github.com/Chris2018998/BeeCP/blob/master/doc/individual/bee.png"></img> <a href="https://github.com/Chris2018998/BeeOP/blob/main/README_ZH.md">中文</a>

BeeOP：A light high-performance java object pool

Maven artifactId(Java7)
```xml
<dependency>
   <groupId>com.github.chris2018998</groupId>
   <artifactId>beeop</artifactId>
   <version>0.3</version>
</dependency>
```
---

##### Performance 
1 million borrow/return (1000 threads x 1000 times)
|       Pool       |commons-pool2-2.9.0  | BeeOP0.3_Fair      | BeeOP0.3_Compet   |
| -----------------|----------------     | -------------------| ----------------- |  
| Average time(ms) | 2.677456            | 0.000347           |  0.000187         |

PC:I5-4210M(2.6Hz，dual core4threads),12G memory Java:JAVA8_64 Pool:init-size10,max-size:10

Test log file：[https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/ObjectPool.log](https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/ObjectPool.log)

Test source：[https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/BeeOP_Test.rar](https://github.com/Chris2018998/BeeOP/blob/main/doc/temp/BeeOP_Test.rar)

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

3：Proxy object safe close when return

4：Pooled object cleared when network bad,pooled object recreate when network restore OK

5：Idle timeout and hold timeout(long time inactively hold by borrower)

6：Pooled object closed when exception,then create new one and transfer it to waiter

7：Pooled object reset when return

8：Pool can be reset

9：Jmx support

 
---
##### configuration
|     field name         |       Description                               |   Remark                                                    |
| ---------------------  | ------------------------------------------------| -----------------------------------------------------------|
|fairMode               |boolean indicator for borrow fair mode           |true:fair mode,false:comepete mode;default is false         |
|initialSize            |pooled object creation size when pool initialized|default is 0                                                |
|maxActive              |max size for pooled object instances in pool     |default is 10                                               | 
|borrowSemaphoreSize    |borrow concurrent thread size                    |default val=min(maxActive/2,cpu size)                       |                       
|maxWait                |max wait time to borrow one object instance      |time unit is ms,default is 8000 ms                          |                       
|idleTimeout            |max idle time of object instance in pool         |time unit is ms,default is 18000 ms                         |  
|holdTimeout            |max inactive time hold by borrower               |time unit is ms,default is 300000 ms                        |  
|forceCloseObject       |object close indicator when pool closing or reseting|true:close;false:wait object return, default is false    |            |waitTimeToClearPool     |park time to clear when checked object is in using state|effected  when forceCloseObject==true               |                              |idleCheckTimeInterval   |scan thread time interval to check idle object |time unit is ms,default is 300000 ms                         |
|objectFactoryClassName  |object factory class name                      |default is null                                              |
|enableJMX               |JMX boolean indicator for pool                 |default is false                                             |

