package net.followt;

import java.util.List;

public class PageNotExistException extends TwitterException {

    private static final long serialVersionUID = 4710874140528212485L;

    public PageNotExistException(String message, List<?> errors) {
        super(message, errors);
    }

}
