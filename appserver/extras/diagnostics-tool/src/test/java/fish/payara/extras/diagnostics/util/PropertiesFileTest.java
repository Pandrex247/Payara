/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2023-2024 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package fish.payara.extras.diagnostics.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PropertiesFileTest {
    
    private static final Path TEST_FILE_PATH = Paths.get(System.getProperty("java.io.tmpdir")).resolve(".testproperties");
    PropertiesFile props;

    @Before
    public void initialisePropertiesFile() {
        props = new PropertiesFile(TEST_FILE_PATH);
    }

    @Test
    public void propertiesValidStoreTest() {
        assertNotNull(props);
        props.store("test", "test");
        assertTrue(Files.exists(TEST_FILE_PATH));
        assertEquals("test", props.get("test"));
    }

    @Test
    public void propertiesConsecutiveValidStoreTest() {
        assertNotNull(props);
        props.store("test", "test");
        assertTrue(Files.exists(TEST_FILE_PATH));
        assertEquals("test", props.get("test"));

        props.store("test2", "test");
        assertTrue(Files.exists(TEST_FILE_PATH));
        assertEquals("test", props.get("test"));
        assertEquals("test", props.get("test2"));
    }

    @Test
    public void propertiesOverwriteExistingKeyTest() {
        assertNotNull(props);
        props.store("test", "test");
        assertTrue(Files.exists(TEST_FILE_PATH));
        assertEquals("test", props.get("test"));

        props.store("test", "test1");
        assertTrue(Files.exists(TEST_FILE_PATH));
        assertEquals("test1", props.get("test"));
    }

    @Test
    public void propertiesInvalidGetTest() {
        assertNotNull(props);
        props.store("test", "test");
        assertTrue(Files.exists(TEST_FILE_PATH));
        assertEquals(null, props.get("non-existant-key"));
    }

    @Test
    public void propertiesGetBeforeStoreTest() {
        assertNotNull(props);
        assertFalse(Files.exists(TEST_FILE_PATH));
        assertEquals(null, props.get("test"));
    }

    @Test
    public void propertiesNonExistantPathTest() {
        Path nonPath = Paths.get("this/path/does/not/exist");
        props = new PropertiesFile(nonPath);
        assertNotNull(props);
        props.store("test", "test");
        assertFalse(Files.exists(nonPath));
        assertEquals(null, props.get("test"));
    }

    @After
    public void cleanUp() {
        if(Files.exists(TEST_FILE_PATH)) {
            TEST_FILE_PATH.toFile().delete();
        }
    }

}
