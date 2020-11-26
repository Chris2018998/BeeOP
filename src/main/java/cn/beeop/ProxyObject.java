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

import java.sql.SQLException;

/**
 * Object Proxy
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class ProxyObject {
    protected Object delegate;
    protected PooledEntry pConn;//called by subclass to update time
    private boolean isClosed;

    public ProxyObject(PooledEntry pConn) {
        this.pConn = pConn;
        pConn.proxyConn = this;
        this.delegate = pConn.rawConn;
    }

    public Object getDelegate() throws SQLException {
        checkClosed();
        return delegate;
    }

    public boolean isClosed() throws SQLException {
        return isClosed;
    }

    protected void checkClosed() throws SQLException {
        // if (isClosed) throw ConnectionClosedException;
    }

    public final void close() throws SQLException {
        synchronized (this) {//safe close
            if (isClosed) return;
            isClosed = true;
            //delegate = CLOSED_CON;
        }
        pConn.recycleSelf();
    }

    final void trySetAsClosed() {//called from FastConnectionPool
        try {
            close();
        } catch (SQLException e) {
        }
    }
}
