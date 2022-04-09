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
     * return raw object to pool and remark statement object as closed
     *
     * @throws Exception if access error occurs
     */
    void close() throws Exception;

    /**
     * query statement is whether closed
     *
     * @return statement state
     * @throws Exception if access error occurs
     */
    boolean isClosed() throws Exception;

    /*
     *  return object reflection statement to user,if 'objectInterfaces' not config,then return null
     *
     * @return statement instance
     * @throws Exception if access error occurs
     */
    Object getReflectProxy() throws Exception;

    /**
     * call raw object'method,which has empty parameter
     *
     * @param methodName method name
     * @return Invocation result of raw object
     * @throws Exception if access error occurs
     */
    Object call(String methodName) throws Exception;

    /**
     * call raw object'method
     *
     * @param methodName  method name
     * @param paramTypes  method parameter types
     * @param paramValues method parameter values
     * @return Invocation result of raw object
     * @throws Exception if access error occurs
     */
    Object call(String methodName, Class[] paramTypes, Object[] paramValues) throws Exception;

}
