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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pool Static Center
 *
 * @author Chris.Liao
 * @version 1.0
 */
public class StaticCenter {
    //POOL STATE
    public static final int POOL_UNINIT = 1;
    public static final int POOL_NORMAL = 2;
    public static final int POOL_CLOSED = 3;
    public static final int POOL_RESTING = 4;
    //POOLED CONNECTION STATE
    public static final int CONNECTION_IDLE = 1;
    public static final int CONNECTION_USING = 2;
    public static final int CONNECTION_CLOSED = 3;
    //ADD CONNECTION THREAD STATE
    public static final int THREAD_WORKING = 1;
    public static final int THREAD_WAITING = 2;
    public static final int THREAD_DEAD = 3;
    //BORROWER STATE
    public static final Object BORROWER_NORMAL = new Object();
    public static final Object BORROWER_WAITING = new Object();

    //Connection reset pos in array
    public static final int POS_AUTO = 0;
    public static final int POS_TRANS = 1;
    public static final int POS_READONLY = 2;
    public static final int POS_CATALOG = 3;
    public static final int POS_SCHEMA = 4;
    public static final int POS_NETWORK = 5;

    public static final ObjectException RequestTimeoutException = new ObjectException("Request timeout");
    public static final ObjectException RequestInterruptException = new ObjectException("Request interrupt");
    public static final ObjectException PoolCloseException = new ObjectException("Pool has been closed or in resetting");
    public static final ObjectException XaConnectionClosedException = new ObjectException("No operations allowed after connection closed.");
    public static final ObjectException ConnectionClosedException = new ObjectException("No operations allowed after connection closed.");
    public static final ObjectException StatementClosedException = new ObjectException("No operations allowed after statement closed.");
    public static final ObjectException ResultSetClosedException = new ObjectException("No operations allowed after resultSet closed.");
    public static final ObjectException AutoCommitChangeForbiddenException = new ObjectException("Execute 'commit' or 'rollback' before this operation");
    public static final ObjectException DriverNotSupportNetworkTimeoutException = new ObjectException("Driver not support 'networkTimeout'");
    public static final Logger commonLog = LoggerFactory.getLogger(StaticCenter.class);

    public static final boolean equals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    public static final boolean isBlank(String str) {
        if (str == null) return true;
        int strLen = str.length();
        for (int i = 0; i < strLen; ++i) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}