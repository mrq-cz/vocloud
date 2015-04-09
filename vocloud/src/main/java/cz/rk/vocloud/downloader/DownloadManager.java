package cz.rk.vocloud.downloader;

import cz.rk.vocloud.entity.DownloadJob;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

/**
 *
 * @author radio.koza
 */
@Startup
@Singleton
public class DownloadManager {
    private static final Logger LOG = Logger.getLogger(DownloadManager.class.getName());

    
    @EJB
    private DownloadJobFacade djb;

    @EJB
    private DownloadProcessor proc;
    
    
    @PostConstruct
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void init() {
        //initialization during startup
        List<DownloadJob> jobs = djb.findUnfinishedJobs();
        for (DownloadJob job: jobs){
            proc.processDownloadJob(job);
        }
    }

    private static final String[] supportedDownloadProtocols = {"HTTP", "HTTPS", "FTP"};
    
    /**
     * 
     * @param downloadURL
     * @param targetFolder
     * @return True if success, false otherwise
     */
    public boolean enqueueNewURLDownload(String downloadURL, String targetFolder) {
        try {
            URL url = new URL(downloadURL);
            //check protocol validity
            //supported HTTP, HTTPS, FTP
            if (!Arrays.asList(supportedDownloadProtocols).contains(url.getProtocol().toUpperCase())){
                throw new MalformedURLException("Unsupported protocol");
            }
        } catch (MalformedURLException ex) {
            return false;
        }
        DownloadJob job = djb.createNewDownloadJob(downloadURL, targetFolder);
        proc.processDownloadJob(job);//must get proxy business object to properly call async method
        return true;
    }
    

}
