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

    void close() throws Exception;

    boolean isClosed() throws Exception;

    Object getObjectProxy() throws Exception;

    Object call(String methodName) throws Exception;

    Object call(String methodName, Class[] paramTypes, Object[] paramValues) throws Exception;
}
