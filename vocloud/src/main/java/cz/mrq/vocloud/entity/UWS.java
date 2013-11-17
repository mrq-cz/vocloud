package cz.mrq.vocloud.entity;

import cz.mrq.vocloud.tools.Toolbox;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Logger;

/**
 *
 * @author voadmin
 */
@Entity
@NamedQueries({
    @NamedQuery(name = "UWS.findAll", query = "SELECT u FROM UWS u"),
    @NamedQuery(name = "UWS.findById", query = "SELECT u FROM UWS u WHERE u.id = :id"),
    @NamedQuery(name = "UWS.findByEnabled", query = "SELECT u FROM UWS u WHERE u.enabled = :enabled"),
    @NamedQuery(name = "UWS.findByLocationUrl", query = "SELECT u FROM UWS u WHERE u.locationUrl = :locationUrl"),
    @NamedQuery(name = "UWS.findByLabel", query = "SELECT u FROM UWS u WHERE u.label = :label"),
    @NamedQuery(name = "UWS.findByPriority", query = "SELECT u FROM UWS u WHERE u.priority = :priority"),
    @NamedQuery(name = "UWS.findByThreads", query = "SELECT u FROM UWS u WHERE u.threads = :threads"),
    @NamedQuery(name = "UWS.findByType", query = "SELECT u FROM UWS u WHERE u.jobType = :jobType")})
public class UWS implements Serializable {
    
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    @NotNull
    @Size(min = 1, max = 255)
    @Column(unique=true, nullable=false)
    private String label;
    @NotNull
    @Size(max = 255)
    private String jobType;
    @NotNull
    @Size(max = 255)
    private String locationUrl;
    @NotNull
    private Boolean enabled;
    private int priority;
    private int threads;

    
    public String startJob(String jobId) throws IOException {
        return Toolbox.httpPost(locationUrl + "/" + jobId + "/phase?PHASE=RUN" );
    }
    
    public String abortJob(String jobId) throws IOException {
        return Toolbox.httpPost(locationUrl + "/" + jobId + "/phase?PHASE=ABORT" );
    }
    
    public void destroyJob(String jobId) throws IOException {
        Toolbox.httpPost(locationUrl + "/" + jobId + "/?ACTION=DELETE" );
    }
    
    public String createJob(String parameters) throws IOException {
        String req = locationUrl + "/?" + parameters;
        Logger.getLogger(UWS.class.toString()).info("posting "+req);
        return Toolbox.httpPost(req);
    }
    
    public String getJob(String jobId) throws IOException {
        return Toolbox.httpGet(locationUrl + "/" + jobId);
    }
    
    //<editor-fold defaultstate="collapsed" desc="getters setters...">
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getLocationUrl() {
        return locationUrl;
    }
    
    public void setLocationUrl(String location) {
        this.locationUrl = location;
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String name) {
        this.label = name;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    public int getThreads() {
        return threads;
    }
    
    public void setThreads(int threads) {
        this.threads = threads;
    }
    
    public String getType() {
        return jobType;
    }
    
    public void setType(String type) {
        this.jobType = type;
    }
    
    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }
    
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof UWS)) {
            return false;
        }
        UWS other = (UWS) object;
        return !((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id)));
    }
    
    @Override
    public String toString() {
        return "entity.UWS[ id=" + id + " ]";
    }
    //</editor-fold>
    
}
