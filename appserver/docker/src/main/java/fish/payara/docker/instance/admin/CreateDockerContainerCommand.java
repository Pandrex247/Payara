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
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.RestEndpoint;
import org.glassfish.api.admin.RestEndpoints;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

@Service(name = "_create-docker-container")
@PerLookup
@ExecuteOn({RuntimeType.DAS})
@RestEndpoints({
        @RestEndpoint(configBean= Domain.class,
                opType=RestEndpoint.OpType.POST,
                path="_create-docker-container",
                description="Create a Docker Container and Instance on the specified nodeName")
})
public class CreateDockerContainerCommand implements AdminCommand {

    private static final Logger logger = Logger.getLogger(CreateDockerContainerCommand.class.getName());

    @Param(name = "nodeName", alias = "node")
    String nodeName;

    @Param(name = "instanceName", alias = "instance", primary = true)
    String instanceName;

//    TO-DO
//    @Param(name = "containerConfig", optional = true, alias = "containerconfig", separator = '|')
//    String[] containerConfig;

    @Inject
    private Servers servers;

    @Inject
    private Nodes nodes;

    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        ActionReport actionReport = adminCommandContext.getActionReport();

        Node node = nodes.getNode(nodeName);

        if (node == null) {
            actionReport.failure(logger, "No nodeName found with given name: " + nodeName);
            return;
        }

        if (!node.getType().equals("DOCKER")) {
            actionReport.failure(logger, "Node is not of type DOCKER, nodeName is of type: " + node.getType());
            return;
        }

        // Get the DAS instance port
        String dasPort = "";
        for (Server server : servers.getServer()) {
            if (server.isDas()) {
                dasPort = Integer.toString(server.getAdminPort());
                break;
            }
        }

        // We want to make sure we have the IP address rather than a host name, which is why we don't get it from the
        // server instance that we got the port from
        String dasHost = "";
        try {
            dasHost = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException uhe) {
            actionReport.failure(logger, "Could not determine DAS address", uhe);
            return;
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

        // Create the JSON request to send
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
        jsonObjectBuilder.add(DockerNodeConstants.DOCKER_IMAGE_KEY, node.getDockerImage());
        jsonObjectBuilder.add(DockerNodeConstants.DOCKER_HOST_CONFIG_KEY, Json.createObjectBuilder()
                .add(DockerNodeConstants.DOCKER_MOUNTS_KEY, Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("Type", "bind")
                                .add("Source", node.getDockerPasswordFile())
                                .add("Target", DockerNodeConstants.PAYARA_PASSWORD_FILE)
                                .add("ReadOnly", true))));
        jsonObjectBuilder.add(DockerInstanceConstants.DOCKER_CONTAINER_ENV, Json.createArrayBuilder()
                .add(DockerNodeConstants.PAYARA_DAS_HOST + "=" + dasHost)
                .add(DockerNodeConstants.PAYARA_DAS_PORT + "=" + dasPort)
                .add(DockerNodeConstants.PAYARA_NODE_NAME + "=" + nodeName)
                .add(DockerInstanceConstants.INSTANCE_NAME + "=" + instanceName));

        // Create web target with query
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target(
                "http://"
                + node.getNodeHost()
                + ":"
                + node.getDockerPort()
                + "/containers/create");
        webTarget = webTarget.queryParam(DockerNodeConstants.DOCKER_NAME_KEY, instanceName);

        // Send the POST request
        Response response = webTarget.queryParam(DockerNodeConstants.DOCKER_NAME_KEY, instanceName)
                .request(MediaType.APPLICATION_JSON).post(
                        Entity.entity(jsonObjectBuilder.build(), MediaType.APPLICATION_JSON));

        // Check status of response and act on result
        Response.StatusType responseStatus = response.getStatusInfo();
        if (!responseStatus.getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
            actionReport.failure(logger, "Failed to create Docker Container: \n"
                    + responseStatus.getReasonPhrase());
        }
    }
}
