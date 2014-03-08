package cz.mrq.vocloud.ejb;

import cz.mrq.vocloud.entity.Job;
import cz.mrq.vocloud.entity.Phase;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author voadmin
 */
@Singleton
@LocalBean
public class SchedulerBean {
    
    @EJB
    JobFacade jf;
    
    private List<Job> watchedJobs = new CopyOnWriteArrayList<Job>();
    
    private Date lastUpdate;

    private static final Logger logger = Logger.getLogger(SchedulerBean.class.toString());
    
    @PostConstruct
    public void init() {
        logger.log(Level.INFO, "scheduler initialized");
        
        // find executing jobs
        watchedJobs.addAll(jf.findByPhase(Phase.EXECUTING));
        updateExecutingJobs();
    }
    
        
    /**
     * periodically queries uws for job's progress
     * 
     */
    @Schedule(second="*/5", minute="*", hour="*", persistent=false)
    public void updateExecutingJobs() {
        int prevsize = watchedJobs.size();
        Phase phase;
        for (Job job : watchedJobs) {
            phase = job.getPhase();
            job.updateFromUWS();
            if (phase != job.getPhase())
                jf.edit(job);
            if (job.getPhase() == Phase.COMPLETED || job.getPhase() == Phase.ERROR || job.getPhase() == Phase.ABORTED) {
                jf.exportUWSJob(job);
                jf.downloadResults(job);
                jf.postProcess(job);
                job.destroyOnUWS();
                watchedJobs.remove(job);
            }
        }
        lastUpdate = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat();
        if(prevsize != watchedJobs.size()) {
            logger.log(Level.INFO, "update happened at {0}",sdf.format(lastUpdate));
            logger.log(Level.INFO, "watched jobs: {0}", watchedJobs.size());
        }
    }
    
    public void addWatchedJob(Job job) {
        if (!watchedJobs.contains(job)) {
            watchedJobs.add(job);    
            logger.log(Level.INFO, "watched jobs: {0}", watchedJobs.size());
        }
    }
    
    public void removeWatchedJob(Job job) {
        if (watchedJobs.contains(job)) {
            watchedJobs.remove(job);    
            logger.log(Level.INFO, "watched jobs: {0}", watchedJobs.size());
        }
    }
    

    public Date getLastUpdate() {
        return lastUpdate;
    }

    
}

