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
            translatePropertyValuesToJson(jsonObjectBuilder);
        }



        return jsonObjectBuilder.build();
    }

    private JsonObjectBuilder translatePropertyValuesToJson(JsonObjectBuilder jsonObjectBuilder) {
        for (Object propertyKey : containerConfig.keySet()) {
            // Should always be a String
            String property = (String) propertyKey;

            // Check if any of the properties are in the same namespace as our defaults
            if (property.startsWith(DockerNodeConstants.DOCKER_HOST_CONFIG_KEY)) {
                addHostConfigProperties(jsonObjectBuilder);
                continue;
            } else if (property.startsWith(DockerInstanceConstants.DOCKER_CONTAINER_ENV)) {
                translateEnvPropertiesToJson(jsonObjectBuilder);
                continue;
            }

            // Check if this is a nested property
            if (property.contains(".")) {
                // Recurse through the properties and add any other properties that fall under the same namespace
                searchForPropertiesInSameNamespace(jsonObjectBuilder, property);
            } else {
                // Not a nested property, add it as is
                // Check if its value is anything special like an array or object
                String propertyValue = containerConfig.getProperty(property);

                // First, check if the value is an array
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
                } else if (propertyValue.contains(",")) {
                    // Just an object
                    JsonObjectBuilder childObjectBuilder = Json.createObjectBuilder();
                    for (String object : propertyValue.split(",")) {
                        String[] keyValue = object.split("=");
                        childObjectBuilder.add(keyValue[0], keyValue[1]);
                    }

                    jsonObjectBuilder.add(property, childObjectBuilder);
                } else {
                    // Just a value
                    jsonObjectBuilder.add(property, propertyValue);
                }

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

        List<String> defaultsOverriden = new ArrayList<>();

        for (String property : hostConfigProperties) {
            // Check if property overrides any of our defaults
            if (!defaultsOverriden.contains(DockerNodeConstants.DOCKER_MOUNTS_KEY)
                    && property.startsWith(DockerNodeConstants.DOCKER_MOUNTS_KEY)) {
                defaultsOverriden.add(DockerNodeConstants.DOCKER_MOUNTS_KEY);
            }

            switch (property) {
                case DockerNodeConstants.DOCKER_MOUNTS_READONLY_KEY:
                    defaultsOverriden.add(DockerNodeConstants.DOCKER_MOUNTS_READONLY_KEY);
                    break;
                case DockerNodeConstants.DOCKER_MOUNTS_SOURCE_KEY:
                    defaultsOverriden.add(DockerNodeConstants.DOCKER_MOUNTS_SOURCE_KEY);
                    break;
                case DockerNodeConstants.DOCKER_MOUNTS_TARGET_KEY:
                    defaultsOverriden.add(DockerNodeConstants.DOCKER_MOUNTS_TARGET_KEY);
                    break;
                case DockerNodeConstants.DOCKER_MOUNTS_TYPE_KEY:
                    defaultsOverriden.add(DockerNodeConstants.DOCKER_MOUNTS_TYPE_KEY);
                    break;
                case DockerNodeConstants.DOCKER_NETWORK_MODE_KEY:
                    defaultsOverriden.add(DockerNodeConstants.DOCKER_NETWORK_MODE_KEY);
                    break;
            }

            // Check if the property has any extra nested properties
            if (property.contains("\\.")) {

            } else  {
                // Not a nested property, add it as is
                // Check if its value is anything special like an array or object
                String propertyValue = containerConfig.getProperty(property);

                // First, check if the value is an array
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

                        hostConfigObjectBuilder.add(property, jsonArrayBuilder);
                    } else {
                        // Just an array
                        JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
                        for (String arrayElement : propertyValue.split("|")) {
                            jsonArrayBuilder.add(arrayElement);
                        }
                        hostConfigObjectBuilder.add(property, jsonArrayBuilder);
                    }
                } else if (propertyValue.contains(",")) {
                    // Just an object
                    JsonObjectBuilder childObjectBuilder = Json.createObjectBuilder();
                    for (String object : propertyValue.split(",")) {
                        String[] keyValue = object.split("=");
                        childObjectBuilder.add(keyValue[0], keyValue[1]);
                    }

                    hostConfigObjectBuilder.add(property, childObjectBuilder);
                } else {
                    // Just a value
                    hostConfigObjectBuilder.add(property, propertyValue);
                }

                containerConfig.remove("property");
            }
        }



        // Add any remaining defaults
        if (!defaultsOverriden.contains(DockerNodeConstants.DOCKER_MOUNTS_KEY)) {
            hostConfigObjectBuilder.add(DockerNodeConstants.DOCKER_MOUNTS_KEY, Json.createArrayBuilder()
                    .add(Json.createObjectBuilder()
                            .add("Type", "bind")
                            .add("Source", nodes.getNode(nodeName).getDockerPasswordFile())
                            .add("Target", DockerNodeConstants.PAYARA_PASSWORD_FILE)
                            .add("ReadOnly", true)));
        }

        if (!defaultsOverriden.contains(DockerNodeConstants.DOCKER_NETWORK_MODE_KEY)) {
            hostConfigObjectBuilder.add(DockerNodeConstants.DOCKER_NETWORK_MODE_KEY, "host");
        }

        // Finally, add host config object to final Json request object
        jsonObjectBuilder.add(DockerNodeConstants.DOCKER_HOST_CONFIG_KEY, hostConfigObjectBuilder);
    }

    private JsonValue searchForPropertiesInSameNamespace(JsonObjectBuilder jsonObjectBuilder, String originalProperty) {
        // Get all container config properties that match the parent property
        List<String> matchingProperties = new ArrayList<>();

        String parent = originalProperty.substring(0, originalProperty.indexOf("."));
        String child = originalProperty.substring(originalProperty.indexOf(".") + 1);

        for (String property : containerConfig.stringPropertyNames()) {
            // Make sure we don't match against the child property passed in
            if (property.startsWith(parent)
                    && !property.substring(property.indexOf(".")).equals(child)) {
                matchingProperties.add(property);
            }
        }

        // If we've found a matching property, recurse in for all that we've found, otherwise just add the child property
        if (!matchingProperties.isEmpty()) {
            // Check if its value is anything special like an array or object
            String propertyValue = containerConfig.getProperty(originalProperty);

            // First, check if the value is an array
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

                    jsonObjectBuilder.add(parent, jsonArrayBuilder);
                } else {
                    // Just an array
                    JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
                    for (String arrayElement : propertyValue.split("|")) {
                        jsonArrayBuilder.add(arrayElement);
                    }
                    jsonObjectBuilder.add(parent, jsonArrayBuilder);
                }
            } else if (propertyValue.contains(",")) {
                // Just an object
                JsonObjectBuilder childObjectBuilder = Json.createObjectBuilder();
                for (String object : propertyValue.split(",")) {
                    String[] keyValue = object.split("=");
                    childObjectBuilder.add(keyValue[0], keyValue[1]);
                }

                jsonObjectBuilder.add(parent, childObjectBuilder);
            } else {
                // Just a value
                jsonObjectBuilder.add(parent, propertyValue);
            }
        } else {
            // If here, that means we've found other properties that share the same namespace, so we need to add them
            // all to the Json tree together

            // Sort them into alphabetical order to group them all related properties together
            matchingProperties.sort(Comparator.comparing(String::toString));

            // Now work through the map adding the properties as we go
            for (String matchingProperty : matchingProperties) {
                // Split it into its component parts
                String[] propertyComponents = matchingProperty.split("\\.");



                for (String propertyComponent : propertyComponents) {
                    jsonObjectBuilder.add(propertyComponent)
                }
            }
        }

        return jsonObjectBuilder.build();
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
