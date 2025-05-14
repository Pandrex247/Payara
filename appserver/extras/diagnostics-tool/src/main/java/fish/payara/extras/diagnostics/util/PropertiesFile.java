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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Logger;

import org.glassfish.api.logging.LogLevel;

public class PropertiesFile {
    Logger logger = Logger.getLogger(this.getClass().getName());

    private Path path;
    private Properties props;

    public PropertiesFile(Path path) {
        this.path = path;
        this.props = new Properties();
    }   

    /**
     * Store method takes a key value pair, and stores in a properties file.
     * 
     * @param key
     * @param value
     */
    public void store(String key, String value) {
        try(OutputStream out = new FileOutputStream(path.toString())) {
            props.setProperty(key, value);
            props.store(out, null);
        } catch(FileNotFoundException fnfe) {
            logger.log(LogLevel.WARNING, "Properties file was not found", path.toString());
        } catch(IOException io) {
            logger.log(LogLevel.SEVERE, String.format("IOException occured trying to store %s and %s", key, value));
            io.printStackTrace();
        }
    }

    /**
     * Retrieves value based on key from the properties file.
     * 
     * @param key
     * @return String
     */
    public String get(String key) {
        try(InputStream in = new FileInputStream(path.toString())) {
            props.load(in);
            return props.getProperty(key);
        } catch(FileNotFoundException fnfe) {
            logger.log(LogLevel.WARNING, "Properties file was not found", path.toString());
        } catch(IOException io) {
            logger.log(LogLevel.SEVERE, String.format("IOException occured trying to fetch %s", key));
            io.printStackTrace();
        }
        
        return null;
    }

    /**
     * Returns properties file path.
     * @return Path
     */
    public Path getPath() {
        return this.path;
    }
}
