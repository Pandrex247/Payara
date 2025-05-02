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

package fish.payara.extensions.autoscale.groups.nodes.admin;

import fish.payara.extensions.autoscale.groups.admin.GetScalingGroupConfigurationCommand;
import fish.payara.extensions.autoscale.groups.nodes.NodesScalingGroup;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandValidationException;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RestParam;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Gets the configuration of a {@link NodesScalingGroup}.
 *
 * @author Andrew Pielage
 */
@Service(name = "get-nodes-scaling-group-configuration")
@PerLookup
@ExecuteOn(RuntimeType.DAS)
@RestEndpoints({
        @RestEndpoint(configBean = NodesScalingGroup.class,
                opType = RestEndpoint.OpType.GET,
                path = "get-nodes-scaling-group-configuration",
                description = "Gets the configuration of the target Scaling Group",
                params = {
                        @RestParam(name = "id", value = "$parent")
                }
        )
})
public class GetNodesScalingGroupConfigurationCommand extends GetScalingGroupConfigurationCommand {

    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        try {
            validateParams();
        } catch (CommandValidationException commandValidationException) {
            adminCommandContext.getActionReport().setFailureCause(commandValidationException);
            adminCommandContext.getActionReport().setActionExitCode(ActionReport.ExitCode.FAILURE);
            return;
        }

        NodesScalingGroup nodesScalingGroup = null;

        // Search through the scaling groups, checking for the one with a reference to our requested deployment group
        for (NodesScalingGroup nodesScalingGroupIterator : scalingGroups.getScalingGroupsOfType(NodesScalingGroup.class)) {
            if (nodesScalingGroupIterator.getName().equals(name)) {
                nodesScalingGroup = nodesScalingGroupIterator;
                break;
            }
        }

        adminCommandContext.getActionReport().setMessage("Nodes Scaling Group: " + nodesScalingGroup.getName());
        adminCommandContext.getActionReport().appendMessage("\nConfig Ref: " + nodesScalingGroup.getConfigRef());
        adminCommandContext.getActionReport().appendMessage("\nDeployment Group Ref: " + nodesScalingGroup.getDeploymentGroupRef());
        adminCommandContext.getActionReport().appendMessage("\nNode Refs: " + String.join(", ", nodesScalingGroup.getNodeRefs()));

        Properties extraProps = new Properties();
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("name", nodesScalingGroup.getName());
        configMap.put("config", nodesScalingGroup.getConfigRef());
        configMap.put("deploymentGroup", nodesScalingGroup.getDeploymentGroupRef());
        configMap.put("nodes", nodesScalingGroup.getNodeRefs());

        extraProps.put("scalingGroupConfig", configMap);
        adminCommandContext.getActionReport().setExtraProperties(extraProps);
    }

    @Override
    protected void validateParams() throws CommandValidationException {
        super.validateParams();

        List<NodesScalingGroup> nodesScalingGroups = scalingGroups.getScalingGroupsOfType(NodesScalingGroup.class);

        if (nodesScalingGroups.isEmpty()) {
            throw new CommandValidationException("Scaling Group " + name + " is not a Nodes Scaling Group.");
        }

        boolean exists = false;
        for (NodesScalingGroup nodesScalingGroup : nodesScalingGroups) {
            if (nodesScalingGroup.getName().equals(name)) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            throw new CommandValidationException("Scaling Group " + name + " is not a Nodes Scaling Group.");
        }
    }
}
