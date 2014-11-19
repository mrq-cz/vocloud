/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cz.mrq.vocloud.worker.rf;

import java.io.File;
import java.util.Map;
import java.util.logging.Logger;
import uws.UWSException;
import uws.job.AbstractJob;
import uws.service.QueuedBasicUWS;

/**
 *
 * @author palicka
 */
public class JobRF extends AbstractJob {
    public JobRF(Map<String, String> lstParam) throws UWSException {
        super(lstParam);
    }

    @Override
    protected void jobWork() throws UWSException, InterruptedException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
