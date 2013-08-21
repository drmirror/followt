package net.followt;

import java.util.List;

public class RateLimitExceededException extends TwitterException {

    private static final long serialVersionUID = 5051487864096303930L;

    public RateLimitExceededException (String message, List<?> errors) {
        super (message, errors);
    }
    
}
