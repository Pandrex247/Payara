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

import fish.payara.extras.diagnostics.collection.CollectorService;
import fish.payara.extras.diagnostics.util.ParamConstants;
import fish.payara.extras.diagnostics.util.PropertiesFile;
import jakarta.inject.Inject;
import org.glassfish.api.Param;
import org.glassfish.api.admin.CommandException;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.hk2.api.ServiceLocator;
import org.jvnet.hk2.annotations.Service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Service(name = "collect-diagnostics")
@PerLookup
public class CollectAsadmin extends BaseAsadmin {

    @Param(name = ParamConstants.SERVER_LOG_PARAM, optional = true, defaultValue = "true")
    private boolean collectServerLog;

    @Param(name = ParamConstants.ACCESS_LOG_PARAM, optional = true, defaultValue = "true")
    private boolean collectAccessLog;

    @Param(name = ParamConstants.NOTIFICATION_LOG_PARAM, optional = true, defaultValue = "true")
    private boolean collectNotificationLog;

    @Param(name = ParamConstants.DOMAIN_XML_PARAM, optional = true, defaultValue = "true")
    private boolean collectDomainXml;

    @Param(name = ParamConstants.OBFUSCATE_PARAM, optional = true, defaultValue = "true")
    private boolean obfuscate;

    @Param(name = ParamConstants.THREAD_DUMP_PARAM, optional = true, defaultValue = "true")
    private boolean collectThreadDump;

    @Param(name = ParamConstants.JVM_REPORT_PARAM, optional = true, defaultValue = "true")
    private boolean collectJvmReport;

    @Param(name = ParamConstants.DOMAIN_NAME_PARAM, optional = true, primary = true, defaultValue = "domain1")
    private String domainName;

    @Param(name = ParamConstants.TARGET_PARAM, optional = true, defaultValue = "domain")
    private String target;

    @Param(name = ParamConstants.HEAP_DUMP_PARAM, optional = true, defaultValue = "true")
    private boolean collectHeapDump;

    @Param(name = ParamConstants.NODE_DIR_PARAM, optional = true)
    private String nodeDir;

    @Inject
    ServiceLocator serviceLocator;

    /**
     * Execute asadmin command Collect.
     * <p>
     * 0 - success
     * 1 - failure
     *
     * @return int
     */
    @Override
    protected int executeCommand() {
        parameterMap = populateParameters(new HashMap<>());
        parameterMap = resolveDir(parameterMap);

        CollectorService collectorService = new CollectorService(parameterMap, env, programOpts, target, serviceLocator, domainName, nodeDir);
        PropertiesFile props = getProperties();
        props.store(DIR_PARAM, (String) parameterMap.get(DIR_PARAM));
        return collectorService.executeCollection();
    }

    @Override
    protected void validate() throws CommandException {
        try {
            setDomainName(domainName);
            super.validate();
        } catch (Exception ignored) {
        }
    }

    /**
     * Populates parameters with Parameter options into a map. Overriden method add some more additionaly properties required by the collect command.
     *
     * @param params
     * @return Map<String, Object>
     */
    private Map<String, Object> populateParameters(Map<String, Object> params) {
        //Parameter Options
        params.put(ParamConstants.SERVER_LOG_PARAM, getOption(ParamConstants.SERVER_LOG_PARAM));
        params.put(ParamConstants.ACCESS_LOG_PARAM, getOption(ParamConstants.ACCESS_LOG_PARAM));
        params.put(ParamConstants.NOTIFICATION_LOG_PARAM, getOption(ParamConstants.NOTIFICATION_LOG_PARAM));
        params.put(ParamConstants.DOMAIN_XML_PARAM, getOption(ParamConstants.DOMAIN_XML_PARAM));
        params.put(ParamConstants.OBFUSCATE_PARAM, getOption(ParamConstants.OBFUSCATE_PARAM));
        params.put(ParamConstants.THREAD_DUMP_PARAM, getOption(ParamConstants.THREAD_DUMP_PARAM));
        params.put(ParamConstants.JVM_REPORT_PARAM, getOption(ParamConstants.JVM_REPORT_PARAM));
        params.put(ParamConstants.HEAP_DUMP_PARAM, getOption(ParamConstants.HEAP_DUMP_PARAM));
        params.put(ParamConstants.DOMAIN_NAME, getOption(ParamConstants.DOMAIN_NAME));

        //Paths
        if (this.getServerDirs() != null) {
            if (getDomainXml() != null) {
                params.put(ParamConstants.DOMAIN_XML_FILE_PATH, getDomainXml().getAbsolutePath());
            }
        }

        try {
            if (this.getDomainsDir() != null) {
                if (getDomainRootDir() != null) {
                    params.put(ParamConstants.LOGS_PATH, getDomainRootDir().getPath() + File.separator + "logs");
                }
            }
        } catch (NullPointerException ignored) {
        }
        return params;
    }

}
