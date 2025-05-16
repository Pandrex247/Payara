/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016-2024 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/main/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
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
package fish.payara.appserver.cpu.core.reporting;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.glassfish.api.StartupRunLevel;
import org.glassfish.api.event.EventListener;
import org.glassfish.api.event.EventTypes;
import org.glassfish.api.event.Events;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.server.ServerEnvironmentImpl;
import org.jvnet.hk2.annotations.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.nio.file.Files;

/**
 * @author Stian Sigvartsen
 */
@Service(name = "cpu-core-reporting-service")
@RunLevel(StartupRunLevel.VAL)
public class CpuCoreReportingService implements EventListener {

    @Override
    public void event(Event<?> event) {
        if (!event.is(EventTypes.SERVER_STARTUP)) {
            return;
        }
        logger.log(Level.INFO, "Capturing CPU cores data...");

        Thread thread = new Thread(() -> {
            try {
                Path path = Paths.get(env.getInstanceRoot().getAbsolutePath() + File.separator + "logs" + File.separator + LOG_FILE_NAME);
                String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_DATE_TIME);
                int availableProcessors = Runtime.getRuntime().availableProcessors();

                if (!Files.exists(path)) {
                    Files.createFile(path);
                }
                Files.writeString(
                        path,
                        String.format("%s,%s\n", timestamp, availableProcessors),
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to capture CPU cores data", e);
            }
        });

        events.unregister(this);

        thread.start();
    }

    @PostConstruct
    public void postConstruct() {
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.WARNING, "CPU Core Reporting Service: Initialisation error.", e);
            throw new Error(e);
        }
        events.register(this);
    }

    @PreDestroy
    public void preDestroy() {
        events.unregister(this);
    }

    @Inject
    private Events events;

    @Inject
    private Logger logger;

    @Inject
    private ServerEnvironmentImpl env;

    private static final String LOG_FILE_NAME = "cpu_monitor.log";
}