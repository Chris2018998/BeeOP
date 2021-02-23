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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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
    //POOLED OBJECT STATE
    public static final int OBJECT_IDLE = 1;
    public static final int OBJECT_USING = 2;
    public static final int OBJECT_CLOSED = 3;
    //ADD OBJECT THREAD STATE
    public static final int THREAD_WORKING = 1;
    public static final int THREAD_WAITING = 2;
    public static final int THREAD_DEAD = 3;
    //BORROWER STATE
    public static final Object BORROWER_NORMAL = new Object();
    public static final Object BORROWER_WAITING = new Object();
    public static final BeeObjectException RequestTimeoutException = new BeeObjectException("Request timeout");
    public static final BeeObjectException RequestInterruptException = new BeeObjectException("Request interrupted");
    public static final BeeObjectException PoolCloseException = new BeeObjectException("Pool has shut down or in clearing");
    public static final BeeObjectException ObjectClosedException = new BeeObjectException("No operations allowed after object handle closed");
    public static final BeeObjectException ObjectMethodForbiddenException = new BeeObjectException("Method illegal access");
    public static final Class[] EmptyParamTypes = new Class[0];
    public static final Object[] EmptyParamValues = new Object[0];
    public static final Logger commonLog = LoggerFactory.getLogger(StaticCenter.class);
    static final ConcurrentHashMap<Object, Method> ObjectMethodMap = new ConcurrentHashMap<Object, Method>();

    static final Object genMethodCacheKey(String name, Class[] types) {
        return new MethodKey(name, types);
    }

    public static final boolean equals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    public static final boolean isBlank(String str) {
        if (str == null) return true;
        int strLen = str.length();
        for (int i = 0; i < strLen; ++i) {
            if (!Character.isWhitespace(str.charAt(i)))
                return false;
        }
        return true;
    }

    static final class MethodKey {
        private String name;
        private Class[] types;

        public MethodKey(String name, Class[] types) {
            this.name = name;
            this.types = types;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MethodKey that = (MethodKey) o;
            return name.equals(that.name) &&
                    Arrays.equals(types, that.types);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(name);
            result = 31 * result + Arrays.hashCode(types);
            return result;
        }
    }
}


