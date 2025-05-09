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

package fish.payara.extras.diagnostics.collection.collectors;

import fish.payara.extras.diagnostics.collection.Collector;
import org.glassfish.api.logging.LogLevel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Logger;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public abstract class FileCollector implements Collector {
    Logger logger = Logger.getLogger(this.getClass().getName());

    private Path filePath;
    private Path destination;
    private String instanceName;

    private Map<String, Object> params;

    public FileCollector() {
    }

    public FileCollector(String filePath, String destination) {
        this(Paths.get(filePath), Paths.get(destination));
    }

    public FileCollector(Path filePath, Path destination) {
        this.filePath = filePath;
        this.destination = destination;
    }

    public FileCollector(File file, String destination) {
        this(file.getAbsolutePath(), destination);
    }

    @Override
    public int collect() {
        try {
            if (confirmPath(filePath, false) && confirmPath(destination, true)) {
                Path targetFile = resolveDestinationFile();
                Files.copy(filePath, targetFile, REPLACE_EXISTING);
            }
        } catch (IOException ie) {
            logger.log(LogLevel.SEVERE, "Could not copy path from " + filePath + " to " + destination);
            ie.printStackTrace();
            return 1;
        }
        return 0;
    }

    protected Path resolveDestinationFile() {
        if (instanceName != null) {
           return destination.resolve((instanceName + "-" + filePath.getFileName()));
        } else {
            return destination.resolve(filePath.getFileName());
        }
    }

    protected boolean confirmPath(Path path, boolean createIfNonExistant) {
        if (path != null) {
            if (Files.exists(path)) {
                return true;
            } else {
                if (createIfNonExistant) {
                    logger.log(LogLevel.INFO, "Attempting to create missing path at " + path);
                    try {
                        Files.createDirectories(path);
                    } catch (IOException io) {
                        logger.log(LogLevel.WARNING, "Could not create file at " + path.toString());
                        return false;
                    }
                    //Path is confirmed if it exists.
                    return Files.exists(path);
                }
            }
        }
        return false;
    }

    @Override
    public Map<String, Object> getParams() {
        return params;
    }

    @Override
    public void setParams(Map<String, Object> params) {
        if (params != null) {
            this.params = params;
        }
    }

    public void setFilePath(Path filePath) {
        if (filePath != null) {
            this.filePath = filePath;
        }
    }

    public void setDestination(Path path) {
        if (path != null) {
            this.destination = path;
        }
    }

    public Path getFilePath() {
        return this.filePath;
    }

    public Path getDestination() {
        return this.destination;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }
}
