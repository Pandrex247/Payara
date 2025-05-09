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

package fish.payara.extensions.autoscale.groups.nodes.admin;

import com.sun.enterprise.config.serverbeans.Nodes;
import com.sun.enterprise.util.StringUtils;
import fish.payara.extensions.autoscale.groups.admin.SetScalingGroupConfigurationCommand;
import fish.payara.extensions.autoscale.groups.nodes.NodesScalingGroup;
import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandValidationException;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RestParam;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.TransactionFailure;

import jakarta.inject.Inject;
import java.util.List;

/**
 * Sets the configuration of a {@link NodesScalingGroup}.
 *
 * @author Andrew Pielage
 */
@Service(name = "set-nodes-scaling-group-configuration")
@PerLookup
@ExecuteOn(RuntimeType.DAS)
@RestEndpoints({
        @RestEndpoint(configBean = NodesScalingGroup.class,
                opType = RestEndpoint.OpType.POST,
                path = "set-nodes-scaling-group-configuration",
                description = "Sets the configuration of the target Scaling Group",
                params = {
                        @RestParam(name = "name", value = "$parent")
                }
        )
})
public class SetNodesScalingGroupConfigurationCommand extends SetScalingGroupConfigurationCommand {

    @Param(name = "nodes", optional = true)
    private List<String> nodeRefs;

    @Inject
    protected Nodes nodes;

    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        try {
            validateParams();
        } catch (CommandValidationException commandValidationException) {
            adminCommandContext.getActionReport().setFailureCause(commandValidationException);
            adminCommandContext.getActionReport().setActionExitCode(ActionReport.ExitCode.FAILURE);
            return;
        }

        try {
            ConfigSupport.apply(scalingGroupsProxy -> {
                ConfigSupport.apply(nodesScalingGroupProxy -> {
                    if (StringUtils.ok(deploymentGroupRef)) {
                        nodesScalingGroupProxy.setDeploymentGroupRef(deploymentGroupRef);
                    }

                    if (StringUtils.ok(configRef)) {
                        nodesScalingGroupProxy.setConfigRef(configRef);
                    }

                    if (nodeRefs != null && !nodeRefs.isEmpty()) {
                        nodesScalingGroupProxy.getNodeRefs().clear();
                        for (String nodeRef : nodeRefs) {
                            // Ensure no duplicates
                            if (!nodesScalingGroupProxy.getNodeRefs().contains(nodeRef)) {
                                nodesScalingGroupProxy.getNodeRefs().add(nodeRef);
                            }
                        }
                    }

                    return nodesScalingGroupProxy;
                }, (NodesScalingGroup) scalingGroupsProxy.getScalingGroup(name));

                return scalingGroupsProxy;
            }, scalingGroups);
        } catch (TransactionFailure transactionFailure) {
            adminCommandContext.getActionReport().setActionExitCode(ActionReport.ExitCode.FAILURE);
            adminCommandContext.getActionReport().setFailureCause(transactionFailure);
        }
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

        if (nodeRefs != null && !nodeRefs.isEmpty()) {
            for (String nodeRef : nodeRefs) {
                if (!StringUtils.ok(nodeRef) || nodes.getNode(nodeRef) == null) {
                    throw new CommandValidationException("Node name " + nodeRef + " is not valid or doesn't exist");
                }

                if (!nodeRef.equals(nodes.getDefaultLocalNode().getName())) {
                    String nodeType = nodes.getNode(nodeRef).getType();
                    if (nodeType.equals("CONFIG") || nodeType.equals("TEMP")) {
                        throw new CommandValidationException("Node " + nodeRef + " is not a valid type: " + nodeType);
                    }
                }
            }
        }
    }
}
