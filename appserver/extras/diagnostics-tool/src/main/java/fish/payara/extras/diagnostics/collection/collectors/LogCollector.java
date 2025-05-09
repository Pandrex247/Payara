/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2023-2025 Payara Foundation and/or its affiliates. All rights reserved.
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

import com.sun.enterprise.admin.cli.Environment;
import com.sun.enterprise.admin.cli.ProgramOptions;
import com.sun.enterprise.admin.cli.remote.RemoteCLICommand;
import com.sun.enterprise.config.serverbeans.Node;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.SFTPv3Client;
import com.trilead.ssh2.SFTPv3DirectoryEntry;
import fish.payara.extras.diagnostics.collection.CollectorService;
import fish.payara.extras.diagnostics.util.ParamConstants;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.CommandException;
import org.glassfish.api.admin.ParameterMap;
import org.glassfish.cluster.ssh.launcher.SSHLauncher;
import org.glassfish.hk2.api.ServiceLocator;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class LogCollector extends FileCollector {

    private Map<String, Object> params;

    private Path logPath;
    private String logName;
    private String dirSuffix;
    private boolean obfuscateEnabled;
    private String target;
    private boolean collectNotifcationLogs;
    private boolean retrieveAccessLogs = false;
    private boolean collectServerLogs;
    private String currentCollectionLogType;
    private final ProgramOptions programOptions;
    private final Environment environment;
    private Logger LOGGER = Logger.getLogger(LogCollector.class.getName());
    private boolean collectLogsForServer;
    private Node node;
    private String nodeInstallationDirectory;
    private ServiceLocator serviceLocator;
    private String targetType = "CONFIG";

    public LogCollector(String logName, CollectorService collectorService, Environment environment, ProgramOptions programOptions, boolean logsForServer) {

        this.logName = logName;
        this.obfuscateEnabled = collectorService.getObfuscateEnabled();
        this.environment = environment;
        this.collectLogsForServer = logsForServer;
        this.programOptions = programOptions;
        this.target = collectorService.getTarget();
        this.collectNotifcationLogs = collectorService.notificationLog;
        this.collectServerLogs = collectorService.serverLog;
    }

    public LogCollector(String instanceName, String dirSuffix, String logName, CollectorService collectorService,  Environment environment, ProgramOptions programOptions, String collectionLogType, boolean logsForServer) {
        this(logName, collectorService, environment, programOptions, logsForServer);
        super.setInstanceName(instanceName);
        target = instanceName;
        this.targetType = collectorService.returnInstanceType(target);
        this.node = collectorService.returnCurrentNode(target);
        this.nodeInstallationDirectory = collectorService.returnNodeInstallationDirectory(target);
        this.dirSuffix = dirSuffix;
        this.serviceLocator = collectorService.returnServiceLocator();
        this.currentCollectionLogType = collectionLogType;

    }

    @Override
    public int collect(){
        Map<String, Object> params = getParams();
        if (params == null) {
            return 0;
        }
        String outputPathString = (String) params.get(ParamConstants.DIR_PARAM);
        Path outputPath = Paths.get(outputPathString, dirSuffix != null ? dirSuffix : "");

        if (collectLogsForServer){
            collectLogs(outputPath);
        }
        if (currentCollectionLogType != null){
            switch (currentCollectionLogType) {
                case "server":
                case "notification":
                    collectLogs(outputPath);
                    break;
                case "access":
                    retrieveAccessLogs = true;
                    collectLogs(outputPath);
                    break;
            }
        }

        return 0;
    }

    private boolean collectLogs(Path outputPath) {
        //./asadmin collect-log-files --target xx --retrieve true /home/user/... will create /logs/target
        try {
            if (!target.equals("domain")){
                ParameterMap parameterMap = new ParameterMap();
                parameterMap.add("target", target);
                programOptions.updateOptions(parameterMap);
            }
            programOptions.setInteractive(false);


            RemoteCLICommand remoteCLICommand = new RemoteCLICommand("collect-log-files", programOptions, environment);
            String result = remoteCLICommand.executeAndReturnOutput("collect-log-files");
            ActionReport report = remoteCLICommand.executeAndReturnActionReport();

            String zipFilePath = parseCommandZipFilePath(result);
            if (zipFilePath == null) {
                LOGGER.info("Failed to extract path");
                return false;
            }

            unzipFile(Paths.get(zipFilePath), outputPath);
            if (!collectNotifcationLogs){
                deleteNotificationOrServerLog(outputPath, "notification.log");}
            if (!collectServerLogs){
                deleteNotificationOrServerLog(outputPath, "server.log");
            }
            if (retrieveAccessLogs && targetType.equals("SSH")){
                Path accessLogsFilePath = Paths.get(nodeInstallationDirectory + "/glassfish/nodes/" + node.getName() + "/" + getInstanceName() + "/logs/access/");
                String accessLogFile = nodeInstallationDirectory + "/glassfish/nodes/" + node.getName() + "/" + getInstanceName() + "/logs/access/";
                downloadAccessLogUsingSCP(accessLogFile,outputPath.toString(), logName, accessLogsFilePath);
            }
            if (retrieveAccessLogs && targetType.equals("CONFIG")){
                Path accessLogsFilePath = Paths.get(nodeInstallationDirectory + "/glassfish/nodes/" + node.getName() + "/" + getInstanceName() + "/logs/access/");
                retrieveAccessLogSLocally(outputPath.toString(),accessLogsFilePath);
            }

            if (report.getActionExitCode() == ActionReport.ExitCode.SUCCESS) {
                LOGGER.info("Log has been collected successfully");
            }
        } catch (CommandException e) {
            LOGGER.info(e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private String parseCommandZipFilePath(String commandOutput) {
        Pattern pattern = Pattern.compile("Created Zip file under (.+\\.zip)");
        Matcher matcher = pattern.matcher(commandOutput);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void unzipFile(Path zipFilePath, Path outputDir) throws IOException {
        Path finalOutputFilePath = null;
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath.toFile()))) {
            ZipEntry entry; // current file, shows like: logs/server/server.log
            while ((entry = zis.getNextEntry()) != null) {
                finalOutputFilePath = outputDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(finalOutputFilePath);
                } else {
                    Files.createDirectories(finalOutputFilePath.getParent());
                    try (OutputStream os = new FileOutputStream(finalOutputFilePath.toFile())) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
        LOGGER.info("Unzipped logs to: " + finalOutputFilePath); // Added to see which folder/file is created first
    }

    private void deleteNotificationOrServerLog(Path outputDir, String deleteLogName) {
        try {
            Files.walk(outputDir)
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals(deleteLogName))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            LOGGER.info("Deleted: " + path);
                        } catch (IOException e) {
                            LOGGER.warning("Failed to delete: " + path + " due to " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOGGER.warning("Error traversing directory " + outputDir + ": " + e.getMessage());
        }
    }

    private boolean downloadAccessLogUsingSCP(String remoteDirectory, String localAccessDirectory, String fileName, Path remoteFilePath) throws IOException {
        try  {
            SSHLauncher sshL = getSSHL(serviceLocator);
            sshL.init(node, LOGGER);
            SFTPv3Client sftpClient = sshL.getSFTPClient();
            SCPClient scpClient = sshL.getSCPClient();
            localAccessDirectory = localAccessDirectory+"/logs/"+getInstanceName()+"/access/";
            Files.createDirectories(Paths.get(localAccessDirectory));
            remoteFilePath.getFileName().toString().contains(fileName);

            Vector listOfFilesInDirectory = sftpClient.ls(remoteDirectory);
            for (Object fileObject: listOfFilesInDirectory){
                SFTPv3DirectoryEntry file = (SFTPv3DirectoryEntry) fileObject;
                if (file.attributes.isRegularFile()){
                    String remoteFileName = file.filename;
                    LOGGER.info("Downloading access log: " + remoteFileName);
                    scpClient.get(remoteDirectory + remoteFileName, localAccessDirectory);
                }
            }
            // remoteFile would be {installationDirectory} + "/glassfish/nodes/" + {node.toString()} + "/" + {instance} + "/logs/access/(file contains access_log)"
            LOGGER.info("Access log downloaded successfully to: " + localAccessDirectory);
            return true;
        } catch (IOException e) {
            LOGGER.severe("Error downloading access logs: " + e.getMessage());
            return false;
        }
    }

    private boolean retrieveAccessLogSLocally(String localAccessDirectory, Path accessLogsFilePath) {
        try {
            Path localAccessPath = Paths.get(localAccessDirectory+"/logs/"+getInstanceName()+"/access/");
            Files.createDirectories(localAccessPath);

            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(accessLogsFilePath)) {
                for (Path file : directoryStream) {
                    if (Files.isRegularFile(file)) {
                        Path destination = (localAccessPath.resolve(file.getFileName()));
                        Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                        LOGGER.info("Copied access log: " + file.getFileName() + " to " + destination);
                    }
                }
            }
            LOGGER.info("All access logs have been retrieved successfully to: " + localAccessDirectory);
            return true;
        } catch (IOException e) {
            LOGGER.severe("Error retrieving access logs locally: " + e.getMessage());
            return false;
        }
    }


    private SSHLauncher getSSHL(ServiceLocator habitat) {
        return habitat.getService(SSHLauncher.class);
    }

}