
package cz.rk.vocloud.view;

import cz.mrq.vocloud.ejb.JobFacade;
import cz.mrq.vocloud.ejb.UserSessionBean;
import cz.mrq.vocloud.entity.Job;
import cz.mrq.vocloud.entity.UserAccount;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortOrder;

/**
 *
 * @author radio.koza
 */
@Named
@ViewScoped
public class JobListBean implements Serializable {
    
    private UserAccount userAcc;
    @EJB
    private UserSessionBean usb;
    @EJB
    private JobFacade jobFacade;
    private LazyDataModel<Job> model;
    
    
    @PostConstruct
    private void init(){
        //initialize user acc
        userAcc = usb.getUser();
        model = new LazyDataModel<Job>() {
            private static final long serialVersionUID = 1L;

            @Override
            public List<Job> load(int first, int pageSize, String sortField, SortOrder sortOrder, Map<String, Object> filters) {
                List<Job> jobs = jobFacade.findUserJobsPaginated(userAcc, first, pageSize);//sorting and filtering will not be used
                model.setRowCount((int)(long)jobFacade.countUserJobs(userAcc));
                return jobs;
            }
        
            
        };
    }
    
    public void pollRefresh(){
        //nothing to do here
    }

    public LazyDataModel<Job> getModel() {
        return model;
    }

    public void setModel(LazyDataModel<Job> model) {
        this.model = model;
    }
    
    public void startJob(Job job){
        jobFacade.startJob(job);
    }
    
    public void abortJob(Job job){
        jobFacade.abortJob(job);
    }
    
    public void deleteJob(Job job){
        //destroy remote job - idc about results - and delete job dir
        jobFacade.deleteJob(job);
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", "Job with id " + job.getId() + " was deleted"));
    }
    
    public String showDetail(Job job){
        return "details?faces-redirect=true&jobId=" + job.getId();
    }
}
