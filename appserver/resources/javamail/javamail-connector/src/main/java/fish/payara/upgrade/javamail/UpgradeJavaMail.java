package fish.payara.upgrade.javamail;

import com.sun.enterprise.config.serverbeans.Resources;
import jakarta.inject.Inject;
import org.glassfish.api.StartupRunLevel;
import org.glassfish.hk2.api.PostConstruct;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.resources.javamail.config.MailResource;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.SingleConfigCode;
import org.jvnet.hk2.config.Transaction;
import org.jvnet.hk2.config.TransactionFailure;

import java.beans.PropertyVetoException;

@Service
@RunLevel(StartupRunLevel.VAL)
public class UpgradeJavaMail implements PostConstruct {
    private static final String OLD_PACKAGE = "com.sun.mail";
    private static final String NEW_PACKAGE = "org.eclipse.angus.mail";

    @Inject
    Resources resources;

    @Override
    public void postConstruct () {
        for (MailResource resource : this.resources.getResources(MailResource.class)) {
            if (resource == null) {
                continue;
            }

            try {
                ConfigSupport.apply(
                    new SingleConfigCode<MailResource>() {
                        @Override
                        public Object run (MailResource mailResource) throws PropertyVetoException {
                            if (mailResource.getStoreProtocolClass().startsWith(OLD_PACKAGE)) {
                                mailResource.setStoreProtocolClass(
                                    mailResource.getStoreProtocolClass().replace(OLD_PACKAGE, NEW_PACKAGE)
                                );
                            }

                            if (mailResource.getTransportProtocolClass().startsWith(OLD_PACKAGE)) {
                                mailResource.setTransportProtocolClass(
                                    mailResource.getTransportProtocolClass().replace(OLD_PACKAGE, NEW_PACKAGE)
                                );
                            }
                            return mailResource;
                        }
                    },
                    resource
                );
            }
            catch (TransactionFailure ignored) {

            }
        }
    }
}
