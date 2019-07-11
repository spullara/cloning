package com.rits.cloning;

public class DepthException extends RuntimeException {
    public static DepthException SINGLETON = new DepthException();
    static {
        SINGLETON.setStackTrace(new StackTraceElement[0]);
    }
}
