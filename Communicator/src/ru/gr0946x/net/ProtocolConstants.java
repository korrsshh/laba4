package ru.gr0946x.net;

public final class ProtocolConstants {
    public static final int DEFAULT_PORT = 9460;
    public static final String COMMAND_SEPARATOR = ":";
    public static final String AUTHOR_SEPARATOR = "||";
    public static final String FIELD_SEPARATOR = "|||";
    public static final String PARAM_SEPARATOR = "::";
    
    // AUTH commands
    public static final String AUTH_LOGIN = "LOGIN";
    public static final String AUTH_REGISTER = "REGISTER";
    
    // MESSAGE commands
    public static final String MSG_PRIVATE = "PRIVATE";
    public static final String MSG_BROADCAST = "BROADCAST";
    public static final String MSG_SEARCH = "SEARCH";
    
    // SYSTEM commands
    public static final String SYS_ONLINE_USERS = "ONLINE_USERS";
    public static final String SYS_HISTORY = "HISTORY";
}
