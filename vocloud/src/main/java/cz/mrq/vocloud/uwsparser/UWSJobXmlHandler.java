package cz.mrq.vocloud.uwsparser;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * @author voadmin
 */
public class UWSJobXmlHandler extends DefaultHandler {

    private UWSJob job = new UWSJob();

    public UWSJob getJob() {
        return job;
    }
    private Elements current = Elements.UNKNOWN;
    

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        current = Elements.getElement(localName);
        
        if ("true".equals(attributes.getValue("xsi:nil"))) {
            current = Elements.UNKNOWN;
        }
        
        if (current == Elements.result) {
            String result = attributes.getValue(2);
            if (result == null) {
                // needed if job finishes with error
                result = attributes.getValue(0);
            }
            job.setResults(result);
            
            current = Elements.UNKNOWN;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if(current == Elements.UNKNOWN) return;
        
        String string = new String(ch, start, length);
        //Logger.getLogger(UWSParser.class.getName()).log(Level.WARNING, current.toString()+": "+string);
        switch (current) {
            case jobId:
                job.setJobId(string);
                break;
            case runId:
                job.setRunId(string);
                break;
            case ownerId:
                job.setOwner(string);
                break;
            case phase:
                job.setPhase(UWSJobPhase.getPhase(string));
                break;
            case startTime:
                job.setStartTime(string);
                break;
            case endTime:
                job.setEndTime(string);
                break;
            case executionDuration:
                job.setExecutionDuration(Long.parseLong(string));
                break;
            case destruction:
                job.setDestruction(string);
                break;
            case message:
                job.setErrorSummary(string);
                break;
            default:
                break;
        }
        current = Elements.UNKNOWN;
    }

    private enum Elements {

        jobId, runId, ownerId, phase, quote, startTime, endTime, executionDuration, destruction, results, parameters, parameter, job, result, errorSummary, message, UNKNOWN;

        public static Elements getElement(String phStr) {
            try {
                return valueOf(phStr);
            } catch (Exception ex) {
                return Elements.UNKNOWN;
            }

        }
    }
}
