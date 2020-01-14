package fish.payara.samples.datagridencryption.websession;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet(name = "/TestServlet")
public class TestServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession();

        String wibbleParameter = request.getParameter("Wibble");
        if (wibbleParameter != null && !wibbleParameter.isEmpty()) {
            if (session.getAttribute("Wibble") != null) {
                session.setAttribute("Wibble", session.getAttribute("Wibble") + ", " + wibbleParameter);
            } else {
                session.setAttribute("Wibble", wibbleParameter);
            }
        }

        response.getOutputStream().print("Wibble: " + session.getAttribute("Wibble"));
    }

}
