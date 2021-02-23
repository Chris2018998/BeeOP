/*
 * Copyright Chris2018998
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
