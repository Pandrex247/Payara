package fish.payara.docker.node.admin;

import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Node;
import com.sun.enterprise.config.serverbeans.Nodes;
import fish.payara.docker.node.contants.DockerNodeConstants;
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
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.logging.Logger;

@Service(name = "_create-docker-container")
@PerLookup
@ExecuteOn({RuntimeType.DAS})
@RestEndpoints({
        @RestEndpoint(configBean= Domain.class,
                opType=RestEndpoint.OpType.POST,
                path="_create-docker-container",
                description="Create a Docker Container and Instance on the specified node")
})
public class CreateDockerContainerCommand implements AdminCommand {

    private static final Logger logger = Logger.getLogger(CreateDockerContainerCommand.class.getName());

    @Param(name = "node")
    String nodeName;

    @Param(name = "config")
    String config;

    @Param(name = "instance", primary = true)
    String instanceName;

    @Param(name = "containerConfig", optional = true, alias = "containerconfig", separator = '|')
    String[] containerConfig;

    @Inject
    Nodes nodes;

    /**
     * Executes the command with the command parameters passed as Properties
     * where the keys are the parameter names and the values are the parameter values
     *
     * @param adminCommandContext information
     */
    @Override
    public void execute(AdminCommandContext adminCommandContext) {
        ActionReport actionReport = adminCommandContext.getActionReport();

        Node node = nodes.getNode(nodeName);

        if (node == null) {
            actionReport.failure(logger, "No node found with given name: " + nodeName);
            return;
        }

        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder().add(DockerNodeConstants.DOCKER_NAME, instanceName);
        jsonObjectBuilder.add(DockerNodeConstants.DOCKER_ENV, Json.createArrayBuilder()
                .add(DockerNodeConstants.INSTANCE_CONFIG + "=" + config)
                .add(DockerNodeConstants.INSTANCE_NAME + "=" + instanceName));
        jsonObjectBuilder.add(DockerNodeConstants.DOCKER_IMAGE, node.getDockerImage());

        // TO-DO: Container Config






        // Create client and send request to Docker API
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target(node.getNodeHost() + ":" + node.getDockerPort() + "/containers/create");
        Response response = webTarget.request(MediaType.APPLICATION_JSON).post(
                Entity.entity(jsonObjectBuilder.build(), MediaType.APPLICATION_JSON));

        // Check status of response and act on result
        Response.StatusType responseStatus = response.getStatusInfo();
        if (responseStatus.getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
            // woohoo
        } else {
            actionReport.failure(logger, "Failed to create Docker Container: \n" + responseStatus.getReasonPhrase());
        }
    }
}
