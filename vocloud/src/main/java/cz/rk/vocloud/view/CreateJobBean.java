package cz.rk.vocloud.view;

import cz.mrq.vocloud.ejb.UWSTypeFacade;
import cz.mrq.vocloud.entity.UWSType;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import org.apache.commons.io.IOUtils;
import org.primefaces.event.FileUploadEvent;

/**
 *
 * @author radio.koza
 */
@Named
@ViewScoped
public class CreateJobBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(CreateJobBean.class.getName());

    @EJB
    private UWSTypeFacade facade;

    private UWSType chosenUwsType;

    private String configurationJson;

    @PostConstruct
    private void init() {
        String uwsTypeStrId = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("uwsType");
        if (uwsTypeStrId == null) {
            //undefined param
            return;//page will be rendered with error message
        }
        chosenUwsType = facade.findByStringIdentifier(uwsTypeStrId);
    }

    public boolean isNonRestrictedUwsTypeFound() {
        return chosenUwsType != null && !chosenUwsType.getRestricted();
    }

    public boolean isRestrictedUwsTypeFound() {
        return chosenUwsType == null && chosenUwsType.getRestricted();
    }

    public UWSType getChosenUwsType() {
        return chosenUwsType;
    }

    public String checkNonRestrictedParamValidity() {
        if (isNonRestrictedUwsTypeFound()) {
            return null;//no navigation
        }
        //otherwise redirect to home page
        return "/index?faces-redirect=true";
    }

    public String checkRestrictedParamValidity() {
        if (isRestrictedUwsTypeFound()) {
            return null;//no navigation
        }
        //otherwise redirect to home page
        return "/index?faces-redirect=true";
    }

    public String getConfigurationJson() {
        return configurationJson;
    }

    public void setConfigurationJson(String configurationJson) {
        this.configurationJson = configurationJson;
    }

    public void handleConfigUpload(FileUploadEvent event) {
        StringWriter writer = new StringWriter();
        FacesMessage message = null;
        try {
            IOUtils.copy(event.getFile().getInputstream(), writer, "UTF-8");
            IOUtils.closeQuietly(event.getFile().getInputstream());
            configurationJson = writer.toString();
            message = new FacesMessage("Success", event.getFile().getFileName() + " was uploaded.");
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Exception during loading uploaded file to memory", ex);
            message = new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", "Upload was NOT successful");
        }
        FacesContext.getCurrentInstance().addMessage(null, message);
    }

}
