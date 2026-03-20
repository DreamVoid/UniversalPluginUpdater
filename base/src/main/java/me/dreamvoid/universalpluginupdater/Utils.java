package me.dreamvoid.universalpluginupdater;

import java.util.logging.Logger;

public class Utils {
    private static Logger logger;

    public static void setLogger(Logger logger) {
        Utils.logger = logger;
    }

    public static Logger getLogger() {
        return logger;
    }
}
