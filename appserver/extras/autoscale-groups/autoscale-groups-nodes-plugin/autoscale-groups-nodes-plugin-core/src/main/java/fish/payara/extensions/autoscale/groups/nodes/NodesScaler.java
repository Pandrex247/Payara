/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2021-2024 Payara Foundation and/or its affiliates. All rights reserved.
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

package fish.payara.extensions.autoscale.groups.nodes;

import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Nodes;
import com.sun.enterprise.config.serverbeans.Server;
import com.sun.enterprise.util.StringUtils;
import fish.payara.enterprise.config.serverbeans.DeploymentGroup;
import fish.payara.extensions.autoscale.groups.Scaler;
import fish.payara.extensions.autoscale.groups.ScalerFor;
import fish.payara.extensions.autoscale.groups.ScalingGroup;
import fish.payara.extensions.autoscale.groups.core.admin.ScaleCommandHelper;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.CommandException;
import org.glassfish.api.admin.CommandRunner;
import org.glassfish.api.admin.CommandValidationException;
import org.glassfish.api.admin.ParameterMap;
import org.glassfish.internal.api.InternalSystemAdministrator;
import org.jvnet.hk2.annotations.Service;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * {@link Scaler} implementation service which performs the scale up and down procedures across a selection of
 * {@link com.sun.enterprise.config.serverbeans.Nodes Nodes}.
 *
 * @author Andrew Pielage
 */
@Service
@ScalerFor(NodesScalingGroup.class)
public class NodesScaler extends Scaler {

    @Inject
    private InternalSystemAdministrator internalSystemAdministrator;

    @Inject
    private CommandRunner commandRunner;

    @Inject
    private Nodes nodes;

    private static final Logger LOGGER = Logger.getLogger(NodesScaler.class.getName());

    @Override
    protected void validate(int numberOfInstances, ScalingGroup scalingGroup) throws CommandValidationException {
        super.validate(numberOfInstances, scalingGroup);

        // Check that we have a command runner
        if (commandRunner == null) {
            commandRunner = serviceLocator.getService(CommandRunner.class);

            if (commandRunner == null) {
                throw new CommandValidationException(
                        "Could not find or initialise CommandRunner to execute commands with!");
            }
        }

        // Check that we have a system admin
        if (internalSystemAdministrator == null) {
            internalSystemAdministrator = serviceLocator.getService(InternalSystemAdministrator.class);

            if (internalSystemAdministrator == null) {
                throw new CommandValidationException(
                        "Could not find or initialise InternalSystemAdministrator to execute commands with!");
            }
        }

        List<NodesScalingGroup> nodesScalingGroups = scalingGroups.getScalingGroupsOfType(NodesScalingGroup.class);

        // Check that we actually have some nodes scaling groups
        if (nodesScalingGroups.isEmpty()) {
            throw new CommandValidationException("No Nodes Scaling Groups found!");
        }

        // Check that the nodes scaling group exists
        boolean exists = false;
        for (NodesScalingGroup nodesScalingGroup : nodesScalingGroups) {
            if (nodesScalingGroup.getName().equals(scalingGroup.getName())) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            throw new CommandValidationException("Scaling Group " + scalingGroup.getName() +
                    " is not a Nodes Scaling Group.");
        }

        // Check we can find the Nodes.
        if (nodes == null) {
            nodes = serviceLocator.getService(Nodes.class);

            if (nodes == null) {
                throw new CommandValidationException("Could not find Nodes!");
            }
        }

        // Check that the node references are valid
        for (String nodeRef : ((NodesScalingGroup) scalingGroup).getNodeRefs()) {
            if (!StringUtils.ok(nodeRef)) {
                throw new CommandValidationException("Scaling Group has an invalid node reference configured: " + nodeRef);
            }

            if (nodes.getNode(nodeRef) == null) {
                throw new CommandValidationException("Node " + nodeRef + " does not appear to exist!");
            }
        }


    }

    @Override
    public ActionReport scaleUp(int numberOfNewInstances, ScalingGroup scalingGroup) {
        ActionReport actionReport = commandRunner.getActionReport("plain");
        try {
            validate(numberOfNewInstances, scalingGroup);
        } catch (CommandValidationException commandValidationException) {
            actionReport.setMessage("Scale up operation cancelled: an error was encountered during validation");
            actionReport.setFailureCause(commandValidationException);
            actionReport.setActionExitCode(ActionReport.ExitCode.FAILURE);
            return actionReport;
        }

        try {
            // Create the instances (we currently fail out if we fail to create a single one)
            List<String> instanceNames = createInstances(numberOfNewInstances, scalingGroup,
                    actionReport.addSubActionsReport());
            // Attempt to start the instances
            startInstances(instanceNames, actionReport.addSubActionsReport());
        } catch (CommandException commandException) {
            actionReport.setFailureCause(commandException);
            actionReport.setActionExitCode(ActionReport.ExitCode.FAILURE);
            return actionReport;
        }

        return actionReport;
    }

    /**
     * Creates the requested number of instances using the {@link NodesScalingGroup scaling group} config
     *
     * @param numberOfNewInstances The number of instances to create
     * @param scalingGroup The scaling group we're creating the instances against
     * @param actionReport The action report we want to add out command outputs to
     * @return A List containing the names of all created instances
     * @throws CommandException If there's an error creating any instances.
     */
    private List<String> createInstances(int numberOfNewInstances, ScalingGroup scalingGroup,
            ActionReport actionReport) throws CommandException {
        List<String> instanceNames = new ArrayList<>();

        // Get the starting balance of instances to nodes
        DeploymentGroup deploymentGroup = deploymentGroups.getDeploymentGroup(scalingGroup.getDeploymentGroupRef());
        Map<String, Integer> scalingGroupBalance = getNodesInstanceBalance(
                deploymentGroup.getInstances(), ((NodesScalingGroup) scalingGroup).getNodeRefs());

        // Determine where to create each instance
        int instanceCounter = 0;
        while (instanceCounter < numberOfNewInstances) {
            // Get the node with the least instances
            Map.Entry<String, Integer> minNodeEntry = Collections.min(
                    scalingGroupBalance.entrySet(), Comparator.comparing(Map.Entry::getValue));

            // Create the parameter map for the create-instance command
            ParameterMap parameterMap = new ParameterMap();
            parameterMap.add("deploymentgroup", scalingGroup.getDeploymentGroupRef());
            if (!scalingGroup.getConfigRef().equals("default-config")) {
                parameterMap.add("config", scalingGroup.getConfigRef());
            }
            parameterMap.add("autoname", "true");
            parameterMap.add("terse", "true");
            parameterMap.add("node", minNodeEntry.getKey());

            // Execute the command with our parameters - we don't want to execute them in a parallel manner since
            // we'll run into issues with locking on the config beans
            ActionReport subActionReport = actionReport.addSubActionsReport();
            CommandRunner.CommandInvocation createInstanceCommand = commandRunner.getCommandInvocation(
                    "create-instance", subActionReport, internalSystemAdministrator.getSubject());
            createInstanceCommand.parameters(parameterMap);
            createInstanceCommand.execute();

            // Check if we have any failures - we don't want to continue if any failed
            if (subActionReport.hasFailures()) {
                LOGGER.severe("Encountered an error scaling up instances. " +
                        instanceCounter + " were created out of the requested " + numberOfNewInstances + ". " +
                        "The error encountered was: " + subActionReport.getFailureCause().getMessage());
                throw new CommandException("Encountered an error scaling up instances.",
                        subActionReport.getFailureCause());
            }

            // The output of the create-instance command with the "terse" option should be in the format
            // "The instance, wibbles-bibbles, was created on host tiddles"
            String actionReportMessage = subActionReport.getMessage();
            if (actionReportMessage.contains("was created on host")) {
                try {
                    instanceNames.add(actionReportMessage.substring(
                            actionReportMessage.indexOf(", ") + 2, actionReportMessage.lastIndexOf(", ")));
                } catch (StringIndexOutOfBoundsException stringIndexOutOfBoundsException) {
                    throw new CommandException(
                            "Encountered an error scaling up instances. Could not determine instance name");
                }
            } else {
                throw new CommandException(
                        "Encountered an error scaling up instances. Could not determine instance name");
            }

            // Adjust the node balance
            scalingGroupBalance.put(minNodeEntry.getKey(), minNodeEntry.getValue() + 1);

            // Increment the counter for our loop
            instanceCounter++;
        }

        return instanceNames;
    }

    /**
     * Starts the instances in parallel using {@link ScaleCommandHelper}.
     * @param instanceNames The names of the instances to start
     * @param actionReport The {@link ActionReport} we want to add out command outputs to
     */
    private void startInstances(List<String> instanceNames, ActionReport actionReport) {
        ScaleCommandHelper scaleCommandHelper = new ScaleCommandHelper(serviceLocator.getService(Domain.class),
                commandRunner, internalSystemAdministrator.getSubject());
        scaleCommandHelper.runCommandInParallelAcrossInstances("start-instance", new ParameterMap(),
                instanceNames, actionReport.addSubActionsReport());
    }

    @Override
    public ActionReport scaleDown(int numberOfInstancesToRemove, ScalingGroup scalingGroup) {
        ActionReport actionReport = commandRunner.getActionReport("plain");
        try {
            validate(numberOfInstancesToRemove, scalingGroup);
        } catch (CommandValidationException commandValidationException) {
            actionReport.setMessage("Scale down operation cancelled: an error was encountered during validation");
            actionReport.setFailureCause(commandValidationException);
            actionReport.setActionExitCode(ActionReport.ExitCode.FAILURE);
            return actionReport;
        }

        // Determine which instances to stop, attempting to keep the nodes balanced
        List<String> instanceNames = determineInstancesToStop(numberOfInstancesToRemove, scalingGroup);
        // Stop the instances in parallel
        stopInstances(instanceNames, actionReport.addSubActionsReport());
        // Delete the instances sequentially
        deleteInstances(instanceNames, actionReport.addSubActionsReport());

        return actionReport;
    }

    /**
     * Determines the names of which instances to stop in the scaling group, attempting to keep the number of instances
     * on nodes balanced.
     *
     * @param numberOfInstancesToRemove The number of instances to remove
     * @param scalingGroup The scaling group to remove the instances from
     * @return A list of names of the instances to stop
     */
    private List<String> determineInstancesToStop(int numberOfInstancesToRemove, ScalingGroup scalingGroup) {
        List<String> instanceNames = new ArrayList<>();

        // Quick check: will we just be removing all instances? If so we can skip trying to figure out the balance
        boolean removeAll = false;
        DeploymentGroup deploymentGroup = deploymentGroups.getDeploymentGroup(scalingGroup.getDeploymentGroupRef());
        List<Server> instances = deploymentGroup.getInstances();

        if (instances.size() <= numberOfInstancesToRemove) {
            removeAll = true;
        }

        if (removeAll) {
            for (Server server : instances) {
                instanceNames.add(server.getName());
            }
            return instanceNames;
        }

        // Get the balance of instances across the nodes within this Deployment Group and Scaling Group
        List<String> nodeRefs = ((NodesScalingGroup) scalingGroup).getNodeRefs();
        Map<String, Integer> scalingGroupBalance = getNodesInstanceBalance(instances, nodeRefs);

        // Loop until we've removed the requested number of instances
        int instanceCounter = numberOfInstancesToRemove;
        while (instanceCounter > 0) {
            // Get the node with the most instances
            Map.Entry<String, Integer> maxNodeEntry = Collections.max(
                    scalingGroupBalance.entrySet(), Comparator.comparing(Map.Entry::getValue));

            // Pick an instance from that node
            for (Server server : instances) {
                if (!instanceNames.contains(server.getName()) && server.getNodeRef().equals(maxNodeEntry.getKey())) {
                    instanceNames.add(server.getName());
                    scalingGroupBalance.put(maxNodeEntry.getKey(), maxNodeEntry.getValue() - 1);
                    break;
                }
            }

            instanceCounter--;
        }

        return instanceNames;
    }

    /**
     * Returns the number of instances on each node within the scaling group config.
     *
     * @param instances The list of {@link Server Servers} to get the balance of.
     * @param nodeRefs The nodes to get the balance of.
     * @return A Map containing how many of the given instances are on each node.
     */
    private Map<String, Integer> getNodesInstanceBalance(List<Server> instances, List<String> nodeRefs) {
        Map<String, Integer> scalingGroupBalance = new HashMap<>();
        for (String nodeRef : nodeRefs) {
            scalingGroupBalance.put(nodeRef, 0);
        }

        for (Server instance : instances) {
            // We only care about the balance of instances on nodes in our current config
            if (nodeRefs.contains(instance.getNodeRef())) {
                scalingGroupBalance.put(instance.getNodeRef(), scalingGroupBalance.get(instance.getNodeRef()) + 1);
            }
        }

        return scalingGroupBalance;
    }

    /**
     * Stops the instances in parallel using {@link ScaleCommandHelper}.
     *
     * @param instanceNames The names of the instances to stop
     * @param actionReport The action report to add the command outputs to
     */
    private void stopInstances(List<String> instanceNames, ActionReport actionReport) {
        ScaleCommandHelper scaleCommandHelper = new ScaleCommandHelper(serviceLocator.getService(Domain.class),
                commandRunner, internalSystemAdministrator.getSubject());
        scaleCommandHelper.runCommandInParallelAcrossInstances("stop-instance", new ParameterMap(),
                instanceNames, actionReport.addSubActionsReport());
    }

    /**
     * Deletes instances sequentially.
     *
     * @param instanceNames The names of the instances to stop.
     * @param actionReport The action report to add the command outputs to.
     */
    private void deleteInstances(List<String> instanceNames, ActionReport actionReport) {
        for (String instanceName : instanceNames) {
            CommandRunner.CommandInvocation deleteInstanceCommand = commandRunner.getCommandInvocation(
                    "delete-instance", actionReport.addSubActionsReport(), internalSystemAdministrator.getSubject());

            ParameterMap parameterMap = new ParameterMap();
            // Primary parameter is called DEFAULT, regardless of its actual name
            parameterMap.add("DEFAULT", instanceName);

            deleteInstanceCommand.parameters(parameterMap);
            deleteInstanceCommand.execute();
        }
    }
}
