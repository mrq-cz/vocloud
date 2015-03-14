package cz.rk.vocloud.filesystem.model;

import java.io.Serializable;
import java.util.Date;

/**
 *
 * @author radio.koza
 */
public abstract class FilesystemItem implements Serializable{
    
    private final String name;
    private final String prefix;
    
    protected FilesystemItem(String name, String prefix){
        this.name = name;
        this.prefix = prefix;
    }
    
    /**
     * Can return null in case the item is folder.
     * @return 
     */
    public abstract Long getSizeInBytes();
    
    public abstract Date getLastModified();
    
    public abstract boolean isFolder();

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }
    
    
    
}
