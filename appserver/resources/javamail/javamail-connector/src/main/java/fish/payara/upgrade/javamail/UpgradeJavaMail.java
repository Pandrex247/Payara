package fish.payara.upgrade.javamail;

import com.sun.enterprise.config.serverbeans.Resources;
import jakarta.inject.Inject;
import org.glassfish.api.StartupRunLevel;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.resources.javamail.config.MailResource;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.TransactionFailure;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
@RunLevel(StartupRunLevel.VAL)
public class UpgradeJavaMail implements PostConstruct {
    private static final String OLD_PACKAGE = "com.sun.mail";
    private static final String NEW_PACKAGE = "org.eclipse.angus.mail";
    private static final String UPGRADE_PROPERTY = "fish.payara.upgrade.sun.mail.packages";

    private static final Logger LOGGER = Logger.getAnonymousLogger();

    @Inject
    Resources resources;

    @Override
    public void postConstruct () {
        for (MailResource resource : this.resources.getResources(MailResource.class)) {
            if (resource == null) {
                continue;
            }
            if (Boolean.FALSE.toString().equals(resource.getPropertyValue(UPGRADE_PROPERTY))) {
                try {
                    ConfigSupport.apply(
                        mailResource -> {
                            if (mailResource.getStoreProtocolClass().equals(MailResource.DEFAULT_STORE_PROTOCOL_CLASS)) {
                                mailResource.setStoreProtocolClass(
                                    mailResource.getStoreProtocolClass().replace(NEW_PACKAGE, OLD_PACKAGE));
                            }

                            if (mailResource.getTransportProtocolClass().equals(MailResource.DEFAULT_TRANSPORT_PROTOCOL_CLASS)) {
                                mailResource.setTransportProtocolClass(
                                    mailResource.getTransportProtocolClass().replace(NEW_PACKAGE, OLD_PACKAGE));
                            }
                            return mailResource;
                        }, resource);
                }
                catch (TransactionFailure e) {
                    LOGGER.log(Level.WARNING, "Upgrade service failed to update package names for Mail Resource:", e);
                }
            }
            else {
                try {
                    ConfigSupport.apply(
                        mailResource -> {
                            if (mailResource.getStoreProtocolClass().startsWith(OLD_PACKAGE)) {
                                mailResource.setStoreProtocolClass(
                                    mailResource.getStoreProtocolClass().replace(OLD_PACKAGE, NEW_PACKAGE));
                            }

                            if (mailResource.getTransportProtocolClass().startsWith(OLD_PACKAGE)) {
                                mailResource.setTransportProtocolClass(
                                    mailResource.getTransportProtocolClass().replace(OLD_PACKAGE, NEW_PACKAGE));
                            }
                            return mailResource;
                        }, resource);
                }
                catch (TransactionFailure e) {
                    LOGGER.log(Level.WARNING, "Upgrade service failed to update package names for Mail Resource:", e);
                }
            }
        }
    }
}
