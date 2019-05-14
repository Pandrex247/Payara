package fish.payara.docker.instance.admin;

import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Node;
import com.sun.enterprise.config.serverbeans.Nodes;
import com.sun.enterprise.config.serverbeans.Server;
import com.sun.enterprise.config.serverbeans.Servers;
import fish.payara.docker.instance.DockerInstanceConstants;
import fish.payara.docker.node.DockerNodeConstants;
import org.glassfish.api.ActionReport;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.CommandRunner;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.ParameterMap;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import javax.json.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.glassfish.api.ActionReport.ExitCode.SUCCESS;

@Service(name = "_create-docker-container")
@PerLookup
@ExecuteOn({RuntimeType.DAS})
@RestEndpoints({
        @RestEndpoint(configBean = Domain.class,
                opType = RestEndpoint.OpType.POST,
                path = "_create-docker-container",
                description = "Create a Docker Container and Instance on the specified nodeName")
})
public class CreateDockerContainerCommand implements AdminCommand {

    private static final Logger logger = Logger.getLogger(CreateDockerContainerCommand.class.getName());

    @Param(name = "nodeName", alias = "node")
    String nodeName;

    @Param(name = "instanceName", alias = "instance", primary = true)
    String instanceName;

    @Param(name = "containerConfig", optional = true, alias = "containerconfig", separator = ':')
    Properties containerConfig;

    @Inject
    private Servers servers;

    @Inject
    private Nodes nodes;

    @Inject
    private CommandRunner commandRunner;

    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        ActionReport actionReport = adminCommandContext.getActionReport();

        // Get the Node Object and validate
        Node node = nodes.getNode(nodeName);
        if (node == null) {
            actionReport.failure(logger, "No nodeName found with given name: " + nodeName);
            return;
        }

        if (!node.getType().equals("DOCKER")) {
            actionReport.failure(logger, "Node is not of type DOCKER, nodeName is of type: " + node.getType());
            return;
        }

        // Get the DAS hostname and port and validate
        String dasHost = "";
        String dasPort = "";
        for (Server server : servers.getServer()) {
            if (server.isDas()) {
                dasHost = server.getAdminHost();
                dasPort = Integer.toString(server.getAdminPort());
                break;
            }
        }

        if (dasHost == null || dasHost.equals("") || dasPort == null || dasPort.equals("")) {
            actionReport.failure(logger, "Could not retrieve DAS host address or port");
            return;
        }

        // Get the instance that we've got registered in the domain.xml to grab its config
        Server server = servers.getServer(instanceName);
        if (server == null) {
            actionReport.failure(logger, "No instance registered in domain with name: " + instanceName);
            return;
        }

        // Remove all non-docker related properties
        for (String property : containerConfig.stringPropertyNames()) {
            if (!property.startsWith("Docker.")) {
                containerConfig.remove(property);
            }
        }

        // Create the JSON Object to send
        JsonObject jsonObject = constructJsonRequest(node, dasHost, dasPort);

        // Create web target with query
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = null;
        if (Boolean.valueOf(node.getUseTls())) {
            webTarget = client.target("https://"
                    + node.getNodeHost()
                    + ":"
                    + node.getDockerPort()
                    + "/containers/create");
        } else {
            webTarget = client.target("http://"
                    + node.getNodeHost()
                    + ":"
                    + node.getDockerPort()
                    + "/containers/create");
        }
        webTarget = webTarget.queryParam(DockerNodeConstants.DOCKER_NAME_KEY, instanceName);

        // Send the POST request
        Response response = null;
        try {
            response = webTarget.queryParam(DockerNodeConstants.DOCKER_NAME_KEY, instanceName)
                    .request(MediaType.APPLICATION_JSON).post(Entity.entity(jsonObject, MediaType.APPLICATION_JSON));
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Encountered an exception sending request to Docker: \n", ex);
        }

        // Check status of response and act on result
        if (response != null) {
            Response.StatusType responseStatus = response.getStatusInfo();
            if (!responseStatus.getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
                // Log the failure
                actionReport.failure(logger, "Failed to create Docker Container: \n"
                        + responseStatus.getReasonPhrase());

                // Attempt to unregister the instance so we don't have an instance entry that can't be used
                unregisterInstance(adminCommandContext, actionReport);
            }
        } else {
            // If the response is null, clearly something has gone wrong, so treat is as a failure
            actionReport.failure(logger, "Failed to create Docker Container");

            // Attempt to unregister the instance so we don't have an instance entry that can't be used
            unregisterInstance(adminCommandContext, actionReport);
        }
    }

    private JsonObject constructJsonRequest(Node node, String dasHost, String dasPort) {
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();

        jsonObjectBuilder.add(DockerNodeConstants.DOCKER_IMAGE_KEY, node.getDockerImage());

        // If no user properties specified, go with defaults
        if (containerConfig.isEmpty()) {
            jsonObjectBuilder.add(DockerNodeConstants.DOCKER_HOST_CONFIG_KEY, Json.createObjectBuilder()
                    .add(DockerNodeConstants.DOCKER_MOUNTS_KEY, Json.createArrayBuilder()
                    .add(Json.createObjectBuilder()
                            .add("Type", "bind")
                            .add("Source", node.getDockerPasswordFile())
                            .add("Target", DockerNodeConstants.PAYARA_PASSWORD_FILE)
                            .add("ReadOnly", true)))
                    .add(DockerNodeConstants.DOCKER_NETWORK_MODE_KEY, "host"));
            jsonObjectBuilder.add(DockerInstanceConstants.DOCKER_CONTAINER_ENV, Json.createArrayBuilder()
                    .add(DockerNodeConstants.PAYARA_DAS_HOST + "=" + dasHost)
                    .add(DockerNodeConstants.PAYARA_DAS_PORT + "=" + dasPort)
                    .add(DockerNodeConstants.PAYARA_NODE_NAME + "=" + nodeName)
                    .add(DockerInstanceConstants.INSTANCE_NAME + "=" + instanceName));
        } else {
            translatePropertyValuesToJson(jsonObjectBuilder, dasHost, dasPort);
        }

        return jsonObjectBuilder.build();
    }

    private JsonObjectBuilder translatePropertyValuesToJson(JsonObjectBuilder jsonObjectBuilder, String dasHost,
            String dasPort) {
        for (Object propertyKey : containerConfig.keySet()) {
            // Should always be a String
            String property = (String) propertyKey;

            // Check if any of the properties are in the same namespace as our defaults
            if (property.startsWith(DockerNodeConstants.DOCKER_HOST_CONFIG_KEY)) {
                addHostConfigProperties(jsonObjectBuilder);
                continue;
            } else if (property.startsWith(DockerInstanceConstants.DOCKER_CONTAINER_ENV)) {
                addEnvProperties(jsonObjectBuilder, dasHost, dasPort);
                continue;
            }

            // Check if this is a nested property
            if (property.contains(".")) {
                // Recurse through the properties and add any other properties that fall under the same namespace
                addNestedProperties(jsonObjectBuilder, property);
            } else {
                // Not a nested property, add it as is
                String propertyValue = containerConfig.getProperty(property);
                addPropertyToJson(jsonObjectBuilder, property, propertyValue);
                containerConfig.remove("property");
            }
        }

        return jsonObjectBuilder;
    }

    private void addHostConfigProperties(JsonObjectBuilder jsonObjectBuilder) {
        JsonObjectBuilder hostConfigObjectBuilder = Json.createObjectBuilder();
        List<String> hostConfigProperties = new ArrayList<>();
        for (String property : containerConfig.stringPropertyNames()) {
            if (property.startsWith(DockerNodeConstants.DOCKER_HOST_CONFIG_KEY)) {
                String hostConfigProperty = property.substring(property.indexOf(".") + 1);
                hostConfigProperties.add(hostConfigProperty);
            }
        }

        // Sort them into alphabetical order to group them all related properties together
        hostConfigProperties.sort(Comparator.comparing(String::toString));

        // Populate HostConfig defaults map so we can check if any get overridden
        Map<String, Boolean> defaultsOverridden = new HashMap<>();
        defaultsOverridden.put(DockerNodeConstants.DOCKER_MOUNTS_KEY, false);
        defaultsOverridden.put(DockerNodeConstants.DOCKER_MOUNTS_READONLY_KEY, false);
        defaultsOverridden.put(DockerNodeConstants.DOCKER_MOUNTS_SOURCE_KEY, false);
        defaultsOverridden.put(DockerNodeConstants.DOCKER_MOUNTS_TARGET_KEY, false);
        defaultsOverridden.put(DockerNodeConstants.DOCKER_MOUNTS_TYPE_KEY, false);
        defaultsOverridden.put(DockerNodeConstants.DOCKER_NETWORK_MODE_KEY, false);

        for (String property : hostConfigProperties) {
            // Check if property overrides any of our defaults
            if (!defaultsOverridden.get(DockerNodeConstants.DOCKER_MOUNTS_KEY)
                    && property.startsWith(DockerNodeConstants.DOCKER_MOUNTS_KEY)) {
                defaultsOverridden.put(DockerNodeConstants.DOCKER_MOUNTS_KEY, true);
            }

            switch (property) {
                case DockerNodeConstants.DOCKER_MOUNTS_READONLY_KEY:
                    defaultsOverridden.put(DockerNodeConstants.DOCKER_MOUNTS_READONLY_KEY, true);
                    break;
                case DockerNodeConstants.DOCKER_MOUNTS_SOURCE_KEY:
                    defaultsOverridden.put(DockerNodeConstants.DOCKER_MOUNTS_SOURCE_KEY, true);
                    break;
                case DockerNodeConstants.DOCKER_MOUNTS_TARGET_KEY:
                    defaultsOverridden.put(DockerNodeConstants.DOCKER_MOUNTS_TARGET_KEY, true);
                    break;
                case DockerNodeConstants.DOCKER_MOUNTS_TYPE_KEY:
                    defaultsOverridden.put(DockerNodeConstants.DOCKER_MOUNTS_TYPE_KEY, true);
                    break;
                case DockerNodeConstants.DOCKER_NETWORK_MODE_KEY:
                    defaultsOverridden.put(DockerNodeConstants.DOCKER_NETWORK_MODE_KEY, true);
                    break;
            }

            // Check if the property has any extra nested properties
            if (property.contains("\\.")) {
                List<String> propertiesAdded = recurseOverNested(hostConfigObjectBuilder, hostConfigProperties,
                        property, defaultsOverridden, false);
                for (String addedProperty : propertiesAdded) {
                    containerConfig.remove(DockerNodeConstants.DOCKER_HOST_CONFIG_KEY + "." + addedProperty);
                }
            } else  {
                // Not a nested property, add it as is
                String propertyValue = containerConfig.getProperty(DockerNodeConstants.DOCKER_HOST_CONFIG_KEY + "." + property);
                addPropertyToJson(hostConfigObjectBuilder, property, propertyValue);
                containerConfig.remove(DockerNodeConstants.DOCKER_HOST_CONFIG_KEY + "." + property);
            }
        }

        // Add any remaining defaults
        if (!defaultsOverridden.get(DockerNodeConstants.DOCKER_MOUNTS_KEY)) {
            hostConfigObjectBuilder.add(DockerNodeConstants.DOCKER_MOUNTS_KEY, Json.createArrayBuilder()
                    .add(Json.createObjectBuilder()
                            .add("Type", "bind")
                            .add("Source", nodes.getNode(nodeName).getDockerPasswordFile())
                            .add("Target", DockerNodeConstants.PAYARA_PASSWORD_FILE)
                            .add("ReadOnly", true)));
        }

        if (!defaultsOverridden.get(DockerNodeConstants.DOCKER_NETWORK_MODE_KEY)) {
            hostConfigObjectBuilder.add(DockerNodeConstants.DOCKER_NETWORK_MODE_KEY, "host");
        }

        // Finally, add host config object to final Json request object
        jsonObjectBuilder.add(DockerNodeConstants.DOCKER_HOST_CONFIG_KEY, hostConfigObjectBuilder);
    }

    private List<String> recurseOverNested(JsonObjectBuilder jsonObjectBuilder, List<String> sortedProperties,
            String property, Map<String, Boolean> defaultsOverridden, boolean recursing) {
        List<String> propertiesToRemove = new ArrayList<>();
        List<String> propertyComponents = Arrays.asList(property.split("\\."));

        Map<String, JsonObjectBuilder> propertyComponentObjectBuilders = new HashMap<>();
        for (String propertyComponent : propertyComponents) {
            // We don't need to make a builder for the last component, as it isn't an object
            if (propertyComponents.indexOf(propertyComponent) != propertyComponents.size() - 1) {
                propertyComponentObjectBuilders.put(propertyComponent, Json.createObjectBuilder());
            }
        }

        // Add to defaults overridden map if required - only need to check if recursing as we already added the initial
        // property
        if (recursing) {
            switch (property) {
                case DockerNodeConstants.DOCKER_MOUNTS_READONLY_KEY:
                    defaultsOverridden.put(DockerNodeConstants.DOCKER_MOUNTS_READONLY_KEY, true);
                    break;
                case DockerNodeConstants.DOCKER_MOUNTS_SOURCE_KEY:
                    defaultsOverridden.put(DockerNodeConstants.DOCKER_MOUNTS_SOURCE_KEY, true);
                    break;
                case DockerNodeConstants.DOCKER_MOUNTS_TARGET_KEY:
                    defaultsOverridden.put(DockerNodeConstants.DOCKER_MOUNTS_TARGET_KEY, true);
                    break;
                case DockerNodeConstants.DOCKER_MOUNTS_TYPE_KEY:
                    defaultsOverridden.put(DockerNodeConstants.DOCKER_MOUNTS_TYPE_KEY, true);
                    break;
                // We don't need to check for the Network Mode override as this isn't a nested property - it would never
                // be overridden from here
            }
        }

        // Add lowest level property component to immediate parent builder (second last in list)
        String immediateParent = propertyComponents.get(propertyComponents.size() - 2);
        JsonObjectBuilder lowestParentObjectBuilder = propertyComponentObjectBuilders.get(immediateParent);
        String propertyComponentKey = propertyComponents.get(propertyComponents.size() - 1);
        String propertyValue = containerConfig.getProperty(DockerNodeConstants.DOCKER_HOST_CONFIG_KEY + "." + property);
        addPropertyToJson(lowestParentObjectBuilder, propertyComponentKey, propertyValue);
        propertiesToRemove.add(property);

        // If there are more properties, check if the immediate parent has any extra children by checking the next property,
        // moving up the list until we reach the root property component
        if (sortedProperties.indexOf(property) + 1 != sortedProperties.size()) {
            String nextProperty = sortedProperties.get(sortedProperties.indexOf(property) + 1);
            for (int i = propertyComponents.size() - 2; i > -1; i--) {
                String remainingParents = "";
                for (int j = 0; j < i + 1; j++) {
                    remainingParents += propertyComponents.get(j);

                    if (j != i) {
                        remainingParents += ".";
                    }
                }

                if (nextProperty.startsWith(remainingParents)) {
                    // We've found a property in the same namespace, recurse into this method to add this next property
                    // to the object builder
                    propertiesToRemove.addAll(recurseOverNested(
                            propertyComponentObjectBuilders.get(propertyComponents.get(i)),
                            sortedProperties,
                            nextProperty, defaultsOverridden, true));
                    // We don't want to keep looping as we'll end up adding stuff added in the recursed method call
                    // above
                    break;
                } else {
                    if (i != 0) {
                        // If we haven't found another property in the same namespace, add the current object builder to its parent
                        JsonObjectBuilder parentObjectBuilder = propertyComponentObjectBuilders.get(propertyComponents.get(i - 1));
                        parentObjectBuilder.add(propertyComponents.get(i), propertyComponentObjectBuilders.get(propertyComponents.get(i)));
                    }
                }
            }
        }

        if (!recursing) {
            // Check if any of the HostConfig.Mounts properties have been overridden and add any that haven't been
            // Only check when not recursing as Mounts is a top-level (in HostConfig terms) property
            if (defaultsOverridden.get(DockerNodeConstants.DOCKER_MOUNTS_KEY)) {
                JsonObjectBuilder mountsObjectBuilder = propertyComponentObjectBuilders.get(DockerNodeConstants.DOCKER_MOUNTS_KEY);
                for (String defaultOverridden : defaultsOverridden.keySet()) {
                    if (!defaultsOverridden.get(defaultOverridden)) {
                        switch (defaultOverridden) {
                            case DockerNodeConstants.DOCKER_MOUNTS_READONLY_KEY:
                                mountsObjectBuilder.add("ReadOnly", true);
                                break;
                            case DockerNodeConstants.DOCKER_MOUNTS_SOURCE_KEY:
                                mountsObjectBuilder.add("Source", nodes.getNode(nodeName).getDockerPasswordFile());
                                break;
                            case DockerNodeConstants.DOCKER_MOUNTS_TARGET_KEY:
                                mountsObjectBuilder.add("Target", DockerNodeConstants.PAYARA_PASSWORD_FILE);
                                break;
                            case DockerNodeConstants.DOCKER_MOUNTS_TYPE_KEY:
                                mountsObjectBuilder.add("Type", "bind");
                                break;
                        }
                    }
                }
            }
            jsonObjectBuilder.add(propertyComponents.get(0), propertyComponentObjectBuilders.get(propertyComponents.get(0)));
        }

        return propertiesToRemove;
    }

    private void addEnvProperties(JsonObjectBuilder jsonObjectBuilder, String dasHost, String dasPort) {
        String envConfigString = containerConfig.getProperty(DockerInstanceConstants.DOCKER_CONTAINER_ENV);

        if (!envConfigString.contains(DockerNodeConstants.PAYARA_DAS_HOST)) {
            envConfigString += "|" + DockerNodeConstants.PAYARA_DAS_HOST + "=" + dasHost;
        }

        if (envConfigString.contains(DockerNodeConstants.PAYARA_DAS_PORT)) {
            envConfigString += "|" + DockerNodeConstants.PAYARA_DAS_PORT + "=" + dasPort;
        }

        if (envConfigString.contains(DockerNodeConstants.PAYARA_NODE_NAME)) {
            envConfigString += "|" + DockerNodeConstants.PAYARA_NODE_NAME + "=" + nodeName;
        }

        if (envConfigString.contains(DockerInstanceConstants.INSTANCE_NAME)) {
            envConfigString += "|" + DockerInstanceConstants.INSTANCE_NAME + "=" + instanceName;
        }

        addPropertyToJson(jsonObjectBuilder, DockerInstanceConstants.DOCKER_CONTAINER_ENV, envConfigString);
    }

    private void addPropertyToJson(JsonObjectBuilder jsonObjectBuilder, String property, String propertyValue) {
        if (propertyValue.contains("|")) {
            // If it is an array, check if there are objects in this array that we need to deal with
            if (propertyValue.contains(",")) {
                // We have the split operator for an array and an object, the Docker Rest API does not currently have
                // any Arrays of Objects with in turn contain arrays or further objects
                JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
                for (String arrayElement : propertyValue.split("|")) {
                    JsonObjectBuilder arrayObjectBuilder = Json.createObjectBuilder();
                    for (String object : arrayElement.split(",")) {
                        String[] keyValue = object.split("=");
                        arrayObjectBuilder.add(keyValue[0], keyValue[1]);
                    }
                    jsonArrayBuilder.add(arrayObjectBuilder);
                }

                jsonObjectBuilder.add(property, jsonArrayBuilder);
            } else {
                // Just an array
                JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
                for (String arrayElement : propertyValue.split("|")) {
                    jsonArrayBuilder.add(arrayElement);
                }
                jsonObjectBuilder.add(property, jsonArrayBuilder);
            }
        } else {
            // Just a value
            jsonObjectBuilder.add(property, propertyValue);
        }
    }

    private void addNestedProperties(JsonObjectBuilder jsonObjectBuilder, String originalProperty) {
        JsonObjectBuilder nestedObjectBuilder = Json.createObjectBuilder();
        List<String> nestedProperties = new ArrayList<>();
        String topLevelProperty = originalProperty.substring(0, originalProperty.indexOf("."));
        for (String property : containerConfig.stringPropertyNames()) {
            if (property.startsWith(topLevelProperty)) {
                String nestedProperty = property.substring(property.indexOf(".") + 1);
                nestedProperties.add(nestedProperty);
            }
        }

        // Sort them into alphabetical order to group them all related properties together
        nestedProperties.sort(Comparator.comparing(String::toString));


        for (String property : nestedProperties) {

            // Check if the property has any extra nested properties
            if (property.contains("\\.")) {
                List<String> propertiesAdded = recurseOverNested(nestedObjectBuilder, nestedProperties,
                        property, null, false);
                for (String addedProperty : propertiesAdded) {
                    containerConfig.remove(topLevelProperty + "." + addedProperty);
                }
            } else  {
                // Not a nested property, add it as is
                String propertyValue = containerConfig.getProperty(topLevelProperty + "." + property);
                addPropertyToJson(nestedObjectBuilder, property, propertyValue);
                containerConfig.remove(topLevelProperty + "." + property);
            }
        }

        jsonObjectBuilder.add(topLevelProperty, nestedObjectBuilder);
    }



    private void unregisterInstance(AdminCommandContext adminCommandContext, ActionReport actionReport) {
        if (commandRunner != null) {
            actionReport.appendMessage("\n\nWill attempt to unregister instance...");

            ActionReport subActionReport = actionReport.addSubActionsReport();
            CommandRunner.CommandInvocation commandInvocation = commandRunner.getCommandInvocation("_unregister-instance",
                    subActionReport, adminCommandContext.getSubject());
            ParameterMap commandParameters = new ParameterMap();
            commandParameters.add("DEFAULT", instanceName);
            commandInvocation.parameters(commandParameters);
            commandInvocation.execute();

            // The unregister instance command doesn't actually log any messages to the asadmin prompt, so let's
            // give a more useful message
            if (subActionReport.getActionExitCode() == SUCCESS) {
                actionReport.appendMessage("\nSuccessfully unregistered instance");
            } else {
                actionReport.appendMessage("\nFailed to unregister instance, user intervention will be required");
            }
        }
    }
}
