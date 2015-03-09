package cz.mrq.vocloud.entity;

import cz.mrq.vocloud.uwsparser.UWSParserManager;
import cz.mrq.vocloud.uwsparser.model.UWSJob;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author voadmin
 */
@Entity
@NamedQueries({
    @NamedQuery(name = "Job.findAll", query = "SELECT j FROM Job j"),
    @NamedQuery(name = "Job.findById", query = "SELECT j FROM Job j WHERE j.id = :id"),
    @NamedQuery(name = "Job.findByCreatedDate", query = "SELECT j FROM Job j WHERE j.createdDate = :createdDate"),
    @NamedQuery(name = "Job.findByFinishedDate", query = "SELECT j FROM Job j WHERE j.finishedDate = :finishedDate"),
    @NamedQuery(name = "Job.findByJobtype", query = "SELECT j FROM Job j WHERE j.jobType = :jobType"),
    @NamedQuery(name = "Job.findByLabel", query = "SELECT j FROM Job j WHERE j.label = :label"),
    @NamedQuery(name = "Job.findByNotes", query = "SELECT j FROM Job j WHERE j.notes = :notes"),
    @NamedQuery(name = "Job.findByPhase", query = "SELECT j FROM Job j WHERE j.phase = :phase"),
    @NamedQuery(name = "Job.findByOwnerId", query = "SELECT j FROM Job j WHERE j.owner = :owner"),
    @NamedQuery(name = "Job.userJobList", query = "SELECT j FROM Job j WHERE j.owner = :owner ORDER BY j.id DESC"),
    @NamedQuery(name = "Job.findByRemoteId", query = "SELECT j FROM Job j WHERE j.remoteId = :remoteId")})
public class Job implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @NotNull
    private String label;
    @Size(max = 2048)
    @Column(length = 2048)
    private String notes;
    @JoinColumn(name = "owner_id", referencedColumnName = "id")
    @ManyToOne
    private UserAccount owner;
    private String jobType;
    @JoinColumn(name = "uws_id", referencedColumnName = "id", nullable = true)
    @ManyToOne
    private UWS uws;
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date createdDate;
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date startedDate;
    @Temporal(javax.persistence.TemporalType.TIMESTAMP)
    private Date finishedDate;
    private String remoteId;
    private Phase phase;
    private String resultsEmail;

    @Transient
    private UWSJob uwsJob;
    @Transient
    private String uwsJobXml;
    @Transient
    private UWSParserManager parser;

    /**
     * updates attributes from local UWS job object (only if uwsjob isn't null
     * obviously)
     */
    public void updateFromUWSJob() {
        if (uwsJob != null) {
            this.startedDate = uwsJob.getStartTime();
            this.finishedDate = uwsJob.getEndTime();
            this.remoteId = uwsJob.getJobId();
            this.phase = uwsJob.getPhase();
        } else {
            Logger.getLogger(Job.class.getName()).warning("trying to update from non-existent uwsJob");
        }
    }

    /**
     * updates attributes according to data online on UWS server
     */
    public void updateFromUWS() {
        if (uws == null || remoteId == null) {
            return; // cant update without uws
        }
        String xml;
        try {
            xml = getUws().getJob(remoteId);
        } catch (IOException ex) {
            Logger.getLogger(Job.class.getName()).log(Level.SEVERE, "cannot update job", ex);
            return;
        }
        uwsJobXml = xml;
        uwsJob = getParser().parseJob(xml);
        if (uwsJob == null) {
            try {
                Phase uwsPhase = getUws().getJobPhase(remoteId);
                if (uwsPhase != null) {
                    this.phase = uwsPhase;
                }
            } catch (IOException e) {
                Logger.getLogger(Job.class.getName()).log(Level.SEVERE, "failed retrieving job phase", e);
            }
            return;
        }
        updateFromUWSJob();
    }

    public void start() throws IOException {
        String output = getUws().startJob(remoteId);
        uwsJob = getParser().parseJob(output);
        updateFromUWSJob();
    }

    public void abort() throws IOException {
        uwsJob = getParser().parseJob(getUws().abortJob(remoteId));
        updateFromUWSJob();
    }

    public void destroyOnUWS() {
        if (uws == null) {
            return;
        }
        try {
            getUws().destroyJob(remoteId);
        } catch (IOException ex) {
            Logger.getLogger(Job.class.getName()).log(Level.WARNING, "cant destroy job on UWS", ex.toString());
        }
    }

    private UWSParserManager getParser() {
        if (parser == null) {
            parser = UWSParserManager.getInstance();
        }
        return parser;
    }

    public Boolean isCompleted() {
        return (this.phase == Phase.COMPLETED);
    }

    //<editor-fold defaultstate="collapsed" desc="getters & setters">
    public Long getId() {
        return id;
    }

    public String getStringId() {
        return id.toString();
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getFinishedDate() {
        return finishedDate;
    }

    public void setFinishedDate(Date finishedDate) {
        this.finishedDate = finishedDate;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public void setOwner(UserAccount owner) {
        this.owner = owner;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public UWS getUws() {
        return uws;
    }

    public void setUws(UWS uws) {
        this.uws = uws;
    }

    public String getRemoteId() {
        return remoteId;
    }

    public void setUwsId(String remoteId) {
        this.remoteId = remoteId;
    }

    public UWSJob getUwsJob() {
        return uwsJob;
    }

    public void setUwsJob(UWSJob uwsJob) {
        this.uwsJob = uwsJob;
    }

    public Date getStartedDate() {
        return startedDate;
    }

    public void setStartedDate(Date startedDate) {
        this.startedDate = startedDate;
    }

    public String getResultsEmail() {
        return resultsEmail;
    }

    public void setResultsEmail(String resultsEmail) {
        this.resultsEmail = resultsEmail;
    }

    public long getExecutingTime() {
        Date sd = startedDate;
        Date fd = finishedDate;
        if (sd == null) {
            return 0;
        }
        if (fd == null) {
            sd = createdDate;
            fd = new Date();
        }
        return (fd.getTime() - sd.getTime()) / 1000;
    }

    public String getUwsJobXml() {
        return uwsJobXml;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Job)) {
            return false;
        }
        Job other = (Job) object;
        return !((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id)));
    }

    @Override
    public String toString() {
        return "Job{" + "id=" + id + ", label=" + label + ", notes=" + notes + ", owner=" + owner
                + ", jobType=" + jobType + ", uws=" + uws + ", createdDate=" + createdDate
                + ", finishedDate=" + finishedDate + ", remoteId=" + remoteId
                + ", phase=" + phase + ", uwsJob=" + uwsJob + '}';
    }
    //</editor-fold>
}
