/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU General Public License version 3.0.
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static cn.beeop.pool.StaticCenter.*;
import static java.lang.System.*;
import static java.util.concurrent.TimeUnit.*;
import static java.util.concurrent.locks.LockSupport.parkNanos;
import static java.util.concurrent.locks.LockSupport.unpark;

/**
 * Object Pool Implementation
 *
 * @author Chris.Liao
 * @version 1.0
 */
public final class FastPool extends Thread implements PoolJmxBean, ObjectPool {
    private static final long spinForTimeoutThreshold = 1000L;
    private static final int maxTimedSpins = (Runtime.getRuntime().availableProcessors() < 2) ? 0 : 32;
    private static final AtomicIntegerFieldUpdater<PooledEntry> ObjStUpd = AtomicIntegerFieldUpdater.newUpdater(PooledEntry.class, "state");
    private static final AtomicReferenceFieldUpdater<Borrower, Object> BorrowStUpd = AtomicReferenceFieldUpdater.newUpdater(Borrower.class, Object.class, "state");
    private static final String DESC_RM_INIT = "init";
    private static final String DESC_RM_BAD = "bad";
    private static final String DESC_RM_IDLE = "idle";
    private static final String DESC_RM_CLOSED = "closed";
    private static final String DESC_RM_CLEAR = "clear";
    private static final String DESC_RM_DESTROY = "destroy";
    private final Object connArrayLock = new Object();
    private final ConcurrentLinkedQueue<Borrower> waitQueue = new ConcurrentLinkedQueue<Borrower>();
    private final ThreadLocal<WeakReference<Borrower>> threadLocal = new ThreadLocal<WeakReference<Borrower>>();
    private final PoolMonitorVo monitorVo = new PoolMonitorVo();
    private int PoolMaxSize;
    private long MaxWaitNanos;//nanoseconds
    private int UnCatchStateCode;
    private int ObjectTestTimeout;//seconds
    private long ObjectTestInterval;//milliseconds
    private long DelayTimeForNextClearNanos;//nanoseconds
    private ObjectPoolHook exitHook;
    private BeeObjectSourceConfig poolConfig;
    private int semaphoreSize;
    private Semaphore semaphore;
    private TransferPolicy transferPolicy;
    private BeeObjectFactory objectFactory;
    private volatile PooledEntry[] poolEntryArray = new PooledEntry[0];
    private DynAddPooledEntryTask dynAddPooledEntryTask;
    private ThreadPoolExecutor poolTaskExecutor;
    private String poolName = "";
    private String poolMode = "";
    private AtomicInteger poolState = new AtomicInteger(POOL_UNINIT);
    private AtomicInteger needAddEntrySize = new AtomicInteger(0);
    private AtomicInteger idleThreadState = new AtomicInteger(THREAD_WORKING);

    private Properties createProperties;
    private Set<String> excludeMethodNames;
    private ReflectProxyFactory reflectProxyFactory;


    /******************************************************************************************
     *                                                                                        *
     *                 1: Pool initialize and Pooled object create/remove methods             *
     *                                                                                        *
     ******************************************************************************************/

    /**
     * initialize pool with configuration
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
            PoolMaxSize = poolConfig.getMaxActive();
            objectFactory = poolConfig.getObjectFactory();
            ObjectTestTimeout = poolConfig.getObjectTestTimeout();
            createProperties = poolConfig.getCreateProperties();
            excludeMethodNames = poolConfig.getExcludeMethodNames();

            MaxWaitNanos = MILLISECONDS.toNanos(poolConfig.getMaxWait());
            DelayTimeForNextClearNanos = MILLISECONDS.toNanos(poolConfig.getDelayTimeForNextClear());
            ObjectTestInterval = poolConfig.getObjectTestInterval();
            if (poolConfig.isFairMode()) {
                poolMode = "fair";
                transferPolicy = new FairTransferPolicy();
            } else {
                poolMode = "compete";
                transferPolicy = new CompeteTransferPolicy();

            }

            UnCatchStateCode = transferPolicy.getCheckStateCode();
            Class[] objectInterfaces = poolConfig.getObjectInterfaces();
            if (objectInterfaces != null && objectInterfaces.length > 0) {
                reflectProxyFactory = new InvocationProxyFactory(poolConfig.getObjectInterfaces());
            } else {
                reflectProxyFactory = new NullReflectProxyFactory();
            }

            semaphoreSize = poolConfig.getBorrowSemaphoreSize();
            semaphore = new Semaphore(semaphoreSize, poolConfig.isFairMode());
            dynAddPooledEntryTask = new DynAddPooledEntryTask();
            poolTaskExecutor = new ThreadPoolExecutor(1, 1, 5, SECONDS, new LinkedBlockingQueue<Runnable>(), new PoolThreadThreadFactory("PoolTaskThread"));
            poolTaskExecutor.allowCoreThreadTimeOut(true);
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
            poolState.set(POOL_NORMAL);

            this.setName("IdleTimeoutScanThread");
            this.setDaemon(true);
            this.start();
        } else {
            throw new BeeObjectException("Pool has initialized");
        }
    }

    /**
     * create specified size objects to pool,if zero,then try to create one
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
            if (e instanceof ObjectCreateFailedException) {//may be network bad or database is not ready
                if (initSize > 0) throw e;
            } else {
                throw e;
            }
        }
    }

    //create one pooled object
    private final PooledEntry createPooledEntry(int conState) throws BeeObjectException {
        synchronized (connArrayLock) {
            int arrayLen = poolEntryArray.length;
            if (arrayLen < PoolMaxSize) {
                if (isDebugEnabled)
                    commonLog.debug("BeeOP({}))begin to create a new pooled object,state:{}", poolName, conState);
                Object con = null;
                try {
                    con = objectFactory.create(this.createProperties);
                } catch (Throwable e) {
                    throw new ObjectCreateFailedException(e);
                }
                try {
                    objectFactory.setDefault(con);
                } catch (Throwable e) {
                    objectFactory.destroy(con);
                    throw e;
                }

                PooledEntry pooledEntry = new PooledEntry(con, conState, this, objectFactory);// registerStatement
                if (isDebugEnabled)
                    commonLog.debug("BeeOP({}))has created a new pooled object:{},state:{}", poolName, pooledEntry, conState);
                PooledEntry[] arrayNew = new PooledEntry[arrayLen + 1];
                arraycopy(poolEntryArray, 0, arrayNew, 0, arrayLen);
                arrayNew[arrayLen] = pooledEntry;// tail
                poolEntryArray = arrayNew;
                return pooledEntry;
            } else {
                return null;
            }
        }
    }

    //remove one pooled object
    private void removePooledEntry(PooledEntry pooledEntry, String removeType) {
        if (isDebugEnabled)
            commonLog.debug("BeeOP({}))begin to remove pooled object:{},reason:{}", poolName, pooledEntry, removeType);

        pooledEntry.onBeforeRemove();
        synchronized (connArrayLock) {
            int oLen = poolEntryArray.length;
            PooledEntry[] arrayNew = new PooledEntry[oLen - 1];
            for (int i = 0; i < oLen; i++) {
                if (poolEntryArray[i] == pooledEntry) {
                    arraycopy(poolEntryArray, 0, arrayNew, 0, i);
                    int m = oLen - i - 1;
                    if (m > 0) arraycopy(poolEntryArray, i + 1, arrayNew, i, m);
                    break;
                }
            }
            if (isDebugEnabled)
                commonLog.debug("BeeOP({}))has removed pooled object:{},reason:{}", poolName, pooledEntry, removeType);
            poolEntryArray = arrayNew;
        }
    }

    // notify creator thread to add one object to pool
    private void tryToCreateNewPoolEntryByAsyn() {
        int curAddSize, updAddSize;
        do {
            curAddSize = needAddEntrySize.get();
            updAddSize = curAddSize + 1;
            if (poolEntryArray.length + updAddSize > PoolMaxSize) return;
            if (needAddEntrySize.compareAndSet(curAddSize, updAddSize)) {
                poolTaskExecutor.submit(dynAddPooledEntryTask);
                return;
            }
        } while (true);
    }

    ThreadPoolExecutor getNetworkTimeoutExecutor() {
        return poolTaskExecutor;
    }

    /******************************************************************************************
     *                                                                                        *
     *                 2: Pooled object borrow and release methods                            *
     *                                                                                        *
     ******************************************************************************************/

    /**
     * borrow one object from pool,if search one idle object in pool,then try to catch it and return it
     * if not search,then wait until other borrowers release objects or wait timeout
     *
     * @return pooled object,
     * @throws BeeObjectException if pool is closed or waiting timeout,then throw exception
     */
    public final BeeObjectHandle getObject() throws BeeObjectException {
        if (poolState.get() != POOL_NORMAL) throw PoolCloseException;
        //0:try to get from threadLocal cache
        WeakReference<Borrower> ref = threadLocal.get();
        Borrower borrower = (ref != null) ? ref.get() : null;
        if (borrower != null) {
            PooledEntry pooledEntry = borrower.lastUsedEntry;
            if (pooledEntry != null && pooledEntry.state == OBJECT_IDLE && ObjStUpd.compareAndSet(pooledEntry, OBJECT_IDLE, OBJECT_USING)) {
                if (testOnBorrow(pooledEntry)) return createObjectHandle(pooledEntry, borrower);
                borrower.lastUsedEntry = null;
            }
        } else {
            borrower = new Borrower();
            threadLocal.set(new WeakReference<Borrower>(borrower));
        }

        final long deadlineNanos = nanoTime() + MaxWaitNanos;
        try {
            if (!semaphore.tryAcquire(MaxWaitNanos, NANOSECONDS))
                throw RequestTimeoutException;
        } catch (InterruptedException e) {
            throw RequestInterruptException;
        }
        try {//semaphore acquired
            //1:try to search one from array
            PooledEntry[] array = poolEntryArray;
            int i = 0, l = array.length;
            PooledEntry pooledEntry;
            while (i < l) {
                pooledEntry = array[i++];
                if (pooledEntry.state == OBJECT_IDLE && ObjStUpd.compareAndSet(pooledEntry, OBJECT_IDLE, OBJECT_USING) && testOnBorrow(pooledEntry))
                    return createObjectHandle(pooledEntry, borrower);
            }
            //2:try to create one directly
            if (poolEntryArray.length < PoolMaxSize && (pooledEntry = createPooledEntry(OBJECT_USING)) != null)
                return createObjectHandle(pooledEntry, borrower);
            //3:try to get one transferred object
            boolean failed = false;
            BeeObjectException cause = null;
            Thread cth = borrower.thread;
            borrower.state = BOWER_NORMAL;
            waitQueue.offer(borrower);
            int spinSize = (waitQueue.peek() == borrower) ? maxTimedSpins : 0;
            do {
                Object state = borrower.state;
                if (state instanceof PooledEntry) {
                    pooledEntry = (PooledEntry) state;
                    if (transferPolicy.tryCatch(pooledEntry) && testOnBorrow(pooledEntry)) {
                        waitQueue.remove(borrower);
                        return createObjectHandle(pooledEntry, borrower);
                    }
                } else if (state instanceof BeeObjectException) {
                    waitQueue.remove(borrower);
                    throw (BeeObjectException) state;
                }
                if (failed) {
                    if (borrower.state == state)
                        BorrowStUpd.compareAndSet(borrower, state, cause);
                } else if (state instanceof PooledEntry) {
                    borrower.state = BOWER_NORMAL;
                    yield();
                } else {//here:(state == BOWER_NORMAL)
                    long timeout = deadlineNanos - nanoTime();
                    if (timeout > 0L) {
                        if (spinSize > 0) {
                            --spinSize;
                        } else if (borrower.state == BOWER_NORMAL && timeout > spinForTimeoutThreshold && BorrowStUpd.compareAndSet(borrower, BOWER_NORMAL, BOWER_WAITING)) {
                            parkNanos(timeout);
                            if (cth.isInterrupted()) {
                                failed = true;
                                cause = RequestInterruptException;
                            }
                            if (borrower.state == BOWER_WAITING)
                                BorrowStUpd.compareAndSet(borrower, BOWER_WAITING, failed ? cause : BOWER_NORMAL);//reset to normal
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

    /**
     * return object to pool after borrower end of use object
     *
     * @param pooledEntry target object need release
     */
    public final void recycle(PooledEntry pooledEntry) {
        transferPolicy.beforeTransfer(pooledEntry);
        Iterator<Borrower> iterator = waitQueue.iterator();
        W:
        while (iterator.hasNext()) {
            Borrower borrower = iterator.next();
            Object state;
            do {
                if (pooledEntry.state != UnCatchStateCode) return;
                state = borrower.state;
                if (!(state instanceof BorrowerState)) continue W;
            } while (!BorrowStUpd.compareAndSet(borrower, state, pooledEntry));
            if (state == BOWER_WAITING) unpark(borrower.thread);
            return;
        }//first while loop
        transferPolicy.onFailedTransfer(pooledEntry);
    }

    /**
     * when object create failed,creator thread will transfer caused exception to one waiting borrower,
     * which will exit wait and throw this exception.
     *
     * @param e: transfer Exception to waiter
     */
    private void transferException(BeeObjectException e) {
        Iterator<Borrower> iterator = waitQueue.iterator();
        W:
        while (iterator.hasNext()) {
            Borrower borrower = iterator.next();
            Object state;
            do {
                state = borrower.state;
                if (!(state instanceof BorrowerState)) continue W;
            } while (!BorrowStUpd.compareAndSet(borrower, state, e));
            if (state == BOWER_WAITING) unpark(borrower.thread);
            return;
        }//first while loop
    }

    /**
     * remove object when exception occur in return
     *
     * @param pooledEntry target object need release
     */
    public void abandonOnReturn(PooledEntry pooledEntry) {
        removePooledEntry(pooledEntry, DESC_RM_BAD);
        tryToCreateNewPoolEntryByAsyn();
    }

    /**
     * check object alive state,if not alive then remove it from pool
     *
     * @return object alive state,true,alive
     */
    private final boolean testOnBorrow(PooledEntry pooledEntry) {
        if (currentTimeMillis() - pooledEntry.lastAccessTime - ObjectTestInterval >= 0L && !objectFactory.isAlive(pooledEntry, ObjectTestTimeout)) {
            removePooledEntry(pooledEntry, DESC_RM_BAD);
            tryToCreateNewPoolEntryByAsyn();
            return false;
        } else {
            return true;
        }
    }

    /**
     * create one wrapper around one borrowed object.
     *
     * @param pEntry   borrowed pooled object
     * @param borrower
     * @return object handle
     */
    private final BeeObjectHandle createObjectHandle(PooledEntry pEntry, Borrower borrower) {
        ObjectHandle handle = new ObjectHandle(pEntry, excludeMethodNames);
        borrower.lastUsedEntry = pEntry;
        pEntry.objectHandle = handle;

        try {
            handle.setProxyObject(reflectProxyFactory.createProxyObject(pEntry, handle, excludeMethodNames));
        } catch (Throwable e) {
            commonLog.warn("BeeOP({})failed to create reflect proxy instance:", poolName, e);
        }

        return handle;
    }


    /******************************************************************************************
     *                                                                                        *
     *              3: Pooled object idle-timeout/hold-timeout scan methods                   *
     *                                                                                        *
     ******************************************************************************************/

    private final boolean existBorrower() {
        return semaphoreSize > semaphore.availablePermits();
    }

    private void shutdownIdleScanThread() {
        idleThreadState.set(THREAD_EXIT);
        unpark(this);
    }

    public void run() {
        final long CheckTimeIntervalNanos = MILLISECONDS.toNanos(poolConfig.getIdleCheckTimeInterval());
        do {
            if (idleThreadState.get() == THREAD_WORKING) {
                try {
                    parkNanos(CheckTimeIntervalNanos);
                    closeIdleTimeoutPooledEntry();
                } catch (Throwable e) {
                }
            } else {
                break;
            }
        } while (true);
    }

    /**
     * inner timer will call the method to clear some idle timeout objects
     * or dead objects,or long time not active objects in using state
     */
    private void closeIdleTimeoutPooledEntry() {
        if (poolState.get() == POOL_NORMAL) {
            PooledEntry[] array = poolEntryArray;
            for (int i = 0, len = array.length; i < len; i++) {
                PooledEntry pooledEntry = array[i];
                int state = pooledEntry.state;
                if (state == OBJECT_IDLE && !existBorrower()) {
                    boolean isTimeoutInIdle = (currentTimeMillis() - pooledEntry.lastAccessTime - poolConfig.getIdleTimeout() >= 0);
                    if (isTimeoutInIdle && ObjStUpd.compareAndSet(pooledEntry, state, OBJECT_CLOSED)) {//need close idle
                        removePooledEntry(pooledEntry, DESC_RM_IDLE);
                        tryToCreateNewPoolEntryByAsyn();
                    }
                } else if (state == OBJECT_USING) {
                    if (currentTimeMillis() - pooledEntry.lastAccessTime - poolConfig.getHoldTimeout() >= 0L) {//hold timeout
                        BeeObjectHandle proxyHandle = pooledEntry.objectHandle;
                        if (proxyHandle != null) {
                            tryClosedProxyHandle(proxyHandle);
                        } else {
                            removePooledEntry(pooledEntry, DESC_RM_BAD);
                            tryToCreateNewPoolEntryByAsyn();
                        }
                    }
                } else if (state == OBJECT_CLOSED) {
                    removePooledEntry(pooledEntry, DESC_RM_CLOSED);
                    tryToCreateNewPoolEntryByAsyn();
                }
            }

            PoolMonitorVo vo = this.getMonitorVo();
            if (isDebugEnabled)
                commonLog.debug("BeeOP({})idle:{},using:{},semaphore-waiter:{},wait-transfer:{}", poolName, vo.getIdleSize(), vo.getUsingSize(), vo.getSemaphoreWaiterSize(), vo.getTransferWaiterSize());
        }
    }


    /******************************************************************************************
     *                                                                                        *
     *                       4: Pool clear/close methods                                      *
     *                                                                                        *
     ******************************************************************************************/

    //close objects and remove them from pool
    public void clearAllObjects() {
        clearAllObjects(false);
    }

    //close objects and remove them from pool
    public void clearAllObjects(boolean force) {
        if (poolState.compareAndSet(POOL_NORMAL, POOL_CLEARING)) {
            commonLog.info("BeeOP({})begin to remove objects", poolName);
            clearAllObjects(force, DESC_RM_CLEAR);
            commonLog.info("BeeOP({})all objects were removed", poolName);
            poolState.set(POOL_NORMAL);// restore state;
            commonLog.info("BeeOP({})restore to accept new requests", poolName);
        }
    }

    // remove all objects
    public void clearAllObjects(boolean force, String source) {
        while (existBorrower()) {
            transferException(PoolCloseException);
        }
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
                        if (force || currentTimeMillis() - pooledEntry.lastAccessTime - poolConfig.getHoldTimeout() >= 0L)
                            tryClosedProxyHandle(proxyHandle);
                    } else {
                        removePooledEntry(pooledEntry, source);
                    }
                }
            } // for
            if (poolEntryArray.length > 0) parkNanos(DelayTimeForNextClearNanos);
        } // while
    }

    public boolean isClosed() {
        return poolState.get() == POOL_CLOSED;
    }

    public void close() throws BeeObjectException {
        do {
            int poolStateCode = poolState.get();
            if ((poolStateCode == POOL_UNINIT || poolStateCode == POOL_NORMAL) && poolState.compareAndSet(poolStateCode, POOL_CLOSED)) {
                commonLog.info("BeeOP({})begin to shutdown", poolName);
                clearAllObjects(poolConfig.isForceCloseUsingOnClear(), DESC_RM_DESTROY);
                unregisterJmx();
                shutdownIdleScanThread();
                poolTaskExecutor.getQueue().clear();
                poolTaskExecutor.shutdownNow();

                try {
                    Runtime.getRuntime().removeShutdownHook(exitHook);
                } catch (Throwable e) {
                }
                commonLog.info("BeeOP({})has shutdown", poolName);
                break;
            } else if (poolState.get() == POOL_CLOSED) {
                break;
            } else {
                parkNanos(DelayTimeForNextClearNanos);// default wait 3 seconds
            }
        } while (true);
    }


    /******************************************************************************************
     *                                                                                        *
     *                        5: Pool monitor/jmx methods                                     *
     *                                                                                        *
     ******************************************************************************************/

    public PoolMonitorVo getMonitorVo() {
        int totSize = getTotalSize();
        int idleSize = getIdleSize();
        monitorVo.setPoolName(poolName);
        monitorVo.setPoolMode(poolMode);
        monitorVo.setPoolState(poolState.get());
        monitorVo.setMaxActive(PoolMaxSize);
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

    private void registerJmx() {
        if (poolConfig.isEnableJmx()) {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            registerJmxBean(mBeanServer, String.format(" cn.beeop.pool.FastPool:type=BeeOP(%s)", poolName), this);
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
            unregisterJmxBean(mBeanServer, String.format(" cn.beeop.pool.FastPool:type=BeeOP(%s)", poolName));
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

        void onFailedTransfer(PooledEntry p);
    }

    static final class PoolThreadThreadFactory implements ThreadFactory {
        private String thName;

        public PoolThreadThreadFactory(String thName) {
            this.thName = thName;
        }

        public Thread newThread(Runnable r) {
            Thread th = new Thread(r, thName);
            th.setDaemon(true);
            return th;
        }
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

        public final void onFailedTransfer(PooledEntry p) {
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

        public final void onFailedTransfer(PooledEntry p) {
            p.state = OBJECT_IDLE;
        }

    }

    //create pooled object by asyn
    final class DynAddPooledEntryTask implements Runnable {
        // create object to pool
        public void run() {
            PooledEntry pooledEntry;
            while (needAddEntrySize.get() > 0) {
                needAddEntrySize.decrementAndGet();
                if (!waitQueue.isEmpty()) {
                    try {
                        pooledEntry = createPooledEntry(OBJECT_USING);
                        if (pooledEntry != null) recycle(pooledEntry);
                    } catch (Throwable e) {
                        transferException((e instanceof BeeObjectException) ? (BeeObjectException) e : new BeeObjectException(e));
                    }
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
                FastPool.this.close();
            } catch (Throwable e) {
                commonLog.error("Error at closing object pool,cause:", e);
            }
        }
    }
}
