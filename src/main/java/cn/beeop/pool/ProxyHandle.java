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
package cn.beeop.pool;

import cn.beeop.BeeObjectException;

/**
 * raw object proxy handle interface
 *
 * @author Chris.Liao
 * @version 1.0
 */
public interface ProxyHandle {

    /**
     * return raw object to pool and remark proxy object as closed
     *
     * @throws BeeObjectException
     */
    public void close() throws BeeObjectException;

    /**
     * query proxy whether in closed state
     *
     * @return proxy state
     * @throws BeeObjectException
     */
    public boolean isClosed() throws BeeObjectException;

}
