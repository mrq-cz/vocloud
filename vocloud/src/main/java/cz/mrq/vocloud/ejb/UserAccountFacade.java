package cz.mrq.vocloud.ejb;

import cz.mrq.vocloud.entity.UserAccount;

import javax.annotation.Resource;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import java.util.Date;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author voadmin
 */
@Stateless
@LocalBean
public class UserAccountFacade extends AbstractFacade<UserAccount> {
    @PersistenceContext(unitName = "vokorelPU")
    private EntityManager em;
    
    @Resource(name = "vokorel-mail")
    private Session mailSession;

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    public UserAccountFacade() {
        super(UserAccount.class);
    }
    
    @Override
    public void create(UserAccount entity) {
        entity.setSince(new Date());
        super.create(entity);
    }
    
    public void resetPassword(UserAccount entity) {
        Random rng = new Random();
        int length = 6;
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        char[] text = new char[length];
        for (int i = 0; i < length; i++)
        {
            text[i] = characters.charAt(rng.nextInt(characters.length()));
        }
        String newPass = new String(text);
        
        // sent email with pass
        Message message = new MimeMessage(mailSession);
        try {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(entity.getEmail()));
                message.setFrom();
                message.setSubject("vo-korel: Password reset");
                message.setText("Your password for account "+entity.getUsername()+" was changed to: "+newPass);
                message.setHeader("X-Mailer", "My Mailer");
                Transport.send(message);
                Logger.getLogger(UserAccountFacade.class.getName()).log(Level.INFO, "Email with password has been sent to the user.");
        } catch (AddressException ex) {
            Logger.getLogger(UserAccountFacade.class.getName()).log(Level.SEVERE, null, ex);
        } catch (MessagingException ex) {
                Logger.getLogger(UserAccountFacade.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        changePassword(entity, newPass);
    }
    
    public void changePassword(UserAccount user, String password) {
        user.setPass(password);
        edit(user);
    }
    
    public Boolean resetPassword(String email) {
        UserAccount user = findByEmail(email);
        if (user == null) return false;
        resetPassword(user);   
        return true;
    }
     
    public UserAccount findByEmail(String email) {
        UserAccount user = null;
        Query q = getEntityManager().createNamedQuery("UserAccount.findByEmail");
        q.setParameter("email",email);
        //Logger.getGlobal().log(Level.INFO, "query for {0} is {1}", new Object[]{email, q.toString()});
        try {
            user = (UserAccount) q.getSingleResult();
        } catch (PersistenceException pe) {
            Logger.getGlobal().log(Level.WARNING, "query failed: {0}",pe.toString());
        }
        return user;
    }
    
    public UserAccount findByUsername(String username) {
        UserAccount user = null;
        Query q = getEntityManager().createNamedQuery("UserAccount.findByUsername");
        q.setParameter("username",username);
        //Logger.getGlobal().log(Level.INFO, "query for {0} is {1}", new Object[]{username, q.toString()});
        try {
            user = (UserAccount) q.getSingleResult();
        } catch (PersistenceException pe) {
            Logger.getGlobal().log(Level.WARNING, "query failed: {0}",pe.toString());
        }
        return user;
    }
    
    
    
}
