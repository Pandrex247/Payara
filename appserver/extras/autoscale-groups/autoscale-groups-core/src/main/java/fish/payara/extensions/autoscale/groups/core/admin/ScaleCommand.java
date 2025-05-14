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

package fish.payara.extensions.autoscale.groups.core.admin;

import com.sun.enterprise.util.StringUtils;
import fish.payara.enterprise.config.serverbeans.DeploymentGroups;
import fish.payara.extensions.autoscale.groups.Scaler;
import fish.payara.extensions.autoscale.groups.ScalingGroups;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.CommandValidationException;
import org.glassfish.hk2.api.ServiceLocator;

import jakarta.inject.Inject;
import java.util.logging.Logger;

import static fish.payara.extensions.autoscale.groups.Scaler.AUTOSCALE_MAXSCALE_DEFAULT;
import static fish.payara.extensions.autoscale.groups.Scaler.AUTOSCALE_MAXSCALE_PROP;

/**
 *
 *
 * @author Andrew Pielage
 */
public abstract class ScaleCommand implements AdminCommand {

    @Param(name = "target", primary = true)
    protected String target;

    @Param(name = "quantity", shortName = "q", optional = true, defaultValue = "1")
    protected int quantity;

    @Inject
    protected ServiceLocator serviceLocator;

    @Inject
    protected DeploymentGroups deploymentGroups;

    @Inject
    protected ScalingGroups scalingGroups;

    protected void validateParams() throws CommandValidationException {
        if (deploymentGroups == null) {
            deploymentGroups = serviceLocator.getService(DeploymentGroups.class);
            if (deploymentGroups == null) {
                throw new CommandValidationException("Could not find DeploymentGroups config bean!");
            }
        }

        if (scalingGroups == null) {
            scalingGroups = serviceLocator.getService(ScalingGroups.class);
            if (scalingGroups == null) {
                throw new CommandValidationException("Could not find ScalingGroups config bean!");
            }

            if (scalingGroups.getScalingGroups().isEmpty()) {
                throw new CommandValidationException("No Scaling Groups found!");
            }
        }

        if (!StringUtils.ok(target)) {
            throw new CommandValidationException("Target must be a valid Deployment Group!");
        }

        if (deploymentGroups.getDeploymentGroup(target) == null) {
            throw new CommandValidationException(("Deployment Group does not exist!"));
        }

        int maxScale = Integer.getInteger(AUTOSCALE_MAXSCALE_PROP, AUTOSCALE_MAXSCALE_DEFAULT);

        if (maxScale < 1) {
            Logger.getLogger(Scaler.class.getName()).warning(
                    AUTOSCALE_MAXSCALE_PROP + " property evaluated to less than 1, defaulting to " +
                            AUTOSCALE_MAXSCALE_DEFAULT);
            maxScale = AUTOSCALE_MAXSCALE_DEFAULT;
        }

        if (quantity < 1 || quantity > maxScale) {
            throw new CommandValidationException("Quantity must be greater than 0!");
        }
    }
}
