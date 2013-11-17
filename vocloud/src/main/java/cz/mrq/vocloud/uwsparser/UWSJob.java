package cz.mrq.vocloud.uwsparser;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author voadmin
 */
public class UWSJob {
    private String jobId;
    private String runId;
    private String owner;
    private UWSJobPhase phase; 
    private Date startTime;
    private Date endTime;
    private long executionDuration;
    private Date destruction;
    private String results;
    private String parameters;
    private String errorSummary;
    
    private DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    
    @Override
    public String toString() {
        return "UWSJob{" + "jobId=" + jobId + ", runId=" + runId + ", owner=" + owner + ", phase=" + phase + ", startTime=" + startTime + ", endTime=" + endTime + ", executionDuration=" + executionDuration + ", destruction=" + destruction + ", results=" + results + ", parameters=" + parameters + ", dateFormat=" + dateFormat + '}';
    }
    
    //<editor-fold defaultstate="collapsed" desc="getters and setters">
    public Date getDestruction() {
        return destruction;
    }
    
    public void setDestruction(Date destruction) {
        this.destruction = destruction;
    }
    
    public void setDestruction(String destruction) {
        try {
            this.destruction = dateFormat.parse(destruction);
        } catch (ParseException ex) {
            Logger.getLogger(UWSJob.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public Date getEndTime() {
        return endTime;
    }
    
    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }
    
    public void setEndTime(String endTime) {
        try {
            this.endTime = dateFormat.parse(endTime);
        } catch (ParseException ex) {
            Logger.getLogger(UWSJob.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public long getExecutionDuration() {
        return executionDuration;
    }
    
    public void setExecutionDuration(long executionDuration) {
        this.executionDuration = executionDuration;
    }
    
    public String getJobId() {
        return jobId;
    }
    
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public String getParameters() {
        return parameters;
    }
    
    public void setParameters(String parameters) {
        this.parameters = parameters;
    }
    
    public UWSJobPhase getPhase() {
        return phase;
    }
    
    public void setPhase(UWSJobPhase phase) {
        this.phase = phase;
    }
    
    public String getResults() {
        return results;
    }
    
    public void setResults(String results) {
        this.results = results;
    }
    
    public String getRunId() {
        return runId;
    }
    
    public void setRunId(String runId) {
        this.runId = runId;
    }
    
    public Date getStartTime() {
        return startTime;
    }
    
    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }
    
    public void setStartTime(String startTime) {
        try {
            this.startTime = dateFormat.parse(startTime);
        } catch (ParseException ex) {
            Logger.getLogger(UWSJob.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //</editor-fold>
}


