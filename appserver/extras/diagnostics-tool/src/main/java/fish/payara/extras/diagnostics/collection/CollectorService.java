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

package fish.payara.extras.diagnostics.collection;

import com.sun.enterprise.admin.cli.Environment;
import com.sun.enterprise.admin.cli.ProgramOptions;
import com.sun.enterprise.admin.cli.remote.RemoteCLICommand;
import com.sun.enterprise.config.serverbeans.Cluster;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Node;
import com.sun.enterprise.config.serverbeans.Server;
import fish.payara.enterprise.config.serverbeans.DeploymentGroup;
import fish.payara.extras.diagnostics.collection.collectors.DomainXmlCollector;
import fish.payara.extras.diagnostics.collection.collectors.HeapDumpCollector;
import fish.payara.extras.diagnostics.collection.collectors.JVMCollector;
import fish.payara.extras.diagnostics.collection.collectors.LocalLogCollector;
import fish.payara.extras.diagnostics.collection.collectors.LogCollector;
import fish.payara.extras.diagnostics.collection.collectors.FileCollector;
import fish.payara.extras.diagnostics.util.DomainUtil;
import fish.payara.extras.diagnostics.util.JvmCollectionType;
import fish.payara.extras.diagnostics.util.TargetType;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.CommandException;
import org.glassfish.api.admin.ParameterMap;
import org.glassfish.api.logging.LogLevel;
import org.glassfish.hk2.api.ServiceLocator;
import org.jvnet.hk2.config.ConfigParser;

import jakarta.json.JsonString;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static fish.payara.extras.diagnostics.util.ParamConstants.*;

public class CollectorService {
    private static final Logger LOGGER = Logger.getLogger(CollectorService.class.getName());
    private final String target;
    private final Environment environment;
    private final ProgramOptions programOptions;
    private Boolean domainXml;
    private Boolean obfuscateDomainXml;
    public Boolean accessLog;
    public Boolean notificationLog;
    public Boolean serverLog;
    private Boolean threadDump;
    private Boolean jvmReport;
    private Boolean heapDump;
    private boolean isOkayToCollect = true;
    private boolean serverIsOn = true;
    private Domain domain;
    private DomainUtil domainUtil;
    private final String domainName;
    private Path logPath;
    private final ServiceLocator serviceLocator;
    private final Map<String, Object> parameterMap;
    private List<String> instanceList = new ArrayList<>();
    private Map<String, String> instanceWithType = new HashMap<>();
    private Map<String, String> nodeInstallationDirectories= new HashMap<>();
    private Map<String, String> instanceInNode= new HashMap<>();
    private TargetType targetType;

    public CollectorService(Map<String, Object> params, Environment environment, ProgramOptions programOptions, String target, ServiceLocator serviceLocator, String domainName, String nodeDir) {
        this.parameterMap = params;
        this.target = target;
        this.environment = environment;
        this.programOptions = programOptions;
        this.serviceLocator = serviceLocator;
        this.domainName = domainName;
        init();
    }

    private void init() {
        domainXml = true;
        obfuscateDomainXml = true;
        serverLog = true;
        accessLog = true;
        notificationLog = true;
        threadDump = true;
        jvmReport = true;
        heapDump = true;

        if (parameterMap != null) {
            domainXml = parameterMap.get(DOMAIN_XML_PARAM) == null || Boolean.parseBoolean((String) parameterMap.get(DOMAIN_XML_PARAM));
            obfuscateDomainXml = parameterMap.get(OBFUSCATE_PARAM) == null || Boolean.parseBoolean((String) parameterMap.get(OBFUSCATE_PARAM));
            serverLog = parameterMap.get(SERVER_LOG_PARAM) == null || Boolean.parseBoolean((String) parameterMap.get(SERVER_LOG_PARAM));
            accessLog = parameterMap.get(ACCESS_LOG_PARAM) == null || Boolean.parseBoolean((String) parameterMap.get(ACCESS_LOG_PARAM));
            notificationLog = parameterMap.get(NOTIFICATION_LOG_PARAM) == null || Boolean.parseBoolean((String) parameterMap.get(NOTIFICATION_LOG_PARAM));
            threadDump = parameterMap.get(THREAD_DUMP_PARAM) == null || Boolean.parseBoolean((String) parameterMap.get(THREAD_DUMP_PARAM));
            jvmReport = parameterMap.get(JVM_REPORT_PARAM) == null || Boolean.parseBoolean((String) parameterMap.get(JVM_REPORT_PARAM));
            heapDump = parameterMap.get(HEAP_DUMP_PARAM) == null || Boolean.parseBoolean((String) parameterMap.get(HEAP_DUMP_PARAM));
        }
    }


    /**
     * Executes collection of all specified collectors.
     * <p>
     * 0 - Success
     * 1 - Failure
     *
     * @return int
     */
    public int executeCollection() {
        if (parameterMap == null) {
            return 1;
        }

        if (parameterMap.containsKey(DOMAIN_XML_FILE_PATH)) {
            domain = getDomain((String) parameterMap.get(DOMAIN_XML_FILE_PATH));
        }

        List<Collector> activeCollectors = new ArrayList<>();
        // Populates the `targets` list
        domainUtil = new DomainUtil(domain);
        targetType = getTargetType();
        if (targetType == null) {
            LOGGER.info("Target not found! Is the name correct?");
            return 0;
        }
        getInstanceList();
        if (!isOkayToCollect){
            return 0;
        }
        String instanceTargetPlaceholder = "";

        if (domain == null) {
            if (instanceList.isEmpty()) {
                activeCollectors = getActiveCollectors(parameterMap, TargetType.DOMAIN, instanceTargetPlaceholder);
            }
        } else {
            if (instanceList.isEmpty()) {
                activeCollectors = getActiveCollectors(parameterMap, TargetType.DOMAIN, instanceTargetPlaceholder);
            }
            else {
                switch (targetType) {
                    case DOMAIN:
                        activeCollectors = getActiveCollectors(parameterMap, targetType, instanceList.get(0));
                        break;
                    case DEPLOYMENT_GROUP:
                    case CLUSTER:
                        activeCollectors = getActiveCollectors(parameterMap, targetType, instanceTargetPlaceholder);
                        break;
                    case INSTANCE:
                        int indexOfInstance = instanceList.indexOf(target);
                        activeCollectors = getActiveCollectors(parameterMap, targetType, instanceList.get(indexOfInstance));
                        break;
                }
            }
        }

        if (activeCollectors.isEmpty()) {
            LOGGER.info("No collectors are active. Nothing will be collected!");
            return 0;
        }

        int result = 0;
        String previousInstance = null;
        String currentInstance = null;
        boolean skipFirstCollectorName = true; //first collector (DomainXML) has no instanceName/target
        for (Collector collector : activeCollectors) {
            if (!skipFirstCollectorName || !(collector.getClass().getSimpleName().contains("DomainXml"))) {
                currentInstance = getCollectorTarget(collector);

                if (currentInstance != null && !currentInstance.equals(previousInstance)) {
                    if (previousInstance != null) {
                        LOGGER.info("");
                    }
                    LOGGER.info("");
                    LOGGER.info("Collecting for: " + currentInstance);
                    previousInstance = currentInstance;
                }
            }

            skipFirstCollectorName = false;
            collector.setParams(parameterMap);
            result = collector.collect();
            if (result != 0) {
                return result;
            }
        }


        Path filePath = Paths.get((String) parameterMap.get(DIR_PARAM));

        try (FileOutputStream fileOutputStream = new FileOutputStream(filePath.toFile() + ".zip")) {
            ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
            compressDirectory(filePath, zipOutputStream);
            zipOutputStream.close();

            cleanUpDirectory(filePath.toFile());
        } catch (IOException e) {
            LOGGER.log(LogLevel.SEVERE, "Could not zip collected files.");
            LOGGER.log(LogLevel.SEVERE, e.getMessage());
            return 1;
        }
        //Save output to properties, to make uplaod seamless for the user

        return result;
    }

    private void getInstanceList() {
        try {
            ParameterMap parameterMap = new ParameterMap();
            parameterMap.add("long", "true");
            programOptions.updateOptions(parameterMap);


            RemoteCLICommand listNodesRemoteCLICommand = new RemoteCLICommand("list-nodes", programOptions, environment);
            String nodesOutput = listNodesRemoteCLICommand.executeAndReturnOutput();
            //Action report does not contain the referenceBy values and the node type.
            //ActionReport nodesResult = listNodesRemoteCLICommand.executeAndReturnActionReport();

            RemoteCLICommand listInstancesRemoteCLICommand = new RemoteCLICommand("list-instances", programOptions, environment);
            ActionReport instanceResult = listInstancesRemoteCLICommand.executeAndReturnActionReport("list-instances");

            if (instanceResult.getActionExitCode() == ActionReport.ExitCode.SUCCESS) {
                List<Map<String, Object>> instanceList = (List<Map<String, Object>>) instanceResult.getExtraProperties().get("instanceList");

                boolean skipFirstLine = true;
                String[] lines = nodesOutput.split("\\n");
                int referencedByIndex = nodesOutput.indexOf("Referenced By");
                int installationPathIndex = nodesOutput.indexOf("Installation Directory");

                for (String line : lines) {
                    if (skipFirstLine) {
                        skipFirstLine = false;
                        continue;
                    }
                    String[] parts = line.split("\\s+");

                    if (parts.length >= 4) {
                        String nodeType = parts[1];
                        String nodeName = parts[0];
                        String nodeInstallationPath = parts[3];
                        String nodeReferenceBy = line.substring(referencedByIndex).trim();
                        String nodeInstallationDirectory = line.substring(installationPathIndex).trim();
                        String[]  instances = nodeReferenceBy.split(", ");
                        for (String instance : instances) {
                            if (!instance.isEmpty()){
                                if (nodeName.contains(domain.getName())){
                                    LOGGER.info("Adding instance: " + instance + " with type: " + nodeType);
                                }
                                instanceWithType.put(instance, nodeType);
                                instanceInNode.put(instance, nodeName);
                            }
                        }
                        if (!nodeInstallationDirectory.isEmpty()){
                            LOGGER.info("Adding installation directory: " + nodeInstallationPath + " for node: " + nodeName);
                            nodeInstallationDirectories.put(nodeName ,nodeInstallationPath);
                        }
                    }
                }

                for (Map<String, Object> instance : instanceList) {
                    Object nameObject = instance.get("name");
                    String instanceName = ((JsonString) nameObject).getString();
                    this.instanceList.add(instanceName);
                }
                LOGGER.info("Instance List " + this.instanceList);
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Is the server up?")) {
                LOGGER.info("Server Offline! Only domain.xml and local server logs will be collected.");
                LOGGER.info("Turn on Server to collect from instances!");
                serverIsOn = false;
            }
            if (e.getMessage() != null && e.getMessage().contains("Authentication failed")) {
                LOGGER.info(e.getMessage());
                LOGGER.info("Cancelling collection");
                isOkayToCollect = false;
                return;
            }
            if (instanceList.isEmpty()) {
                LOGGER.info("No instances found from remote command. Collecting local instances");
                DomainUtil domainUtil = new DomainUtil(domain);
                List<Server> localInstances = domainUtil.getLocalInstances();

                for (Server server : localInstances) {
                    this.instanceList.add(server.getName());
                    instanceWithType.put(server.getName(), "CONFIG");
                }

                if (instanceList.isEmpty()) {
                    LOGGER.info("No local instances found using DomainUtil.");
                } else {
                    LOGGER.info("Local instances retrieved: " + instanceList);
                }
            }
            if (e instanceof NullPointerException) {
                LOGGER.info("No install directory could be found. Please check remote/local node");
            }
            LOGGER.log(LogLevel.SEVERE, "Could not execute command. " , e);
        }
    }

    public TargetType getTargetType() {
        if (target.equals("domain")) {
            return TargetType.DOMAIN;
        }
        if (domainUtil.getInstancesNames().contains(target)) {
            return TargetType.INSTANCE;
        }

        if (domainUtil.getDeploymentGroups().getDeploymentGroup(target) != null) {
            return TargetType.DEPLOYMENT_GROUP;
        }

        if (domainUtil.getClusters().getCluster(target) != null) {
            return TargetType.CLUSTER;
        }
        return null;
    }


    /**
     * Compresses the collected directory into a zip folder.
     *
     * @param filePath
     * @param zipOutputStream
     * @throws IOException
     */
    private void compressDirectory(Path filePath, ZipOutputStream zipOutputStream) throws IOException {
        Files.walkFileTree(filePath, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                //https://pkware.cachefly.net/webdocs/APPNOTE/APPNOTE-6.3.10.TXT paragraph 4.4.17.1
                String entryName = filePath.relativize(file).toString().replace("\\", "/");
                zipOutputStream.putNextEntry(new ZipEntry(entryName));
                Files.copy(file, zipOutputStream);
                zipOutputStream.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
    }


    /**
     * Removes specified directory.
     *
     * @param dir
     * @throws IOException
     */
    private void cleanUpDirectory(File dir) throws IOException {
        Path path = dir.toPath();
        try (Stream<Path> walk = Files.walk(path)) {
            walk
                    .sorted(Comparator.reverseOrder())
                    .forEach(this::deleteDirectory);
        }
    }


    /**
     * Utility method for directory cleanup. Removes files at specified path.
     *
     * @param path
     */
    private void deleteDirectory(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            LOGGER.log(LogLevel.INFO, "Could not cleanup directory at {0}", path.toString());
        }
    }


    /**
     * Returns list of collectors which are enabled from user parameters.
     *
     * @param parameterMap
     * @return List&lt;Collector&gt;
     */
    public List<Collector> getActiveCollectors(Map<String, Object> parameterMap, TargetType targetType, String currentTarget) {
        List<Collector> activeCollectors = new ArrayList<>();

        if (parameterMap == null) {
            return activeCollectors;
        }

        boolean correctDomainRunning = correctDomainRunning();
        String instanceType = instanceWithType.get(currentTarget);

        if (targetType == TargetType.DOMAIN) {
            //The collectors inside this block, will copy the files with no folder
            if (domainXml) {
                Path domainXmlPath = Paths.get((String) parameterMap.get(DOMAIN_XML_FILE_PATH));
                activeCollectors.add(new DomainXmlCollector(domainXmlPath, obfuscateDomainXml, this));
            }
            if (serverLog) {
                if (!serverIsOn) {
                    Path serverLogPath = Paths.get((String) parameterMap.get(LOGS_PATH));
                    activeCollectors.add(new LocalLogCollector(serverLogPath, "server.log", this));
                    if (notificationLog){
                        activeCollectors.add(new LocalLogCollector(serverLogPath, "notification.log", this));
                    }
                    if (accessLog){
                        activeCollectors.add(new LocalLogCollector(serverLogPath, "access_log", this));
                    }
                } else {
                    boolean collectDomainLogs = true;
                    activeCollectors.add(new LogCollector("server.log", this, environment, programOptions, collectDomainLogs));
                }
            }

            if (jvmReport) {
                activeCollectors.add(new JVMCollector(environment, programOptions, "server", JvmCollectionType.JVM_REPORT, correctDomainRunning));
            }

            if (threadDump) {
                activeCollectors.add(new JVMCollector(environment, programOptions, "server", JvmCollectionType.THREAD_DUMP, correctDomainRunning));
            }

            if (heapDump) {
                activeCollectors.add(new HeapDumpCollector("server", programOptions, environment, correctDomainRunning));
            }

            //adds folder for instance
            if (!instanceList.isEmpty()) {
                addInstanceCollectors(activeCollectors, domainUtil.getStandaloneLocalInstances(), "");


                //adds folder for DG
                for (DeploymentGroup deploymentGroup : domainUtil.getDeploymentGroups().getDeploymentGroup()) {
                    addInstanceCollectors(activeCollectors, deploymentGroup.getInstances(), deploymentGroup.getName());
                }

                for (Cluster cluster : domainUtil.getClusters().getCluster()) {
                    addInstanceCollectors(activeCollectors, cluster.getInstances(), cluster.getName());
                }
            }
        }

        if (targetType == TargetType.INSTANCE) {
            List<Server> servers = new ArrayList<>();
            servers.add(domainUtil.getInstance(currentTarget));
            addInstanceCollectors(activeCollectors, servers, "");
        }

        if (targetType == TargetType.DEPLOYMENT_GROUP) {
            for (DeploymentGroup deploymentGroup : domainUtil.getDeploymentGroups().getDeploymentGroup()) {
                if (deploymentGroup.getName().equals(target)) {
                    addInstanceCollectors(activeCollectors, deploymentGroup.getInstances(), deploymentGroup.getName());
                }
            }
        }

        if (targetType == TargetType.CLUSTER) {
            for (Cluster cluster : domainUtil.getClusters().getCluster()) {
                if (cluster.getName().equals(target)) {
                    addInstanceCollectors(activeCollectors, cluster.getInstances(), cluster.getName());
                }
            }
        }
        return activeCollectors;
    }


    private void addInstanceCollectors(List<Collector> activeCollectors, List<Server> serversList, String dirSuffix) {
        int instanceCounter = 0;
        for (Server server : serversList) {
            Path dirSuffixPath = Paths.get(dirSuffix, server.getName());
            String finalDirSuffix;
            if (dirSuffixPath == null) {
                LOGGER.info("Could not find name for folder! Setting default name: No_Name_Instance"+instanceCounter);
                finalDirSuffix = "No_Name_Instance"+instanceCounter;
                instanceCounter++;
            } else {
                finalDirSuffix = dirSuffixPath.toString();
            }
            String instanceType = instanceWithType.get(server.getName());

            if (instanceType == null) {
                LOGGER.info("Instance type for: " + server.getName() + " not found, skipping it.");
                continue;
            }

            if (domainXml) {
                switch (instanceType) {
                    case "CONFIG":
                        activeCollectors.add(new DomainXmlCollector(Paths.get(domainUtil.getNodePaths().get(server.getNodeRef()).toString(), server.getName(), "config", "domain.xml"), server.getName(), finalDirSuffix, obfuscateDomainXml, this));
                        break;
                    case "SSH":
                        Path domainXmlPath = Paths.get((String) parameterMap.get(DOMAIN_XML_FILE_PATH));
                        activeCollectors.add(new DomainXmlCollector(domainXmlPath, server.getName(), finalDirSuffix, obfuscateDomainXml, this));
                        break;
                }
            }

            if (instanceType.equals("CONFIG")){
                LOGGER.info("Getting log path for: " + server.getName());
                logPath = Paths.get(domainUtil.getNodePaths().get(server.getNodeRef()).toString(), server.getName(), "logs");
            }

            if (serverLog) {
                if (!serverIsOn && instanceType.equals("CONFIG")) {
                    activeCollectors.add(new LocalLogCollector(logPath, server.getName(), finalDirSuffix, "server.log",this));
                } else if (serverIsOn){
                    activeCollectors.add(new LogCollector(server.getName(), finalDirSuffix, "server.log", this, environment, programOptions, "server", false));
                }
            }
            if (accessLog) {
                if (!serverIsOn && instanceType.equals("CONFIG")) {
                    activeCollectors.add(new LocalLogCollector(logPath, server.getName(), finalDirSuffix, "access_log",this));
                } else if (serverIsOn){
                    activeCollectors.add(new LogCollector(server.getName(), finalDirSuffix, "access_log", this, environment, programOptions, "access", false));
                }
            }

            if (notificationLog) {
                if (!serverIsOn && instanceType.equals("CONFIG")) {
                    activeCollectors.add(new LocalLogCollector(logPath, server.getName(), finalDirSuffix, "notification.log",this));
                } else if (serverIsOn) {
                    activeCollectors.add(new LogCollector(server.getName(), finalDirSuffix, "notification.log", this, environment, programOptions, "notification", false));
                }
            }
            if (jvmReport) {
                activeCollectors.add(new JVMCollector(environment, programOptions, server.getName(), JvmCollectionType.JVM_REPORT, finalDirSuffix));
            }

            if (threadDump) {
                activeCollectors.add(new JVMCollector(environment, programOptions, server.getName(), JvmCollectionType.THREAD_DUMP, finalDirSuffix));
            }
            if (heapDump) {
                activeCollectors.add(new HeapDumpCollector(server.getName(), programOptions, environment, finalDirSuffix, this));
            }
        }
    }

    private Domain getDomain(String domainXmlPath) {
        File domainXml = Paths.get(domainXmlPath).toFile();
        ConfigParser configParser = new ConfigParser(serviceLocator);

        try {
            configParser.logUnrecognisedElements(false);
        } catch (NoSuchMethodError noSuchMethodError) {
            LOGGER.log(Level.FINE, "Using a version of ConfigParser that does not support disabling log messages via method",
                    noSuchMethodError);
        }

        URL domainUrl;
        try {
            domainUrl = domainXml.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return configParser.parse(domainUrl).getRoot().createProxy(Domain.class);
    }

    private Boolean correctDomainRunning() {
        try {
            RemoteCLICommand remoteCLICommand = new RemoteCLICommand("get", programOptions, environment);
            String result = remoteCLICommand.executeAndReturnOutput("get", "property.administrative.domain.name");

            if (result.contains("Remote server does not listen for requests on")) {
                return false;
            }

            if (result.contains("=")) {
                String[] keyValue = result.split("=");
                if (keyValue.length == 2) {
                    if (keyValue[1].equalsIgnoreCase(domainName)) {
                        return true;
                    }
                }
            }

            return false;
        } catch (CommandException e) {
            return false;
        }
    }

    public boolean getObfuscateEnabled(){
        return obfuscateDomainXml;
    }

    public String getTarget(){
        return target;
    }

    //Extracts field "target" or "instanceName" from the collector so it can be used to separate output
    private String getCollectorTarget(Collector collector) {
        String[] possibleFields = {"target", "instanceName"};

        for (String fieldName : possibleFields) {
            Class<?> currentClass = collector.getClass();
            while (currentClass != null) {
                try {
                    Field field = currentClass.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(collector);
                    if (value != null) {
                        return value.toString();
                    }
                } catch (NoSuchFieldException e) {
                    // for domainxml its stored in the superclass since it uses super.setInstanceName
                    currentClass = currentClass.getSuperclass();
                } catch (IllegalAccessException e) {
                    // Shouldn't happen due to setAccessible(true), but fallback
                    break;
                }
            }
        }
        return null;
    }



    public String returnInstanceType(String instance) {
        return instanceWithType.get(instance);
    }

    public Node returnCurrentNode(String instance) {
        String nodeName = instanceInNode.get(instance);
        return domain.getNodeNamed(nodeName);
    }

    public String returnNodeInstallationDirectory(String instance) {
        String nodeName = instanceInNode.get(instance);
        return nodeInstallationDirectories.get(nodeName);
    }
    public ServiceLocator returnServiceLocator(){
        return serviceLocator;
    }
}
