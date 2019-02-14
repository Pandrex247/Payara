package fish.payara.iiop.impl;

import com.sun.logging.LogDomains;
import org.glassfish.enterprise.iiop.util.IIOPUtils;
import org.glassfish.enterprise.iiop.util.NotServerException;
import org.glassfish.orb.admin.config.IiopListener;
import org.glassfish.orb.admin.config.IiopService;

import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class that contains common methods for operating on sockets.
 */
public class SocketOperations {

    private static final Logger _logger = LogDomains.getLogger(SocketOperations.class, LogDomains.CORBA_LOGGER);

    public static final String SO_KEEPALIVE = "fish.payara.SO_KEEPALIVE";
    // Deprecated as of 5.191
    public static final String SO_KEEPALIVE_DEPRECATED = "fish.payara.SOKeepAlive";

    /**
     * Checks whether SO_KEEPALIVE should be enabled on a Socket and enables it if it should be. Checks for the
     * presence of a property on the IIOP listener and globally, preferring the value set in the listener.
     *
     * @param socket The socket to potentially enable SO_KEEPALIVE on
     * @throws SocketException If there was an error enabling SO_KEEPALIVE on the socket.
     */
    public static void enableSOKeepAliveAsRequired(Socket socket) throws SocketException {
        boolean shouldSet = false;

        try {
            // Try to get the IIOP Service as this does a check for if we are a server or not (save checking twice)
            IiopService iiopService = IIOPUtils.getInstance().getIiopService();

            // For each listener, find one with a matching port
            for (IiopListener iiopListener : IIOPUtils.getInstance().getIiopService().getIiopListener()) {
                if (Integer.valueOf(iiopListener.getPort()) == socket.getLocalPort()) {
                    // Check for the property globally before checking on the specific listener, giving precedence to the
                    // new property
                    if ((System.getProperty(SO_KEEPALIVE) == null && Boolean.getBoolean(SO_KEEPALIVE_DEPRECATED))
                            || Boolean.getBoolean(SO_KEEPALIVE)) {
                        // Check if the property has been set on the listener
                        if (soKeepAlivePropertyPresentOnIiopListener(iiopListener)) {
                            // Check if we should override the global value
                            if (soKeepAlivePropertyEnabledOnIiopListener(iiopListener)) {
                                shouldSet = true;
                            }
                        } else {
                            // If the property wasn't set on the listener, go with the global setting
                            shouldSet = true;
                        }
                        break;
                    } else {
                        // If it wasn't set globally, just check if it's set and enabled on the listener
                        if (soKeepAlivePropertyPresentOnIiopListener(iiopListener)
                                && soKeepAlivePropertyEnabledOnIiopListener(iiopListener)) {
                            shouldSet = true;
                        }
                        break;
                    }
                }
            }
        } catch (NotServerException notServerException) {
            // Enable or disable SO_KEEPALIVE for the socket as required
            if (Boolean.getBoolean(SO_KEEPALIVE) && !socket.getKeepAlive()) {
                shouldSet = true;
            }
        }

        if (shouldSet) {
            _logger.log(Level.FINER, "Enabling SO_KEEPALIVE");
            socket.setKeepAlive(true);
        }
    }

    /**
     * Shorthand method that simply returns true if either the new or old SO_KEEPALIVE property is present on the
     * listener.
     * @param iiopListener The IIOP listener to check if the SO_KEEPALIVE property is set on
     * @return True if the SO_KEEPALIVE property is present on the IIOP listener
     */
    public static boolean soKeepAlivePropertyPresentOnIiopListener(IiopListener iiopListener) {
        boolean soKeepAlivePropertyPresentOnListener = false;

        if (iiopListener.getPropertyValue(SO_KEEPALIVE) != null
                || iiopListener.getPropertyValue(SO_KEEPALIVE_DEPRECATED) != null) {
            soKeepAlivePropertyPresentOnListener = true;
        }

        return soKeepAlivePropertyPresentOnListener;
    }

    /**
     * Helper method that checks if either the deprecated or new SO_KEEPALIVE property is enabled on an IIOP
     * listener, giving precedence to the new property if both are present.
     * @param iiopListener The IIOP listener to check if the SO_KEEPALIVE property is set on
     * @return True if the SO_KEEPALIVE property is enabled on the IIOP listener
     */
    public static boolean soKeepAlivePropertyEnabledOnIiopListener(IiopListener iiopListener) {
        boolean soKeepAliveEnabledOnListener = false;

        // If the new property isn't present and the deprecated one is set to true, or if the new property is set to
        // true, then register SO_KEEPALIVE as enabled on the listener
        if ((iiopListener.getPropertyValue(SO_KEEPALIVE) == null
                && Boolean.valueOf(iiopListener.getPropertyValue(SO_KEEPALIVE_DEPRECATED)))
                || Boolean.valueOf(iiopListener.getPropertyValue(SO_KEEPALIVE))) {
            soKeepAliveEnabledOnListener = true;
        }

        return soKeepAliveEnabledOnListener;
    }
}
