package fish.payara.samples.datagridencryption.websession;

import fish.payara.samples.ServerOperations;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.Response;
import java.io.File;
import java.net.URL;

@RunWith(Arquillian.class)
public class WebsessionEncryptionTest {

    @ArquillianResource
    private URL url;

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "web.war")
                .addPackage("fish.payara.samples.datagridencryption.websession")
                .addAsWebInfResource(new File("src/main/webapp", "WEB-INF/web.xml"))
                .addAsWebInfResource(new File("src/main/webapp", "WEB-INF/payara-web.xml"));
    }

    @BeforeClass
    public static void enableSecurity() {
        ServerOperations.enableDataGridEncryption();
    }

    @AfterClass
    public static void resetSecurity() {
        ServerOperations.disableDataGridEncryption();
    }

    @Test
    public void testWebsessionEncryption() {
        Client client = ClientBuilder.newClient();
        WebTarget endpoint1 = client.target(url + "TestServlet");

        // First, poke TestServlet and set a session attribute
        Response response = endpoint1.queryParam("Wibble", "Wobble").request().get();
        Cookie sessionCookie = response.getCookies().get("JSESSIONID");
        Assert.assertEquals("Wibble: Wobble", response.readEntity(String.class));

        // Now poke the same session and add to the same attribute
        response = endpoint1.queryParam("Wibble", "Bobble").request().cookie(sessionCookie).get();
        Assert.assertEquals("Wibble: Wobble, Bobble", response.readEntity(String.class));

        // Poke a new session to ensure it's session specific
        response = endpoint1.queryParam("Wibble", "Bibble").request().get();
        Assert.assertNotEquals(sessionCookie, response.getCookies().get("JSESSIONID"));
        Assert.assertEquals("Wibble: Bibble", response.readEntity(String.class));

        // Finally ensure our old session is still there
        response = endpoint1.queryParam("Wibble", "Cobble").request().cookie(sessionCookie).get();
        Assert.assertEquals("Wibble: Wobble, Bobble, Cobble", response.readEntity(String.class));
    }
}