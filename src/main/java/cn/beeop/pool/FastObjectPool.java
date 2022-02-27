/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectHandle;
import cn.beeop.BeeObjectSourceConfig;
import cn.beeop.RawObjectFactory;
import cn.beeop.pool.atomic.AtomicIntegerFieldUpdaterImpl;
import cn.beeop.pool.atomic.AtomicReferenceFieldUpdaterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

import static cn.beeop.pool.PoolStaticCenter.*;

/**
 * Object Pool Implementation
 *
 * @author Chris.Liao
 * @version 1.0
 */
public final class FastObjectPool extends Thread implements ObjectPoolJmxBean, ObjectPool, ObjectTransferPolicy {
    private static final BorrowerState BOWER_NORMAL = new BorrowerState();
    private static final BorrowerState BOWER_WAITING = new BorrowerState();
    private static final AtomicIntegerFieldUpdater<PooledObject> ObjStUpd = AtomicIntegerFieldUpdaterImpl.newUpdater(PooledObject.class, "state");
    private static final AtomicReferenceFieldUpdater<Borrower, Object> BorrowStUpd = AtomicReferenceFieldUpdaterImpl.newUpdater(Borrower.class, Object.class, "state");
    private static final AtomicIntegerFieldUpdater<FastObjectPool> PoolStateUpd = AtomicIntegerFieldUpdaterImpl.newUpdater(FastObjectPool.class, "poolState");
    private static final Logger Log = LoggerFactory.getLogger(FastObjectPool.class);
    private final Object synLock = new Object();

    private String poolName;
    private int poolMaxSize;
    private volatile int poolState;
    private int semaphoreSize;
    private PoolSemaphore semaphore;
    private long maxWaitNs;//nanoseconds
    private long idleTimeoutMs;//milliseconds
    private long holdTimeoutMs;//milliseconds

    private int stateCodeOnRelease;
    private long validAssumeTime;//milliseconds
    private int validTestTimeout;//seconds
    private long delayTimeForNextClearNs;//nanoseconds
    private PooledObject templatePooledObject;
    private ObjectTransferPolicy transferPolicy;
    private RawObjectFactory objectFactory;
    private volatile PooledObject[] pooledArray;

    private AtomicInteger servantState;
    private AtomicInteger servantTryCount;
    private AtomicInteger idleScanState;
    private IdleTimeoutScanThread idleScanThread;
    private ConcurrentLinkedQueue<Borrower> waitQueue;
    private ThreadLocal<WeakReference<Borrower>> threadLocal;
    private BeeObjectSourceConfig poolConfig;
    private ObjectPoolMonitorVo monitorVo;
    private ObjectPoolHook exitHook;
    private boolean printRuntimeLog;

    //***************************************************************************************************************//
    //                1: Pool initialize and Pooled object create/remove methods(4)                                  //                                                                                  //
    //***************************************************************************************************************//

    /**
     * Method-1.1: initialize pool with configuration
     *
     * @param config data source configuration
     * @throws Exception check configuration fail or to create initiated object
     */
    public synchronized void init(BeeObjectSourceConfig config) throws Exception {
        if (config == null) throw new ObjectPoolException("Configuration can't be null");
        if (poolState != POOL_NEW) throw new ObjectPoolException("Pool has initialized");

        poolConfig = config.check();//why need a copy here?
        poolName = poolConfig.getPoolName();
        Log.info("BeeOP({})starting....", poolName);
        poolMaxSize = poolConfig.getMaxActive();
        objectFactory = poolConfig.getObjectFactory();
        pooledArray = new PooledObject[0];
        templatePooledObject = new PooledObject(this,
                objectFactory,
                poolConfig.getObjectInterfaces(),
                poolConfig.getExcludeMethodNames());
        createInitObjects(poolConfig.getInitialSize());

        String poolMode;
        if (poolConfig.isFairMode()) {
            poolMode = "fair";
            transferPolicy = new FairTransferPolicy();
        } else {
            poolMode = "compete";
            transferPolicy = this;
        }
        stateCodeOnRelease = transferPolicy.getStateCodeOnRelease();

        idleTimeoutMs = poolConfig.getIdleTimeout();
        holdTimeoutMs = poolConfig.getHoldTimeout();
        maxWaitNs = TimeUnit.MILLISECONDS.toNanos(poolConfig.getMaxWait());
        delayTimeForNextClearNs = TimeUnit.MILLISECONDS.toNanos(poolConfig.getDelayTimeForNextClear());
        validAssumeTime = poolConfig.getValidAssumeTime();
        validTestTimeout = poolConfig.getValidTestTimeout();
        printRuntimeLog = poolConfig.isPrintRuntimeLog();

        semaphoreSize = poolConfig.getBorrowSemaphoreSize();
        semaphore = new PoolSemaphore(semaphoreSize, poolConfig.isFairMode());
        waitQueue = new ConcurrentLinkedQueue<Borrower>();
        threadLocal = new ThreadLocal<WeakReference<Borrower>>();
        servantTryCount = new AtomicInteger(0);
        servantState = new AtomicInteger(THREAD_WORKING);
        idleScanState = new AtomicInteger(THREAD_WORKING);
        idleScanThread = new IdleTimeoutScanThread(this);
        monitorVo = createPoolMonitorVo(poolMode);
        exitHook = new ObjectPoolHook(this);
        Runtime.getRuntime().addShutdownHook(exitHook);
        registerJmx();

        this.setDaemon(true);
        this.setName(poolName + "-workServant");
        this.setPriority(Thread.MIN_PRIORITY);
        idleScanThread.setDaemon(true);
        idleScanThread.setName(poolName + "-idleCheck");
        idleScanThread.setPriority(Thread.MIN_PRIORITY);

        this.start();
        idleScanThread.start();
        poolState = POOL_READY;

        Log.info("BeeOP({})has startup{mode:{},init size:{},max size:{},semaphore size:{},max wait:{}ms",
                poolName,
                poolMode,
                pooledArray.length,
                poolMaxSize,
                semaphoreSize,
                poolConfig.getMaxWait());
    }

    /**
     * Method-1.2: create specified size objects to pool,if zero,then try to create one
     *
     * @throws Exception error occurred in creating objects
     */
    private void createInitObjects(int initSize) throws Exception {
        try {
            int size = initSize > 0 ? initSize : 1;
            for (int i = 0; i < size; i++)
                createPooledEntry(OBJECT_IDLE);
        } catch (Throwable e) {
            for (PooledObject pooledEntry : pooledArray)
                removePooledEntry(pooledEntry, DESC_RM_INIT);
            if (initSize > 0)
                throw e instanceof ObjectException ? (ObjectException) e : new ObjectException(e);
        }
    }

    //Method-1.3:create one pooled object
    private PooledObject createPooledEntry(int state) throws ObjectException {
        synchronized (synLock) {
            int l = pooledArray.length;
            if (l < poolMaxSize) {
                if (printRuntimeLog)
                    Log.info("BeeOP({}))begin to create a new pooled object,state:{}", poolName, state);

                Object rawObj = null;
                try {
                    rawObj = objectFactory.create();
                    PooledObject p = templatePooledObject.setDefaultAndCopy(rawObj, state);
                    if (printRuntimeLog)
                        Log.info("BeeOP({}))has created a new pooled object:{},state:{}", poolName, p, state);
                    PooledObject[] arrayNew = new PooledObject[l + 1];
                    System.arraycopy(pooledArray, 0, arrayNew, 0, l);
                    arrayNew[l] = p;// tail
                    pooledArray = arrayNew;
                    return p;
                } catch (Throwable e) {
                    if (rawObj != null) objectFactory.destroy(rawObj);
                    throw e instanceof ObjectException ? (ObjectException) e : new ObjectException(e);
                }
            } else {
                return null;
            }
        }
    }


    //***************************************************************************************************************//
    //                  2: Pooled object borrow and release methods(8)                                               //                                                                                  //
    //***************************************************************************************************************//

    //Method-1.4: remove one pooled object
    private void removePooledEntry(PooledObject p, String removeType) {
        if (printRuntimeLog)
            Log.info("BeeOP({}))begin to remove pooled object:{},reason:{}", poolName, p, removeType);
        p.onBeforeRemove();
        synchronized (synLock) {
            for (int l = pooledArray.length, i = l - 1; i >= 0; i--) {
                if (pooledArray[i] == p) {
                    PooledObject[] arrayNew = new PooledObject[l - 1];
                    System.arraycopy(pooledArray, 0, arrayNew, 0, i);
                    int m = l - i - 1;
                    if (m > 0) System.arraycopy(pooledArray, i + 1, arrayNew, i, m);
                    pooledArray = arrayNew;
                    if (printRuntimeLog)
                        Log.info("BeeOP({}))has removed pooled object:{},reason:{}", poolName, p, removeType);
                    break;
                }
            }
        }
    }

    /**
     * Method-2.1:borrow one object from pool,if search one idle object in pool,then try to catch it and return it
     * if not search,then wait until other borrowers release objects or wait timeout
     *
     * @return pooled object,
     * @throws Exception if pool is closed or waiting timeout,then throw exception
     */
    public final BeeObjectHandle getObject() throws Exception {
        if (poolState != POOL_READY) throw PoolCloseException;

        //0:try to get from threadLocal cache
        WeakReference<Borrower> r = threadLocal.get();
        Borrower b = r != null ? r.get() : null;
        if (b != null) {
            PooledObject p = b.lastUsed;
            if (p != null && p.state == OBJECT_IDLE && ObjStUpd.compareAndSet(p, OBJECT_IDLE, OBJECT_USING)) {
                if (testOnBorrow(p)) return createObjectHandle(p, b);
                b.lastUsed = null;
            }
        } else {
            b = new Borrower();
            threadLocal.set(new WeakReference<Borrower>(b));
        }

        long deadline = System.nanoTime();
        try {
            //1:try to acquire a permit
            if (!semaphore.tryAcquire(maxWaitNs, TimeUnit.NANOSECONDS))
                throw RequestTimeoutException;
        } catch (InterruptedException e) {
            throw RequestInterruptException;
        }
        try {//semaphore acquired
            //2:try search one or create one
            PooledObject p = searchOrCreate();
            if (p != null) return createObjectHandle(p, b);

            //3:try to get one transferred one
            b.state = BOWER_NORMAL;
            waitQueue.offer(b);
            boolean failed = false;
            Throwable cause = null;
            deadline += maxWaitNs;
            Thread thd = b.thread;

            do {
                Object s = b.state;
                if (s instanceof PooledObject) {
                    p = (PooledObject) s;
                    if (transferPolicy.tryCatch(p) && testOnBorrow(p)) {
                        waitQueue.remove(b);
                        return createObjectHandle(p, b);
                    }
                } else if (s instanceof Throwable) {
                    waitQueue.remove(b);
                    throw s instanceof ObjectException ? (ObjectException) s : new ObjectException((Throwable) s);
                }

                if (failed) {
                    BorrowStUpd.compareAndSet(b, s, cause);
                } else if (s instanceof PooledObject) {
                    b.state = BOWER_NORMAL;
                    yield();
                } else {//here:(state == BOWER_NORMAL)
                    long t = deadline - System.nanoTime();
                    if (t > 0L) {
                        if (b.state == BOWER_NORMAL && BorrowStUpd.compareAndSet(b, BOWER_NORMAL, BOWER_WAITING)) {
                            if (servantState.get() == THREAD_WAITING && servantTryCount.get() > 0 && servantState.compareAndSet(THREAD_WAITING, THREAD_WORKING))
                                LockSupport.unpark(this);

                            LockSupport.parkNanos(t);//block exit:1:get transfer 2:interrupted 3:timeout 4:unpark before parkNanos
                            if (thd.isInterrupted()) {
                                failed = true;
                                cause = RequestInterruptException;
                            }
                            if (b.state == BOWER_WAITING && BorrowStUpd.compareAndSet(b, BOWER_WAITING, failed ? cause : BOWER_NORMAL) && !failed) {
                                Thread.yield();
                            }
                        }
                    } else {//timeout
                        failed = true;
                        cause = RequestTimeoutException;
                    }
                }//end (state == BOWER_NORMAL)
            } while (true);//while
        } finally {
            semaphore.release();
        }
    }

    //Method-2.2: search one idle Object,if not found,then try to create one
    private PooledObject searchOrCreate() throws ObjectException {
        PooledObject[] array = pooledArray;
        for (int i = 0, l = array.length; i < l; i++) {
            PooledObject p = array[i];
            if (p.state == OBJECT_IDLE && ObjStUpd.compareAndSet(p, OBJECT_IDLE, OBJECT_USING) && testOnBorrow(p))
                return p;
        }
        if (pooledArray.length < poolMaxSize)
            return createPooledEntry(OBJECT_USING);
        return null;
    }

    //Method-2.3: try to wakeup servant thread to work if it waiting
    private void tryWakeupServantThread() {
        int c;
        do {
            c = servantTryCount.get();
            if (c >= poolMaxSize) return;
        } while (!servantTryCount.compareAndSet(c, c + 1));
        if (!waitQueue.isEmpty() && servantState.get() == THREAD_WAITING && servantState.compareAndSet(THREAD_WAITING, THREAD_WORKING))
            LockSupport.unpark(this);
    }

    //Method-2.4: return object to pool after borrower end of use object
    public final void recycle(PooledObject p) {
        Iterator<Borrower> iterator = this.waitQueue.iterator();
        this.transferPolicy.beforeTransfer(p);
        W:while(iterator.hasNext()) {
            Borrower b = (Borrower)iterator.next();
            Object state;
            do {
                if (p.state != this.stateCodeOnRelease)return;
                state = b.state;
                if (!(state instanceof BorrowerState))continue W;
            } while(!BorrowStUpd.compareAndSet(b, state, p));
            if (state == BOWER_WAITING)LockSupport.unpark(b.thread);
            return;
        }
        this.transferPolicy.onTransferFail(p);
        this.tryWakeupServantThread();
    }


    /**
     * Method-2.5:when object create failed,creator thread will transfer caused exception to one waiting borrower,
     * which will exit wait and throw this exception.
     *
     * @param e: transfer Exception to waiter
     */
    private void transferException(Throwable e) {
        Iterator iterator = this.waitQueue.iterator();
        W:while(iterator.hasNext()) {
            Borrower b = (Borrower)iterator.next();
            Object state;
            do {
                state = b.state;
                if (!(state instanceof BorrowerState))continue W;
            } while(!BorrowStUpd.compareAndSet(b, state, e));
            if (state == BOWER_WAITING)LockSupport.unpark(b.thread);
            return;
        }
    }

    //Method-2.6: remove object when exception occur in return
    public void abandonOnReturn(PooledObject p) {
        removePooledEntry(p, DESC_RM_BAD);
        tryWakeupServantThread();
    }

    //Method-2.7: check object alive state,if not alive then remove it from pool
    private boolean testOnBorrow(PooledObject p) {
        if (System.currentTimeMillis() - p.lastAccessTime > validAssumeTime && !objectFactory.isValid(p, validTestTimeout)) {
            removePooledEntry(p, DESC_RM_BAD);
            tryWakeupServantThread();
            return false;
        } else {
            return true;
        }
    }

    //Compete Pooled connection transfer
    //private static final class CompeteTransferPolicy implements ObjectTransferPolicy {
    public final int getStateCodeOnRelease() {
        return OBJECT_IDLE;
    }

    public final void beforeTransfer(PooledObject p) {
        p.state = OBJECT_IDLE;
    }

    public final boolean tryCatch(PooledObject p) {
        return p.state == OBJECT_IDLE && ObjStUpd.compareAndSet(p, OBJECT_IDLE, OBJECT_USING);
    }

    public void onTransferFail(PooledObject p) {
    }

    //***************************************************************************************************************//
    //               3: Pooled object idle-timeout/hold-timeout scan methods(4)                                      //                                                                                  //
    //***************************************************************************************************************//
    //Method-3.1: check whether exists borrows under semaphore
    private boolean existBorrower() {
        return semaphoreSize > semaphore.availablePermits();
    }

    //Method-3.2 shutdown two work threads in pool
    private void shutdownPoolThread() {
        int curState = servantState.get();
        servantState.set(THREAD_EXIT);
        if (curState == THREAD_WAITING) LockSupport.unpark(this);

        curState = idleScanState.get();
        idleScanState.set(THREAD_EXIT);
        if (curState == THREAD_WAITING) LockSupport.unpark(idleScanThread);
    }

    //Method-3.3: pool servant thread run method
    public void run() {
        while (poolState != POOL_CLOSED) {
            while (servantState.get() == THREAD_WORKING && servantTryCount.get() > 0) {
                try {
                    servantTryCount.decrementAndGet();
                    PooledObject p = searchOrCreate();
                    if (p != null) recycle(p);
                } catch (Throwable e) {
                    transferException(e);
                }
            }

            if (servantState.get() == THREAD_EXIT)
                break;
            if (servantState.compareAndSet(THREAD_WORKING, THREAD_WAITING)) {
                LockSupport.park();
            }
        }
    }

    /**
     * Method-3.4: inner timer will call the method to clear some idle timeout objects
     * or dead objects,or long time not active objects in using state
     */
    private void closeIdleTimeoutPooledEntry() {
        if (poolState == POOL_READY) {
            PooledObject[] array = pooledArray;
            for (int i = 0, l = array.length; i < l; i++) {
                PooledObject p = array[i];
                int state = p.state;
                if (state == OBJECT_IDLE && !existBorrower()) {
                    boolean isTimeoutInIdle = System.currentTimeMillis() - p.lastAccessTime - idleTimeoutMs >= 0L;
                    if (isTimeoutInIdle && ObjStUpd.compareAndSet(p, state, OBJECT_CLOSED)) {//need close idle
                        removePooledEntry(p, DESC_RM_IDLE);
                        tryWakeupServantThread();
                    }
                } else if (state == OBJECT_USING) {
                    if (System.currentTimeMillis() - p.lastAccessTime - holdTimeoutMs >= 0L) {//hold timeout
                        BeeObjectHandle handleInUsing = p.handleInUsing;
                        if (handleInUsing != null) {
                            tryClosedProxyHandle(handleInUsing);
                        } else {
                            removePooledEntry(p, DESC_RM_BAD);
                            tryWakeupServantThread();
                        }
                    }
                } else if (state == OBJECT_CLOSED) {
                    removePooledEntry(p, DESC_RM_CLOSED);
                    tryWakeupServantThread();
                }
            }

            ObjectPoolMonitorVo vo = this.getPoolMonitorVo();
            if (printRuntimeLog)
                Log.info("BeeOP({})idle:{},using:{},semaphore-waiting:{},transfer-waiting:{}", poolName, vo.getIdleSize(), vo.getUsingSize(), vo.getSemaphoreWaitingSize(), vo.getTransferWaitingSize());
        }
    }

    //***************************************************************************************************************//
    //                                      4: Pool clear/close methods(5)                                           //                                                                                  //
    //***************************************************************************************************************//
    //Method-4.1: remove all connections from pool
    public void clear() {
        clear(false);
    }

    //Method-4.2: remove all connections from pool
    public void clear(boolean force) {
        if (PoolStateUpd.compareAndSet(this, POOL_READY, POOL_CLEARING)) {
            Log.info("BeeOP({})begin to remove objects", poolName);
            clear(force, DESC_RM_CLEAR);
            poolState = POOL_READY;// restore state;
            Log.info("BeeOP({})all objects were removed and restored to accept new requests", poolName);
        }
    }

    //Method-4.3: remove all connections from pool
    private void clear(boolean force, String source) {
        semaphore.interruptWaitingThreads();
        while (!waitQueue.isEmpty()) transferException(PoolCloseException);

        while (pooledArray.length > 0) {
            PooledObject[] array = pooledArray;
            for (int i = 0, l = array.length; i < l; i++) {
                PooledObject p = array[i];
                if (ObjStUpd.compareAndSet(p, OBJECT_IDLE, OBJECT_CLOSED)) {
                    removePooledEntry(p, source);
                } else if (p.state == OBJECT_CLOSED) {
                    removePooledEntry(p, source);
                } else if (p.state == OBJECT_USING) {
                    BeeObjectHandle handleInUsing = p.handleInUsing;
                    if (handleInUsing != null) {
                        if (force || System.currentTimeMillis() - p.lastAccessTime - holdTimeoutMs >= 0L)
                            tryClosedProxyHandle(handleInUsing);
                    } else {
                        removePooledEntry(p, source);
                    }
                }
            } // for
            if (pooledArray.length > 0) LockSupport.parkNanos(delayTimeForNextClearNs);
        } // while
    }

    //Method-4.4: closed check
    public boolean isClosed() {
        return poolState == POOL_CLOSED;
    }

    // Method-4.5: close pool
    public void close() {
        do {
            int poolStateCode = poolState;
            if ((poolStateCode == POOL_NEW || poolStateCode == POOL_READY) && PoolStateUpd.compareAndSet(this, poolStateCode, POOL_CLOSED)) {
                Log.info("BeeOP({})begin to shutdown", poolName);
                clear(poolConfig.isForceCloseUsingOnClear(), DESC_RM_DESTROY);
                unregisterJmx();
                shutdownPoolThread();

                try {
                    Runtime.getRuntime().removeShutdownHook(exitHook);
                } catch (Throwable e) {
                }
                Log.info("BeeOP({})has shutdown", poolName);
                break;
            } else if (poolStateCode == POOL_CLOSED) {
                break;
            } else {
                LockSupport.parkNanos(delayTimeForNextClearNs);// default wait 3 seconds
            }
        } while (true);
    }

    //***************************************************************************************************************//
    //                                       5: Pool monitor/jmx methods(12)                                         //                                                                                  //
    //***************************************************************************************************************//
    //Method-5.1: set pool info debug switch
    public void setPrintRuntimeLog(boolean indicator) {
        this.printRuntimeLog = indicator;
    }

    //Method-5.2: size of all pooled object
    public int getTotalSize() {
        return pooledArray.length;
    }

    //Method-5.3: size of idle pooled object
    public int getIdleSize() {
        int idleSize = 0;
        PooledObject[] array = pooledArray;
        for (int i = 0, l = array.length; i < l; i++)
            if (array[i].state == OBJECT_IDLE) idleSize++;
        return idleSize;
    }

    //Method-5.4: size of using pooled connections
    public int getUsingSize() {
        int active = pooledArray.length - getIdleSize();
        return (active > 0) ? active : 0;
    }

    //Method-5.5: waiting size for semaphore
    public int getSemaphoreAcquiredSize() {
        return poolConfig.getBorrowSemaphoreSize() - semaphore.availablePermits();
    }

    //Method-5.6: using size of semaphore permit
    public int getSemaphoreWaitingSize() {
        return semaphore.getQueueLength();
    }

    //Method-5.7: waiting size in transfer queue
    public int getTransferWaitingSize() {
        int size = 0;
        for (Borrower borrower : waitQueue) {
            if (borrower.state instanceof BorrowerState) size++;
        }
        return size;
    }

    //Method-5.8: register pool to jmx
    private void registerJmx() {
        if (poolConfig.isEnableJmx()) {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            registerJmxBean(mBeanServer, String.format(" cn.beeop.pool.FastObjectPool:type=BeeOP(%s)", poolName), this);
            registerJmxBean(mBeanServer, String.format("cn.beeop.BeeObjectSourceConfig:type=BeeOP(%s)-config", poolName), poolConfig);
        }
    }

    //Method-5.9: jmx register
    private void registerJmxBean(MBeanServer mBeanServer, String regName, Object bean) {
        try {
            ObjectName jmxRegName = new ObjectName(regName);
            if (!mBeanServer.isRegistered(jmxRegName)) {
                mBeanServer.registerMBean(bean, jmxRegName);
            }
        } catch (Exception e) {
            Log.warn("BeeOP({})failed to register jmx-bean:{}", poolName, regName, e);
        }
    }

    //Method-5.10: pool unregister from jmx
    private void unregisterJmx() {
        if (poolConfig.isEnableJmx()) {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            unregisterJmxBean(mBeanServer, String.format(" cn.beeop.pool.FastObjectPool:type=BeeOP(%s)", poolName));
            unregisterJmxBean(mBeanServer, String.format("cn.beeop.BeeObjectSourceConfig:type=BeeOP(%s)-config", poolName));
        }
    }

    //Method-5.11: jmx unregister
    private void unregisterJmxBean(MBeanServer mBeanServer, String regName) {
        try {
            ObjectName jmxRegName = new ObjectName(regName);
            if (mBeanServer.isRegistered(jmxRegName)) {
                mBeanServer.unregisterMBean(jmxRegName);
            }
        } catch (Exception e) {
            Log.warn("BeeOP({})failed to unregister jmx-bean:{}", poolName, regName, e);
        }
    }

    //Method-5.12 create monitor vo
    private ObjectPoolMonitorVo createPoolMonitorVo(String poolMode) {
        String hostIP = null;
        try {
            hostIP = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            Log.info("BeeOP({})failed to resolve pool hose ip", poolName);
        }
        Thread currentThread = Thread.currentThread();
        return new ObjectPoolMonitorVo(poolName, poolMode, poolMaxSize, hostIP, currentThread.getId(), currentThread.getName());
    }

    //Method-5.12: pool monitor vo
    public ObjectPoolMonitorVo getPoolMonitorVo() {
        int totSize = getTotalSize();
        int idleSize = getIdleSize();
        monitorVo.setPoolState(poolState);
        monitorVo.setIdleSize(idleSize);
        monitorVo.setUsingSize(totSize - idleSize);
        monitorVo.setSemaphoreWaitingSize(getSemaphoreWaitingSize());
        monitorVo.setTransferWaitingSize(getTransferWaitingSize());
        return monitorVo;
    }

    //***************************************************************************************************************//
    //                                  6: Pool inner interface/class(4)                                             //                                                                                  //
    //***************************************************************************************************************//

    //class-6.1:Compete Pooled connection transfer
    private static final class FairTransferPolicy implements ObjectTransferPolicy {
        public final int getStateCodeOnRelease() {
            return OBJECT_USING;
        }

        public final void beforeTransfer(PooledObject p) {
        }

        public final boolean tryCatch(PooledObject p) {
            return p.state == OBJECT_USING;
        }

        public void onTransferFail(PooledObject p) {
            p.state = OBJECT_IDLE;
        }
    }

    //class-6.2:Semaphore extend
    private static final class PoolSemaphore extends Semaphore {
        PoolSemaphore(int permits, boolean fair) {
            super(permits, fair);
        }

        void interruptWaitingThreads() {
            for (Thread thread : super.getQueuedThreads()) {
                State state = thread.getState();
                if (state == State.WAITING || state == State.TIMED_WAITING) {
                    thread.interrupt();
                }
            }
        }
    }

    //class-6.3:Idle scan thread
    private static final class IdleTimeoutScanThread extends Thread {
        private final FastObjectPool pool;
        private final AtomicInteger idleScanState;

        IdleTimeoutScanThread(FastObjectPool pool) {
            this.pool = pool;
            this.idleScanState = pool.idleScanState;
        }

        public void run() {
            long checkTimeIntervalNanos = TimeUnit.MILLISECONDS.toNanos(pool.poolConfig.getTimerCheckInterval());
            while (idleScanState.get() == THREAD_WORKING) {
                LockSupport.parkNanos(checkTimeIntervalNanos);
                try {
                    pool.closeIdleTimeoutPooledEntry();
                } catch (Throwable e) {
                }
            }
        }
    }

    //class-6.4:JVM exit hook
    private class ObjectPoolHook extends Thread {
        private final FastObjectPool pool;

        ObjectPoolHook(FastObjectPool pool) {
            this.pool = pool;
        }

        public void run() {
            try {
                Log.info("BeeOP({})PoolHook Running", pool.poolName);
                pool.close();
            } catch (Throwable e) {
                Log.error("BeeOP({})Error at closing pool,cause:", pool.poolName, e);
            }
        }
    }
}
