package cc.lotuscard.utils.exception;

/**
 * @author Endless
 * @date 11/01/2018
 */

public class TimeoutException extends RuntimeException {
    public TimeoutException(String msg) {
        super(msg);
    }
}
