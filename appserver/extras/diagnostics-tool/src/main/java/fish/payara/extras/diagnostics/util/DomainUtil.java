package fish.payara.extras.diagnostics.util;

import com.sun.enterprise.config.serverbeans.*;
import fish.payara.enterprise.config.serverbeans.DeploymentGroup;
import fish.payara.enterprise.config.serverbeans.DeploymentGroups;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DomainUtil {

    private final Domain domain;

    public DomainUtil(Domain domain) {
        this.domain = domain;
    }

    public DeploymentGroups getDeploymentGroups() {
        return domain.getDeploymentGroups();
    }

    public Clusters getClusters() {
        return domain.getClusters();
    }

    public List<Node> getNodes() {
        return domain.getNodes().getNode();
    }

    public List<Server> getInstances() {
        List<Server> instances = new ArrayList<>();
        List<Node> nodes = getNodes();
        for (Node node : nodes) {
            instances.addAll(domain.getInstancesOnNode(node.getName()));
        }
        return instances;
    }

    public List<Server> getLocalInstances() {
        List<Server> instances = new ArrayList<>();
        List<Node> nodes = getNodes();
        for (Node node : nodes) {
            if (node.isLocal()) {
                instances.addAll(domain.getInstancesOnNode(node.getName()));
            }
        }
        return instances;
    }

    public List<Server> getStandaloneLocalInstances() {
        List<Server> instances = getInstances();

        for (DeploymentGroup dg : getDeploymentGroups().getDeploymentGroup()) {
            for (Server dgInstance : dg.getInstances()) {
                instances.removeIf(instance -> instance.getName().equals(dgInstance.getName()) && dgInstance.getNodeRef().equals(instance.getNodeRef()));
            }
        }

        for (Cluster cluster : getClusters().getCluster()) {
            for (Server clusterInstance : cluster.getInstances()) {
                instances.removeIf(instance -> instance.getName().equals(clusterInstance.getName()) && clusterInstance.getNodeRef().equals(instance.getNodeRef()));
            }
        }
        return instances;
    }

    public Server getInstance(String instance) {
        for (Server server : getInstances()) {
            if (server.getName().equals(instance)) {
                return server;
            }
        }
        return null;
    }


    public Map<String, Path> getNodePaths() {
        Map<String, Path> nodePaths = new HashMap<>();
        for (Node node : domain.getNodes().getNode()) {
            if (!node.getType().equals("CONFIG")) {
                continue;
            }

            if (!node.isLocal()) {
                continue;
            }

            if (node.getNodeDir() != null) {
                nodePaths.put(node.getName(), Paths.get(node.getNodeDir(), node.getName()));
                continue;
            }
            nodePaths.put(node.getName(), Paths.get(node.getInstallDir().replace("${com.sun.aas.productRoot}", System.getProperty("com.sun.aas.productRoot")), "glassfish", "nodes", node.getName()));
        }
        return nodePaths;
    }

    public Map<String, List<String>> getServersInNodes() {
        Map<String, List<String>> nodesAndServers = new HashMap<>();
        for (Server server : domain.getServers().getServer()) {
            if (server.getConfig().isDas()) {
                continue;
            }

            if (!nodesAndServers.containsKey(server.getNodeRef())) {
                List<String> servers = new ArrayList<>();
                servers.add(server.getName());
                nodesAndServers.put(server.getNodeRef(), servers);
                continue;
            }
            List<String> servers = nodesAndServers.get(server.getNodeRef());
            servers.add(server.getName());
            nodesAndServers.put(server.getNodeRef(), servers);
        }
        return nodesAndServers;
    }

    public List<String> getInstancesNames() {
        List<Server> instances = getInstances();
        List<String> instanceNames = new ArrayList<>();
        instances.forEach(instance -> instanceNames.add(instance.getName()));

        return instanceNames;
    }
}
