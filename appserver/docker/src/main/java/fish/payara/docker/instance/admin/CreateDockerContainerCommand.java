package fish.payara.docker.instance.admin;

import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Node;
import com.sun.enterprise.config.serverbeans.Nodes;
import com.sun.enterprise.config.serverbeans.Server;
import com.sun.enterprise.config.serverbeans.Servers;
import com.sun.enterprise.config.util.PortConstants;
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
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.hk2.api.PerLookup;
import org.glassfish.server.ServerEnvironmentImpl;
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
                description="Create a Docker Container and Instance on the specified node")
})
public class CreateDockerContainerCommand implements AdminCommand {

    private static final Logger logger = Logger.getLogger(CreateDockerContainerCommand.class.getName());

    @Param(name = "node")
    String nodeName;

    @Param(name = "config", optional = true, defaultValue = "default-config")
    String config;

    @Param(name = "instance", primary = true)
    String instanceName;

    @Param(name = "deploymentgroup", optional = true)
    String deploymentGroup;

    @Param(name = "portbase", optional = true)
    private String portBase;

    @Param(name = "systemproperties", optional = true, separator = ':')
    private String systemProperties;

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
            actionReport.failure(logger, "No node found with given name: " + nodeName);
            return;
        }

        if (!node.getType().equals("DOCKER")) {
            actionReport.failure(logger, "Node is not of type DOCKER, node is of type: " + node.getType());
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
        jsonObjectBuilder.add(DockerNodeConstants.DOCKER_IMAGE, node.getDockerImage());
        jsonObjectBuilder.add(DockerNodeConstants.DOCKER_IMAGE, node.getDockerImage());
        jsonObjectBuilder.add(DockerNodeConstants.DOCKER_ENV, Json.createArrayBuilder()
                .add(DockerNodeConstants.PAYARA_DAS_HOST + "=" + dasHost)
                .add(DockerNodeConstants.PAYARA_DAS_PORT + "=" + dasPort)
                .add(DockerInstanceConstants.INSTANCE_CONFIG + "=" + config)
                .add(DockerInstanceConstants.INSTANCE_NAME + "=" + instanceName)
                .add(DockerInstanceConstants.DEPLOYMENT_GROUP + "=" + deploymentGroup)
                .add(DockerInstanceConstants.PORTBASE + "=" + portBase)
                .add(DockerInstanceConstants.SYSTEM_PROPERTIES + "=" + systemProperties)
                .add(PortConstants.ADMIN + "=" + server.getSystemProperty(PortConstants.ADMIN))
                .add(PortConstants.HTTP + "=" + server.getSystemProperty(PortConstants.HTTP))
                .add(PortConstants.HTTPS + "=" + server.getSystemProperty(PortConstants.HTTPS))
                .add(PortConstants.IIOP + "=" + server.getSystemProperty(PortConstants.IIOP))
                .add(PortConstants.IIOPM + "=" + server.getSystemProperty(PortConstants.IIOPM))
                .add(PortConstants.IIOPS + "=" + server.getSystemProperty(PortConstants.IIOPS))
                .add(PortConstants.JMS + "=" + server.getSystemProperty(PortConstants.JMS))
                .add(PortConstants.JMX + "=" + server.getSystemProperty(PortConstants.JMX))
                .add(PortConstants.OSGI + "=" + server.getSystemProperty(PortConstants.OSGI))
                .add(PortConstants.DEBUG + "=" + server.getSystemProperty(PortConstants.DEBUG)));

        // Create web target with query
        Client client = ClientBuilder.newClient();
        WebTarget webTarget = client.target(
                "http://"
                + node.getNodeHost()
                + ":"
                + node.getDockerPort()
                + "/containers/create");
        webTarget = webTarget.queryParam(DockerNodeConstants.DOCKER_NAME, instanceName);

        // Send the POST request
        Response response = webTarget.queryParam(DockerNodeConstants.DOCKER_NAME, instanceName)
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
