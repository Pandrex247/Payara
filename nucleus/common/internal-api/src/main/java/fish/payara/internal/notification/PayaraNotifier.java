/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) [2020-2024] Payara Foundation and/or its affiliates. All rights reserved.
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
package fish.payara.internal.notification;

import org.jvnet.hk2.annotations.Contract;

/**
 * A contract for a service representing a dynamic PayaraNotifier with no
 * domain.xml configuration. For a notifier with domain.xml configuration see
 * {@link PayaraConfiguredNotifier}.
 */
@Contract
public interface PayaraNotifier {

    /**
     * Receives a notification and processes if certain conditions are met.
     *
     * @param event The notification.
     */
    default void tryHandleNotification (PayaraNotification event) {
        this.handleNotification(event);
    }

    /**
     * Receive notifications from the notification service.
     * @param event the notification
     */
    void handleNotification(PayaraNotification event);

    /**
     * Initialise any required properties. Called when the notifier is initialised,
     * or configuration values are changed.
     */
    default void bootstrap() {
    };

    /**
     * Destroy any objects before configuration values are changed. Called when the
     * server shuts down or before the notifier is reinitialised.
     */
    default void destroy() {
    };

}