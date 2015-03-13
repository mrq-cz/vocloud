package cz.rk.vocloud.view;

import cz.rk.vocloud.filesystem.FilesystemManipulator;
import cz.rk.vocloud.filesystem.model.FilesystemItem;
import java.util.List;
import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.inject.Named;

/**
 *
 * @author radio.koza
 */
@Named
@RequestScoped
public class FilesystemViewBean {
    
    @EJB
    private FilesystemManipulator fsm;
    
    private String prefix = "/";//todo
    
    
    public List<FilesystemItem> getFilesystemItemList(){
        return fsm.listFilesystemItems(prefix);
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    
}
