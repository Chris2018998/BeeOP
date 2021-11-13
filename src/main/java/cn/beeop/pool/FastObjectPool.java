/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop.pool;

import cn.beeop.BeeObjectException;
import cn.beeop.BeeObjectFactory;
import cn.beeop.BeeObjectHandle;
import cn.beeop.BeeObjectSourceConfig;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

import static cn.beeop.pool.PoolStaticCenter.*;
import static java.lang.System.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.locks.LockSupport.*;

/**
 * Object Pool Implementation
 *
 * @author Chris.Liao
 * @version 1.0
 */
public final class FastObjectPool extends Thread implements PoolJmxBean, ObjectPool {
    private static final long spinForTimeoutThreshold = 1000L;
    private static final int maxTimedSpins = Runtime.getRuntime().availableProcessors() < 2 ? 0 : 32;
    private static final AtomicIntegerFieldUpdater<PooledEntry> ObjStUpd = AtomicIntegerFieldUpdater.newUpdater(PooledEntry.class, "state");
    private static final AtomicReferenceFieldUpdater<Borrower, Object> BorrowStUpd = AtomicReferenceFieldUpdater.newUpdater(Borrower.class, Object.class, "state");

    private final ConcurrentLinkedQueue<Borrower> waitQueue = new ConcurrentLinkedQueue<Borrower>();
    private final ThreadLocal<WeakReference<Borrower>> threadLocal = new ThreadLocal<WeakReference<Borrower>>();
    private final PoolMonitorVo monitorVo = new PoolMonitorVo();
    private final AtomicInteger poolState = new AtomicInteger(POOL_UNINIT);
    private final AtomicInteger servantThreadState = new AtomicInteger(THREAD_WORKING);
    private final AtomicInteger servantThreadTryCount = new AtomicInteger(0);
    private final AtomicInteger idleScanThreadState = new AtomicInteger(THREAD_WORKING);
    private final IdleTimeoutScanThread idleScanThread = new IdleTimeoutScanThread(this);
    private boolean printRuntimeLog;

    private String poolName;
    private String poolMode;
    private int poolMaxSize;
    private long maxWaitNs;//nanoseconds
    private long idleTimeoutMs;//milliseconds
    private long holdTimeoutMs;//milliseconds
    private int unCatchStateCode;

    private long validAssumeTime;//milliseconds
    private int validTestTimeout;//seconds
    private long delayTimeForNextClearNanos;//nanoseconds
    private TransferPolicy transferPolicy;
    private ObjectPoolHook exitHook;
    private BeeObjectSourceConfig poolConfig;
    private int semaphoreSize;
    private PoolSemaphore semaphore;
    private BeeObjectFactory objectFactory;
    private volatile PooledEntry[] poolEntryArray = new PooledEntry[0];

    private Properties createProperties;
    private Set<String> excludeMethodNames;
    private ProxyObjectFactory proxyObjectFactory;

    /******************************************************************************************
     *                                                                                        *
     *                 1: Pool initialize and Pooled object create/remove methods(4)          *
     *                                                                                        *
     ******************************************************************************************/

    /**
     * Method-1.1: initialize pool with configuration
     *
     * @param config data source configuration
     * @throws BeeObjectException check configuration fail or to create initiated object
     */
    public void init(BeeObjectSourceConfig config) throws BeeObjectException {
        if (poolState.get() == POOL_UNINIT) {
            if (config == null) throw new BeeObjectException("Configuration can't be null");
            poolConfig = config.check();//why need a copy here?
            poolName = poolConfig.getPoolName();

            commonLog.info("BeeOP({})starting....", poolName);
            poolMaxSize = poolConfig.getMaxActive();
            objectFactory = poolConfig.getObjectFactory();
            validTestTimeout = poolConfig.getValidTestTimeout();
            createProperties = poolConfig.getCreateProperties();
            excludeMethodNames = poolConfig.getExcludeMethodNames();

            idleTimeoutMs = poolConfig.getIdleTimeout();
            holdTimeoutMs = poolConfig.getHoldTimeout();
            maxWaitNs = MILLISECONDS.toNanos(poolConfig.getMaxWait());
            delayTimeForNextClearNanos = MILLISECONDS.toNanos(poolConfig.getDelayTimeForNextClear());
            validAssumeTime = poolConfig.getValidAssumeTime();
            if (poolConfig.isFairMode()) {
                poolMode = "fair";
                transferPolicy = new FairTransferPolicy();
            } else {
                poolMode = "compete";
                transferPolicy = new CompeteTransferPolicy();
            }

            printRuntimeLog = poolConfig.isPrintRuntimeLog();
            unCatchStateCode = transferPolicy.getCheckStateCode();
            Class[] objectInterfaces = poolConfig.getObjectInterfaces();
            if (objectInterfaces != null && objectInterfaces.length > 0) {
                proxyObjectFactory = new InvocationProxyObjectFactory(poolConfig.getObjectInterfaces());
            } else {
                proxyObjectFactory = new NullProxyObjectFactory();
            }

            semaphoreSize = poolConfig.getBorrowSemaphoreSize();
            semaphore = new PoolSemaphore(semaphoreSize, poolConfig.isFairMode());
            createInitObjects(poolConfig.getInitialSize());

            exitHook = new ObjectPoolHook();
            Runtime.getRuntime().addShutdownHook(exitHook);
            registerJmx();
            commonLog.info("BeeOP({})has startup{mode:{},init size:{},max size:{},semaphore size:{},max wait:{}ms",
                    poolName,
                    poolMode,
                    poolEntryArray.length,
                    config.getMaxActive(),
                    semaphoreSize,
                    poolConfig.getMaxWait());

            this.setDaemon(true);
            this.setName(poolName + "-workServant");
            this.setPriority(Thread.MIN_PRIORITY);
            this.start();
            idleScanThread.setDaemon(true);
            idleScanThread.setName(poolName + "-idleCheck");
            idleScanThread.start();
            poolState.set(POOL_NORMAL);
        } else {
            throw new BeeObjectException("Pool has initialized");
        }
    }

    /**
     * Method-1.2: create specified size objects to pool,if zero,then try to create one
     *
     * @throws BeeObjectException error occurred in creating objects
     */
    private void createInitObjects(int initSize) throws BeeObjectException {
        try {
            int size = (initSize > 0) ? initSize : 1;
            for (int i = 0; i < size; i++)
                createPooledEntry(OBJECT_IDLE);
        } catch (Throwable e) {
            for (PooledEntry pooledEntry : poolEntryArray)
                removePooledEntry(pooledEntry, DESC_RM_INIT);
            if (initSize > 0)
                throw e instanceof BeeObjectException ? (BeeObjectException) e : new BeeObjectException(e);
        }
    }

    //Method-1.3:create one pooled object
    private synchronized final PooledEntry createPooledEntry(int conState) throws BeeObjectException {
        int l = poolEntryArray.length;
        if (l < poolMaxSize) {
            if (printRuntimeLog)
                commonLog.info("BeeOP({}))begin to create a new pooled object,state:{}", poolName, conState);

            Object rawObj = null;
            try {
                rawObj = objectFactory.create(this.createProperties);
                objectFactory.setDefault(rawObj);
            } catch (Throwable e) {
                if (rawObj != null) objectFactory.destroy(rawObj);
                throw e instanceof BeeObjectException ? (BeeObjectException) e : new BeeObjectException(e);
            }

            PooledEntry pooledEntry = new PooledEntry(rawObj, conState, this, objectFactory);// registerStatement
            if (printRuntimeLog)
                commonLog.info("BeeOP({}))has created a new pooled object:{},state:{}", poolName, pooledEntry, conState);
            PooledEntry[] arrayNew = new PooledEntry[l + 1];
            arraycopy(poolEntryArray, 0, arrayNew, 0, l);
            arrayNew[l] = pooledEntry;// tail
            poolEntryArray = arrayNew;
            return pooledEntry;
        } else {
            return null;
        }
    }

    //Method-1.4: remove one pooled object
    private synchronized void removePooledEntry(PooledEntry p, String removeType) {
        if (printRuntimeLog)
            commonLog.info("BeeOP({}))begin to remove pooled object:{},reason:{}", poolName, p, removeType);

        p.onBeforeRemove();
        int l = poolEntryArray.length;
        PooledEntry[] arrayNew = new PooledEntry[l - 1];
        for (int i = 0; i < l; i++) {
            if (poolEntryArray[i] == p) {
                arraycopy(poolEntryArray, 0, arrayNew, 0, i);
                int m = l - i - 1;
                if (m > 0) arraycopy(poolEntryArray, i + 1, arrayNew, i, m);
                break;
            }
        }
        if (printRuntimeLog)
            commonLog.info("BeeOP({}))has removed pooled object:{},reason:{}", poolName, p, removeType);
        poolEntryArray = arrayNew;
    }

    /******************************************************************************************
     *                                                                                        *
     *                 2: Pooled object borrow and release methods(8)                            *
     *                                                                                        *
     ******************************************************************************************/

    /**
     * Method-2.1:borrow one object from pool,if search one idle object in pool,then try to catch it and return it
     * if not search,then wait until other borrowers release objects or wait timeout
     *
     * @return pooled object,
     * @throws BeeObjectException if pool is closed or waiting timeout,then throw exception
     */
    public final BeeObjectHandle getObject() throws BeeObjectException {
        if (poolState.get() != POOL_NORMAL) throw PoolCloseException;
        //0:try to get from threadLocal cache
        WeakReference<Borrower> r = (WeakReference) threadLocal.get();
        Borrower b = (r != null) ? (Borrower) r.get() : null;
        if (b != null) {
            PooledEntry p = b.lastUsed;
            if (p != null && p.state == OBJECT_IDLE && ObjStUpd.compareAndSet(p, OBJECT_IDLE, OBJECT_USING)) {
                if (testOnBorrow(p)) return createObjectHandle(p, b);
                b.lastUsed = null;
            }
        } else {
            b = new Borrower();
            threadLocal.set(new WeakReference<Borrower>(b));
        }

        long deadline = nanoTime();
        try {
            if (!semaphore.tryAcquire(maxWaitNs, NANOSECONDS))
                throw RequestTimeoutException;
        } catch (InterruptedException e) {
            throw RequestInterruptException;
        }
        try {//semaphore acquired
            //2:try search one or create one
            PooledEntry p = searchOrCreate();
            if (p != null) return createObjectHandle(p, b);

            //3:try to get one transferred one
            boolean failed = false;
            Throwable cause = null;
            deadline += maxWaitNs;
            Thread bth = b.thread;
            b.state = BOWER_NORMAL;
            waitQueue.offer(b);
            int spinSize = (waitQueue.peek() == b) ? maxTimedSpins : 0;

            do {
                Object s = b.state;
                if (s instanceof PooledEntry) {
                    p = (PooledEntry) s;
                    if (transferPolicy.tryCatch(p) && testOnBorrow(p)) {
                        waitQueue.remove(b);
                        return createObjectHandle(p, b);
                    }
                } else if (s instanceof Throwable) {
                    waitQueue.remove(b);
                    throw s instanceof BeeObjectException ? (BeeObjectException) s : new BeeObjectException((Throwable) s);
                }
                if (failed) {
                    BorrowStUpd.compareAndSet(b, s, cause);
                } else if (s instanceof PooledEntry) {
                    b.state = BOWER_NORMAL;
                    yield();
                } else {//here:(state == BOWER_NORMAL)
                    long t = deadline - nanoTime();
                    if (t > spinForTimeoutThreshold) {
                        if (spinSize > 0) {
                            spinSize--;
                        } else if (BorrowStUpd.compareAndSet(b, BOWER_NORMAL, BOWER_WAITING)) {
                            if (servantThreadTryCount.get() > 0 && servantThreadState.get() == THREAD_WAITING && servantThreadState.compareAndSet(THREAD_WAITING, THREAD_WORKING))
                                unpark(this);//wakeup servant thread

                            parkNanos(t);//block borrower thread
                            if (bth.isInterrupted()) {
                                failed = true;
                                cause = RequestInterruptException;
                            }
                            if (b.state == BOWER_WAITING)
                                BorrowStUpd.compareAndSet(b, BOWER_WAITING, failed ? cause : BOWER_NORMAL);//reset to normal
                        }
                    } else if (t <= 0) {//timeout
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
    private final PooledEntry searchOrCreate() throws BeeObjectException {
        PooledEntry[] array = poolEntryArray;
        int l = array.length;
        for (int i = 0; i < l; ++i) {
            PooledEntry p = array[i];
            if (p.state == OBJECT_IDLE && ObjStUpd.compareAndSet(p, OBJECT_IDLE, OBJECT_USING) && testOnBorrow(p))
                return p;
        }
        if (poolEntryArray.length < poolMaxSize)
            return createPooledEntry(OBJECT_USING);
        return null;
    }

    //Method-2.3: try to wakeup servant thread to work if it waiting
    private final void tryWakeupServantThread() {
        int c;
        do {
            c = servantThreadTryCount.get();
            if (c >= poolMaxSize) return;
        } while (!servantThreadTryCount.compareAndSet(c, c + 1));
        if (!waitQueue.isEmpty() && servantThreadState.get() == THREAD_WAITING && servantThreadState.compareAndSet(THREAD_WAITING, THREAD_WORKING))
            unpark(this);
    }

    //Method-2.4: return object to pool after borrower end of use object
    public final void recycle(PooledEntry p) {
        Iterator<Borrower> iterator = waitQueue.iterator();
        transferPolicy.beforeTransfer(p);
        W:
        while (iterator.hasNext()) {
            Borrower b = (Borrower) iterator.next();
            Object state;
            do {
                if (p.state != unCatchStateCode) return;
                state = b.state;
                if (!(state instanceof BorrowerState)) continue W;
            } while (!BorrowStUpd.compareAndSet(b, state, p));
            if (state == BOWER_WAITING) LockSupport.unpark(b.thread);
            return;
        }
        transferPolicy.onTransferFail(p);
        tryWakeupServantThread();
    }

    /**
     * Method-2.5:when object create failed,creator thread will transfer caused exception to one waiting borrower,
     * which will exit wait and throw this exception.
     *
     * @param e: transfer Exception to waiter
     */
    private void transferException(Throwable e) {
        final Iterator<Borrower> iterator = waitQueue.iterator();
        W:
        while (iterator.hasNext()) {
            Borrower b = (Borrower) iterator.next();
            Object state;
            do {
                state = b.state;
                if (!(state instanceof BorrowerState)) continue W;
            } while (!BorrowStUpd.compareAndSet(b, state, e));
            if (state == BOWER_WAITING) LockSupport.unpark(b.thread);
            return;
        }
    }

    //Method-2.6: remove object when exception occur in return
    public void abandonOnReturn(PooledEntry p) {
        removePooledEntry(p, DESC_RM_BAD);
        tryWakeupServantThread();
    }

    //Method-2.7: check object alive state,if not alive then remove it from pool
    private final boolean testOnBorrow(PooledEntry p) {
        if (currentTimeMillis() - p.lastAccessTime > validAssumeTime && !objectFactory.isValid(p, validTestTimeout)) {
            removePooledEntry(p, DESC_RM_BAD);
            tryWakeupServantThread();
            return false;
        } else {
            return true;
        }
    }

    /**
     * Method-2.8: create one wrapper around one borrowed object.
     *
     * @param p borrowed pooled object
     * @param b
     * @return object handle
     */
    private final BeeObjectHandle createObjectHandle(PooledEntry p, Borrower b) {
        ObjectHandle handle = new ObjectHandle(p, excludeMethodNames);
        b.lastUsed = p;
        p.objectHandle = handle;

        try {
            handle.setProxyObject(proxyObjectFactory.create(p, handle, excludeMethodNames));
        } catch (Throwable e) {
            if (printRuntimeLog)
                commonLog.warn("BeeOP({})failed to create reflect proxy instance:", poolName, e);
        }
        return handle;
    }

    /******************************************************************************************
     *                                                                                        *
     *              3: Pooled object idle-timeout/hold-timeout scan methods                   *
     *                                                                                        *
     ******************************************************************************************/

    //Method-3.1: check whether exists borrows under semaphore
    private final boolean existBorrower() {
        return semaphoreSize > semaphore.availablePermits();
    }

    //Method-3.2 shutdown two work threads in pool
    private void shutdownPoolThread() {
        int curState = servantThreadState.get();
        servantThreadState.set(THREAD_EXIT);
        if (curState == THREAD_WAITING) unpark(this);

        curState = idleScanThreadState.get();
        idleScanThreadState.set(THREAD_EXIT);
        if (curState == THREAD_WAITING) unpark(idleScanThread);
    }

    //Method-3.3: pool servant thread run method
    public void run() {
        while (poolState.get() != POOL_CLOSED) {
            while (servantThreadState.get() == THREAD_WORKING && servantThreadTryCount.get() > 0) {
                try {
                    servantThreadTryCount.decrementAndGet();
                    PooledEntry p = searchOrCreate();
                    if (p != null) recycle(p);
                } catch (Throwable e) {
                    transferException(e);
                }
            }

            servantThreadTryCount.set(0);
            if (servantThreadState.get() == THREAD_EXIT)
                break;
            if (servantThreadState.compareAndSet(THREAD_WORKING, THREAD_WAITING))
                park();
        }
    }

    /**
     * Method-3.4: inner timer will call the method to clear some idle timeout objects
     * or dead objects,or long time not active objects in using state
     */
    private void closeIdleTimeoutPooledEntry() {
        if (poolState.get() == POOL_NORMAL) {
            PooledEntry[] array = poolEntryArray;
            for (int i = 0, len = array.length; i < len; i++) {
                PooledEntry p = array[i];
                int state = p.state;
                if (state == OBJECT_IDLE && !existBorrower()) {
                    boolean isTimeoutInIdle = currentTimeMillis() - p.lastAccessTime - idleTimeoutMs >= 0L;
                    if (isTimeoutInIdle && ObjStUpd.compareAndSet(p, state, OBJECT_CLOSED)) {//need close idle
                        removePooledEntry(p, DESC_RM_IDLE);
                        tryWakeupServantThread();
                    }
                } else if (state == OBJECT_USING) {
                    if (currentTimeMillis() - p.lastAccessTime - holdTimeoutMs >= 0L) {//hold timeout
                        BeeObjectHandle proxyHandle = p.objectHandle;
                        if (proxyHandle != null) {
                            tryClosedProxyHandle(proxyHandle);
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

            PoolMonitorVo vo = this.getMonitorVo();
            if (printRuntimeLog)
                commonLog.info("BeeOP({})idle:{},using:{},semaphore-waiter:{},wait-transfer:{}", poolName, vo.getIdleSize(), vo.getUsingSize(), vo.getSemaphoreWaiterSize(), vo.getTransferWaiterSize());
        }
    }


    /******************************************************************************************
     *                                                                                        *
     *                       4: Pool clear/close methods                                      *
     *                                                                                        *
     ******************************************************************************************/

    //Method-4.1: remove all connections from pool
    public void clearAllObjects() {
        clearAllObjects(false);
    }

    //Method-4.2: remove all connections from pool
    public void clearAllObjects(boolean force) {
        if (poolState.compareAndSet(POOL_NORMAL, POOL_CLEARING)) {
            commonLog.info("BeeOP({})begin to remove objects", poolName);
            clearAllObjects(force, DESC_RM_CLEAR);
            poolState.set(POOL_NORMAL);// restore state;
            commonLog.info("BeeOP({})all objects were removed and restored to accept new requests", poolName);
        }
    }

    //Method-4.3: remove all connections from pool
    public void clearAllObjects(boolean force, String source) {
        semaphore.interruptWaitingThreads();
        while (!waitQueue.isEmpty()) transferException(PoolCloseException);

        while (poolEntryArray.length > 0) {
            PooledEntry[] array = poolEntryArray;
            for (int i = 0, len = array.length; i < len; i++) {
                PooledEntry pooledEntry = array[i];
                if (ObjStUpd.compareAndSet(pooledEntry, OBJECT_IDLE, OBJECT_CLOSED)) {
                    removePooledEntry(pooledEntry, source);
                } else if (pooledEntry.state == OBJECT_CLOSED) {
                    removePooledEntry(pooledEntry, source);
                } else if (pooledEntry.state == OBJECT_USING) {
                    BeeObjectHandle proxyHandle = pooledEntry.objectHandle;
                    if (proxyHandle != null) {
                        if (force || currentTimeMillis() - pooledEntry.lastAccessTime - holdTimeoutMs >= 0L)
                            tryClosedProxyHandle(proxyHandle);
                    } else {
                        removePooledEntry(pooledEntry, source);
                    }
                }
            } // for
            if (poolEntryArray.length > 0) parkNanos(delayTimeForNextClearNanos);
        } // while
    }

    //Method-4.4: closed check
    public boolean isClosed() {
        return poolState.get() == POOL_CLOSED;
    }

    // Method-4.5: close pool
    public void close() throws BeeObjectException {
        do {
            int poolStateCode = poolState.get();
            if ((poolStateCode == POOL_UNINIT || poolStateCode == POOL_NORMAL) && poolState.compareAndSet(poolStateCode, POOL_CLOSED)) {
                commonLog.info("BeeOP({})begin to shutdown", poolName);
                clearAllObjects(poolConfig.isForceCloseUsingOnClear(), DESC_RM_DESTROY);
                unregisterJmx();
                shutdownPoolThread();

                try {
                    Runtime.getRuntime().removeShutdownHook(exitHook);
                } catch (Throwable e) {
                }
                commonLog.info("BeeOP({})has shutdown", poolName);
                break;
            } else if (poolState.get() == POOL_CLOSED) {
                break;
            } else {
                parkNanos(delayTimeForNextClearNanos);// default wait 3 seconds
            }
        } while (true);
    }


    /******************************************************************************************
     *                                                                                        *
     *                        5: Pool monitor/jmx methods                                     *
     *                                                                                        *
     ******************************************************************************************/

    //Method-5.1: pool monitor vo
    public PoolMonitorVo getMonitorVo() {
        int totSize = getTotalSize();
        int idleSize = getIdleSize();
        monitorVo.setPoolName(poolName);
        monitorVo.setPoolMode(poolMode);
        monitorVo.setPoolState(poolState.get());
        monitorVo.setMaxActive(poolMaxSize);
        monitorVo.setIdleSize(idleSize);
        monitorVo.setUsingSize(totSize - idleSize);
        monitorVo.setSemaphoreWaiterSize(getSemaphoreWaitingSize());
        monitorVo.setTransferWaiterSize(getTransferWaitingSize());
        return monitorVo;
    }

    public int getTotalSize() {
        return poolEntryArray.length;
    }

    public int getIdleSize() {
        int idleSize = 0;
        PooledEntry[] array = poolEntryArray;
        for (int i = 0, l = array.length; i < l; i++)
            if (array[i].state == OBJECT_IDLE) idleSize++;
        return idleSize;
    }

    public int getUsingSize() {
        int active = poolEntryArray.length - getIdleSize();
        return (active > 0) ? active : 0;
    }

    public int getSemaphoreAcquiredSize() {
        return poolConfig.getBorrowSemaphoreSize() - semaphore.availablePermits();
    }

    public int getSemaphoreWaitingSize() {
        return semaphore.getQueueLength();
    }

    public int getTransferWaitingSize() {
        int size = 0;
        Iterator<Borrower> iterator = waitQueue.iterator();
        while (iterator.hasNext()) {
            Borrower borrower = iterator.next();
            if (borrower.state instanceof BorrowerState) size++;
        }
        return size;
    }

    //set pool info debug switch
    public void setPrintRuntimeLog(boolean indicator) {
        this.printRuntimeLog = indicator;
    }

    private void registerJmx() {
        if (poolConfig.isEnableJmx()) {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            registerJmxBean(mBeanServer, String.format(" cn.beeop.pool.FastObjectPool:type=BeeOP(%s)", poolName), this);
            registerJmxBean(mBeanServer, String.format("cn.beeop.BeeObjectSourceConfig:type=BeeOP(%s)-config", poolName), poolConfig);
        }
    }

    private void registerJmxBean(MBeanServer mBeanServer, String regName, Object bean) {
        try {
            ObjectName jmxRegName = new ObjectName(regName);
            if (!mBeanServer.isRegistered(jmxRegName)) {
                mBeanServer.registerMBean(bean, jmxRegName);
            }
        } catch (Exception e) {
            commonLog.warn("BeeOP({})failed to register jmx-bean:{}", poolName, regName, e);
        }
    }

    private void unregisterJmx() {
        if (poolConfig.isEnableJmx()) {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            unregisterJmxBean(mBeanServer, String.format(" cn.beeop.pool.FastObjectPool:type=BeeOP(%s)", poolName));
            unregisterJmxBean(mBeanServer, String.format("cn.beeop.BeeObjectSourceConfig:type=BeeOP(%s)-config", poolName));
        }
    }

    private void unregisterJmxBean(MBeanServer mBeanServer, String regName) {
        try {
            ObjectName jmxRegName = new ObjectName(regName);
            if (mBeanServer.isRegistered(jmxRegName)) {
                mBeanServer.unregisterMBean(jmxRegName);
            }
        } catch (Exception e) {
            commonLog.warn("BeeOP({})failed to unregister jmx-bean:{}", poolName, regName, e);
        }
    }


    /******************************************************************************************
     *                                                                                        *
     *                        6: Pool some inner classes                                      *
     *                                                                                        *
     ******************************************************************************************/

    // Transfer Policy
    static interface TransferPolicy {
        int getCheckStateCode();

        void beforeTransfer(PooledEntry p);

        boolean tryCatch(PooledEntry p);

        void onTransferFail(PooledEntry p);
    }

    static final class CompeteTransferPolicy implements TransferPolicy {
        public final int getCheckStateCode() {
            return OBJECT_IDLE;
        }

        public final void beforeTransfer(PooledEntry p) {
            p.state = OBJECT_IDLE;
        }

        public final boolean tryCatch(PooledEntry p) {
            return ObjStUpd.compareAndSet(p, OBJECT_IDLE, OBJECT_USING);
        }

        public final void onTransferFail(PooledEntry p) {
        }
    }

    static final class FairTransferPolicy implements TransferPolicy {
        public final int getCheckStateCode() {
            return OBJECT_USING;
        }

        public final void beforeTransfer(PooledEntry p) {
        }

        public final boolean tryCatch(PooledEntry p) {
            return p.state == OBJECT_USING;
        }

        public final void onTransferFail(PooledEntry p) {
            p.state = OBJECT_IDLE;
        }

    }

    private static final class PoolSemaphore extends Semaphore {
        public PoolSemaphore(int permits, boolean fair) {
            super(permits, fair);
        }

        public void interruptWaitingThreads() {
            Iterator<Thread> iterator = super.getQueuedThreads().iterator();
            while (iterator.hasNext()) {
                Thread thread = iterator.next();
                State state = thread.getState();
                if (state == State.WAITING || state == State.TIMED_WAITING) {
                    thread.interrupt();
                }
            }
        }
    }

    //create pooled connection by asyn
    private static final class IdleTimeoutScanThread extends Thread {
        private FastObjectPool pool;

        public IdleTimeoutScanThread(FastObjectPool pool) {
            this.pool = pool;
        }

        public void run() {
            final long checkTimeIntervalNanos = MILLISECONDS.toNanos(pool.poolConfig.getTimerCheckInterval());
            final AtomicInteger idleScanThreadState = pool.idleScanThreadState;
            while (idleScanThreadState.get() == THREAD_WORKING) {
                parkNanos(checkTimeIntervalNanos);
                try {
                    pool.closeIdleTimeoutPooledEntry();
                } catch (Throwable e) {
                }
            }
        }
    }

    /**
     * Hook when JVM exit
     */
    private class ObjectPoolHook extends Thread {
        public void run() {
            try {
                commonLog.info("ObjectPoolHook Running");
                FastObjectPool.this.close();
            } catch (Throwable e) {
                commonLog.error("Error at closing object pool,cause:", e);
            }
        }
    }
}
