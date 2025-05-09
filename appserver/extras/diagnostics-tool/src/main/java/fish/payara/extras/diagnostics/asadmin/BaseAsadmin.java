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

package fish.payara.extras.diagnostics.asadmin;

import com.sun.enterprise.admin.servermgmt.cli.LocalDomainCommand;
import fish.payara.extras.diagnostics.util.ParamConstants;
import fish.payara.extras.diagnostics.util.PropertiesFile;
import org.glassfish.api.ExecutionContext;
import org.glassfish.api.Param;
import org.glassfish.api.ParamDefaultCalculator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.logging.Level;

public abstract class BaseAsadmin extends LocalDomainCommand {
    private static final String OUTPUT_DIR_PARAM_SYS_PROP = "fish.payara.diagnostics.path";
    private static final String PROPERTIES_PATH_SYS_PROP = "fish.payara.properties.path";
    private static final String JAV_DIR_SYS_PROP = "user.home";

    protected static final String DIR_PARAM = ParamConstants.DIR_PARAM;

    private static final String PROPERTIES_PARAM = ParamConstants.PROPERTIES_PARAM;
    private static final String PROPERTIES_FILE_NAME = "." + PROPERTIES_PARAM;

    @Param(name = DIR_PARAM, shortName = "f", optional = true, defaultCalculator = DefaultOutputDirCalculator.class)
    protected String dir;

    @Param(name = PROPERTIES_PARAM, shortName = "p", optional = true, defaultCalculator = DefaultPropertiesPathCalculator.class)
    protected String propertiesPath;

    protected Map<String, Object> parameterMap;


    /**
     * Configures a valid directory to use in commands.
     *
     * @param params
     * @return Map&lt;String, Object&gt;
     */
    protected Map<String, Object> resolveDir(Map<String, Object> params) {
        if (params == null) {
            return null;
        }

        if (dir != null) {
            if (Files.isDirectory(Paths.get(dir))) {
                if (!dir.endsWith(File.separator)) {
                    dir = dir + File.separator;
                }
                String domainName = getDomainName() == null ? "" : "-" + getDomainName();
                dir = dir + "payara-diagnostics" + domainName + "-" +
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ssX").withZone(ZoneOffset.UTC).format(Instant.now());
            }
            params.put(DIR_PARAM, dir);
        }

        logger.log(Level.INFO, "Directory selected {0} ", dir);

        return params;
    }

    /**
     * If a properties file path exists, return a new properties file at that path.
     *
     * @return PropertiesFile
     */
    protected PropertiesFile getProperties() {
        if (propertiesPath != null) {
            Path path = Paths.get(propertiesPath);
            return new PropertiesFile(path);
        }
        return null;
    }

    public static class DefaultOutputDirCalculator extends ParamDefaultCalculator {
        @Override
        public String defaultValue(ExecutionContext context) {
            return System.getProperty(OUTPUT_DIR_PARAM_SYS_PROP, System.getProperty(JAV_DIR_SYS_PROP));
        }
    }

    public static class DefaultPropertiesPathCalculator extends ParamDefaultCalculator {
        @Override
        public String defaultValue(ExecutionContext context) {
            return System.getProperty(PROPERTIES_PATH_SYS_PROP, System.getProperty(JAV_DIR_SYS_PROP) + "/" + PROPERTIES_FILE_NAME);
        }
    }
}