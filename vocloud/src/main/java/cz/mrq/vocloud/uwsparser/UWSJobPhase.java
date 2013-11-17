package cz.mrq.vocloud.uwsparser;

/**
 *
 * @author voadmin
 */
public enum UWSJobPhase {

    PENDING,
    QUEUED,
    EXECUTING,
    COMPLETED,
    ERROR,
    ABORTED,
    UNKNOWN,
    HELD,
    SUSPENDED,
    PROCESSING; // not standard phase, used for manager needs

    public static String getStr(UWSJobPhase ph) {
        return (ph == null) ? UWSJobPhase.UNKNOWN.name() : ph.name();
    }

    public static UWSJobPhase getPhase(String phStr) {
        try {
            return valueOf(phStr);
        } catch (Exception ex) {
            return UWSJobPhase.UNKNOWN;
        }

    }
}
