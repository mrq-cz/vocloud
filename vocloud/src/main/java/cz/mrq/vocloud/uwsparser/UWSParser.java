package cz.mrq.vocloud.uwsparser;

import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author voadmin
 */
public class UWSParser {

    public UWSJob parseJob(String xml) {
        UWSJobXmlHandler handler = new UWSJobXmlHandler();
        if (xml == null) {
            Logger.getLogger(UWSParser.class.getName()).log(Level.SEVERE, "No XML to parse");
            return null;
        }
        try {
            XMLReader saxParser = XMLReaderFactory.createXMLReader();
            saxParser.setContentHandler(handler);
            saxParser.setErrorHandler(handler);
            saxParser.parse(xml);
        } catch (IOException ex) {
            Logger.getLogger(UWSParser.class.getName()).log(Level.SEVERE, null, ex);

        } catch (SAXException ex) {
            Logger.getLogger(UWSParser.class.getName()).log(Level.SEVERE, null, ex);
        }

        return handler.getJob();
    }
}
