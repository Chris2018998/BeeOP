/*
 * Copyright(C) Chris2018998
 * Contact:Chris2018998@tom.com
 *
 * Licensed under GNU Lesser General Public License v2.1
 */
package cn.beeop;

/**
 * object handle interface
 *
 * @author Chris.Liao
 * @version 1.0
 */
public interface BeeObjectHandle {

    /**
     * return raw object to pool and remark proxy object as closed
     *
     * @throws BeeObjectException if access error occurs
     */
    public void close() throws BeeObjectException;

    /**
     * query proxy whether in closed state
     *
     * @return proxy state
     * @throws BeeObjectException if access error occurs
     */
    public boolean isClosed() throws BeeObjectException;

    /*
     *  return object reflection proxy to user,if 'objectInterfaces' not config,then return null
     *
     * @return proxy instance
     * @throws BeeObjectException if access error occurs
     */
    public Object getProxyObject() throws BeeObjectException;

    /**
     * call raw object'method,which has empty parameter
     *
     * @param methodName method name
     * @return Invocation result of raw object
     * @throws BeeObjectException if access error occurs
     */
    public Object call(String methodName) throws BeeObjectException;

    /**
     * call raw object'method
     *
     * @param methodName  method name
     * @param paramTypes  method parameter types
     * @param paramValues method parameter values
     * @return Invocation result of raw object
     * @throws BeeObjectException if access error occurs
     */
    public Object call(String methodName, Class[] paramTypes, Object[] paramValues) throws BeeObjectException;

}
