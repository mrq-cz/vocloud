package cz.rk.vocloud.view;

import cz.mrq.vocloud.ejb.UWSTypeFacade;
import cz.mrq.vocloud.entity.UWSType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Named;
import org.primefaces.component.menuitem.UIMenuItem;
import org.primefaces.component.submenu.UISubmenu;

/**
 *
 * @author radio.koza
 */
@Named
@RequestScoped
public class JobsCreateMenuBean {

    private UISubmenu jobsCreateSubmenu;
    @EJB
    private UWSTypeFacade facade;

    private Set<String> possibleTypeIds;
    
    @PostConstruct
    private void init() {
        //populate menu
        List<UWSType> possibleTypes = facade.findAllowedNonRestrictedTypes();
        possibleTypeIds = new HashSet<>();
        jobsCreateSubmenu = new UISubmenu();
        jobsCreateSubmenu.setLabel("Create job");
        for (UWSType type : possibleTypes) {
            possibleTypeIds.add(type.getStringIdentifier());
            UIMenuItem item = new UIMenuItem();
            item.setAjax(false);
            item.setId(type.getStringIdentifier());
            item.setValue(type.getShortDescription());
//            item.setParam("action", "#{jobsCreateMenuBean.navigateToJobCreate('" + type.getStringIdentifier() + "')}");
            item.setActionExpression(FacesContext.getCurrentInstance().getApplication().getExpressionFactory().
                    createMethodExpression(FacesContext.getCurrentInstance().getELContext(), "#{jobsCreateMenuBean.navigateToCreateJob('" + type.getStringIdentifier() + "')}", String.class, new Class[]{String.class}));
            jobsCreateSubmenu.getChildren().add(item);
        }
    }

    public UISubmenu getSubmenuBinding() {
        return this.jobsCreateSubmenu;
    }

    public void setSubmenuBinding(UISubmenu submenu) {
        this.jobsCreateSubmenu = submenu;
    }
    
    public String navigateToCreateJob(String uwsType){
        //just to be sure check that it is one of possible types
        if (!possibleTypeIds.contains(uwsType)){
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "This type is no longer possible to invoke", ""));
            return null;
        }
        return "/jobs/create?faces-redirect=true&uwsType=" + uwsType;
    }
}
