/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2021 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
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

package fish.payara.extensions.autoscale.groups.core.admin;

import com.sun.enterprise.admin.remote.RemoteRestAdminCommand;
import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Server;
import com.sun.enterprise.config.serverbeans.Servers;
import com.sun.enterprise.v3.admin.adapter.AdminEndpointDecider;
import com.sun.enterprise.v3.admin.cluster.ClusterCommandHelper;
import com.sun.enterprise.v3.admin.cluster.CommandRunnable;
import com.sun.enterprise.v3.admin.cluster.Strings;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandException;
import org.glassfish.api.admin.CommandRunner;
import org.glassfish.api.admin.ParameterMap;
import org.glassfish.api.admin.ProgressStatus;
import org.glassfish.api.admin.progress.ProgressStatusImpl;

import javax.security.auth.Subject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.FINE;
import static org.glassfish.api.ActionReport.ExitCode.FAILURE;
import static org.glassfish.api.ActionReport.ExitCode.SUCCESS;
import static org.glassfish.api.ActionReport.ExitCode.WARNING;

/**
 * Helper class used for more efficiently executing commands across multiple instances.
 * Inspired by {@link ClusterCommandHelper}.
 *
 * @author Andrew Pielage
 */
public class ScaleCommandHelper {

    private static final int ADMIN_DEFAULT_POOL_SIZE = 5;
    private static final Logger LOGGER = Logger.getLogger(ScaleCommandHelper.class.getName());

    private Domain domain;
    private CommandRunner commandRunner;
    private Subject subject;

    private ProgressStatus progressStatus;

    /**
     * Construct a ScaleCommandHelper
     *
     * @param commandRunner A CommandRunner to use for running commands
     */
    public ScaleCommandHelper(Domain domain, CommandRunner commandRunner, Subject subject) {
        this.domain = domain;
        this.commandRunner = commandRunner;
        this.subject = subject;
    }

    public ActionReport runCommandInParallelAcrossInstances(String commandName, ParameterMap parameterMap,
            List<String> targetNames) {
        return runCommandInParallelAcrossInstances(commandName, parameterMap, targetNames,
                commandRunner.getActionReport("plain"));
    }

    /**
     * Loop a subset of instances and execute a command for each one.
     * Inspired by {@link ClusterCommandHelper#runCommand(String, ParameterMap, String, AdminCommandContext, boolean)}.
     *
     * @param commandName The name of the command to run. The instance name will be used as the operand for the command.
     * @param parameterMap A map of parameters to use for the command. May be null if no parameters. When the command is
     *            executed for a server instance, the instance name is set as the DEFAULT parameter (operand)
     * @param targetNames The instance names of the cluster or deployment group to run the command against.
     * @param actionReport The action report that all action reports generated by this command will be added to.
     * @return An ActionReport containing the results
     */
    public ActionReport runCommandInParallelAcrossInstances(String commandName, ParameterMap parameterMap,
            List<String> targetNames, ActionReport actionReport) {
        // When we started
        long startTime = System.currentTimeMillis();


        // Check the instances exist
        Servers servers = domain.getServers();
        List<Server> targetServers = new ArrayList<>();
        for (String targetName : targetNames) {
            Server targetServer = servers.getServer(targetName);
            if (targetServer == null) {
                actionReport.setActionExitCode(WARNING);
                actionReport.appendMessage("\nSkipping target " + targetName +
                        " since it isn't an instance or doesn't exist");
            } else {
                targetServers.add(targetServer);
            }
        }

        int nInstances = targetServers.size();

        // We will save the name of the instances that worked and did not work so we can summarize our results.
        StringBuilder failedServerNames = new StringBuilder();
        StringBuilder succeededServerNames = new StringBuilder();
        List<String> waitingForServerNames = new ArrayList<>();
        ClusterCommandHelper.ReportResult reportResult = new ClusterCommandHelper.ReportResult();
        boolean failureOccurred = false;
        progressStatus = new ProgressStatusImpl();

        // Save command output to return in ActionReport
        StringBuilder output = new StringBuilder();

        // Optimize the order of server instances to avoid clumping
        ClusterCommandHelper clusterCommandHelper = new ClusterCommandHelper(domain, commandRunner);
        targetServers = clusterCommandHelper.optimizeServerListOrder(targetServers);

        // Holds responses from the threads running the command
        ArrayBlockingQueue<CommandRunnable> responseQueue = new ArrayBlockingQueue<>(nInstances);

        // Make the thread pool use the smaller of the number of instances or half the admin thread pool size
        int threadPoolSize = Math.min(nInstances, getAdminThreadPoolSize() / 2);
        if (threadPoolSize < 1) {
            threadPoolSize = 1;
        }

        ExecutorService threadPool = Executors.newFixedThreadPool(threadPoolSize);

        if (parameterMap == null) {
            parameterMap = new ParameterMap();
        }

        LOGGER.info(String.format(
                "Executing %s on %d instances using a thread pool of size %d: %s", commandName, nInstances,
                threadPoolSize, targetServers.stream().map(Server::getName).collect(Collectors.joining(", "))));

        progressStatus.setTotalStepCount(nInstances);
        progressStatus.progress(Strings.get("cluster.command.executing", commandName, nInstances));

        // Loop through instance names, construct the command for each instance name, and hand it off to the threadpool.
        for (Server server : targetServers) {
            String serverName = server.getName();
            waitingForServerNames.add(serverName);

            ParameterMap instanceParameterMap = new ParameterMap(parameterMap);
            // Set the instance name as the operand for the commnd
            instanceParameterMap.set("DEFAULT", serverName);

            ActionReport instanceReport = actionReport.addSubActionsReport();
            instanceReport.setActionExitCode(SUCCESS);
            CommandRunner.CommandInvocation invocation = commandRunner.getCommandInvocation(commandName, instanceReport,
                    subject);
            invocation.parameters(instanceParameterMap);

            String msg = commandName + " " + serverName;
            LOGGER.info(msg);

            // Wrap the command invocation in a runnable and hand it off
            // to the thread pool
            CommandRunnable cmdRunnable = new CommandRunnable(invocation, instanceReport, responseQueue);
            cmdRunnable.setName(serverName);
            threadPool.execute(cmdRunnable);
        }

        if (LOGGER.isLoggable(FINE)) {
            LOGGER.fine(String.format("%s commands queued, waiting for responses", commandName));
        }

        // Make sure we don't wait longer than the admin read timeout. Set our limit to be 3 seconds less.
        long adminTimeout = RemoteRestAdminCommand.getReadTimeout() - 3000;
        if (adminTimeout <= 0) {
            // This should never be the case
            adminTimeout = 57 * 1000;
        }

        if (LOGGER.isLoggable(FINE)) {
            LOGGER.fine(String.format("Initial command timeout: %d ms", adminTimeout));
        }

        // Now go get results from the response queue.
        for (int n = 0; n < nInstances; n++) {
            long timeLeft = adminTimeout - (System.currentTimeMillis() - startTime);
            if (timeLeft < 0) {
                timeLeft = 0;
            }
            CommandRunnable cmdRunnable = null;
            try {
                cmdRunnable = responseQueue.poll(timeLeft, MILLISECONDS);
            } catch (InterruptedException e) {
                // This thread has been interrupted. Abort
                threadPool.shutdownNow();
                String msg = Strings.get("cluster.command.interrupted", targetNames, n, nInstances, commandName);
                LOGGER.warning(msg);
                failureOccurred = true;
                // Re-establish interrupted state on thread
                Thread.currentThread().interrupt();
                break;
            }

            if (cmdRunnable == null) {
                // We've timed out.
                break;
            }
            String cname = cmdRunnable.getName();
            waitingForServerNames.remove(cname);
            ActionReport instanceReport = cmdRunnable.getActionReport();
            if (LOGGER.isLoggable(FINE)) {
                LOGGER.fine(String.format("Instance %d of %d (%s) has responded with %s", n + 1, nInstances, cname,
                        instanceReport.getActionExitCode()));
            }

            if (instanceReport.getActionExitCode() != SUCCESS) {
                // Bummer, the command had an error. Log and save output
                failureOccurred = true;
                failedServerNames.append(cname).append(" ");
                reportResult.failedServerNames.add(cname);
                String msg = cname + ": " + instanceReport.getMessage();
                LOGGER.severe(msg);
                msg = Strings.get("cluster.command.instancesFailed", commandName, cname);
                progressStatus.progress(1, msg);
            } else {
                // Command worked. Note that too.
                succeededServerNames.append(cname).append(" ");
                reportResult.succeededServerNames.add(cname);
                progressStatus.progress(1, cname);
            }
        }

        actionReport.setActionExitCode(SUCCESS);

        if (failureOccurred) {
            actionReport.setResultType(List.class, reportResult.failedServerNames);
        } else {
            actionReport.setResultType(List.class, reportResult.succeededServerNames);
        }

        // Display summary of started servers
        if (succeededServerNames.length() > 0) {
            output.append("\n").append(Strings.get("cluster.command.instancesSucceeded", commandName,
                    succeededServerNames));
        }

        if (failureOccurred) {
            // Display summary of failed servers if we have any
            output.append("\n").append(Strings.get("cluster.command.instancesFailed", commandName,
                    failedServerNames));
            if (succeededServerNames.length() > 0) {
                // At least one instance started. Warning.
                actionReport.setActionExitCode(WARNING);
            } else {
                // No instance started. Failure
                actionReport.setActionExitCode(FAILURE);
            }
        }

        // Check for server that did not respond
        if (!waitingForServerNames.isEmpty()) {
            String msg = Strings.get("cluster.command.instancesTimedOut", commandName,
                    waitingForServerNames.stream().map(Object::toString)
                            .collect(Collectors.joining(", ")));
            LOGGER.warning(msg);
            if (output.length() > 0) {
                output.append("\n");
            }
            output.append(msg);
            actionReport.setActionExitCode(WARNING);
        }

        actionReport.setMessage(output.toString());
        threadPool.shutdown();
        return actionReport;
    }

    /**
     * Get the size of the admin threadpool
     */
    private int getAdminThreadPoolSize() {
        // Get the DAS configuration
        Config config = domain.getConfigNamed("server-config");

        // Check for null - standalone instances may not have server-config
        if (config == null) {
            return ADMIN_DEFAULT_POOL_SIZE;
        }

        return new AdminEndpointDecider(config).getMaxThreadPoolSize();
    }
}
