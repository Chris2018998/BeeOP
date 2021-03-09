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
package cn.beeop.test.config;

import cn.beeop.BeeObjectSourceConfig;
import cn.beeop.BeeObjectSourceConfigException;
import cn.beeop.test.TestCase;
import cn.beeop.test.object.JavaBook;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * @author Chris.Liao
 * @version 1.0
 */
public class ConfigCheckCopyTest extends TestCase {
    public void test() throws Exception {
        BeeObjectSourceConfig config = new BeeObjectSourceConfig();
        config.setObjectClassName(JavaBook.class.getName());
        BeeObjectSourceConfig config2 = config.check();

        if (config2 == config) throw new Exception("Configuration check copy failed");

        List<String> excludeNames = new LinkedList<>();
        excludeNames.add("createProperties");
        excludeNames.add("objectFactory");
        excludeNames.add("objectInterfaces");
        excludeNames.add("objectInterfaceNames");
        excludeNames.add("excludeMethodNames");

        //1:primitive type copy
        Field[] fields = BeeObjectSourceConfig.class.getDeclaredFields();
        for (Field field : fields) {
            if (!excludeNames.contains(field.getName())) {
                field.setAccessible(true);
                if (!Objects.deepEquals(field.get(config), field.get(config2))) {
                    throw new BeeObjectSourceConfigException("Failed to copy field[" + field.getName() + "],value is not equals");
                }
            }
        }

        //2:test container type properties
        if (config.getCreateProperties() == config2.getCreateProperties())
            throw new Exception("Configuration 'createProperties'check copy failed");
        if (!Objects.deepEquals(config.getCreateProperties(), config2.getCreateProperties()))
            throw new Exception("Configuration 'createProperties' check copy failed");

        if (!Objects.deepEquals(config.getExcludeMethodNames(), config2.getExcludeMethodNames()))
            throw new Exception("Configuration 'excludeMethodNames' check copy failed");
        if (!Objects.deepEquals(config.getObjectInterfaceNames(), config2.getObjectInterfaceNames()))
            throw new Exception("Configuration 'objectInterfaceNames' check copy failed");
    }

    private void testContainerField(BeeObjectSourceConfig config, BeeObjectSourceConfig config2, String propertyName) throws Exception {
        Field field = BeeObjectSourceConfig.class.getDeclaredField(propertyName);
        field.setAccessible(true);
    }
}