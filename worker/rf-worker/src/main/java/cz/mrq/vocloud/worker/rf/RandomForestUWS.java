/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.mrq.vocloud.worker.rf;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import uws.service.QueuedBasicUWS;
/**
 *
 * @author palicka
 */
public class RandomForestUWS extends HttpServlet {
    private static final Logger logger = Logger.getLogger(Jo.class.getName());
    protected QueuedBasicUWS<JobRF> uws;
}
