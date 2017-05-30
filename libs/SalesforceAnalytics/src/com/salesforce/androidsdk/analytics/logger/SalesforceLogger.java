/*
 * Copyright (c) 2017-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.analytics.logger;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A simple logger that allows components to log statements of different log levels. This class
 * also provides the ability to break down logs at the component level and set different log
 * levels for different components. The available options for log output are console and file.
 *
 * @author bhariharan
 */
public class SalesforceLogger {

    private static final String TAG = "SalesforceLogger";
    private static final String LOG_LINE_FORMAT = "LEVEL: %s, TAG: %s, MESSAGE: %s";
    private static final String LOG_LINE_FORMAT_WITH_EXCEPTION = "LEVEL: %s, TAG: %s, MESSAGE: %s, EXCEPTION: %s";
    private static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(3);
    private static Map<String, SalesforceLogger> LOGGERS;

    /**
     * An enumeration of log levels.
     */
    public enum Level {
        OFF,
        ERROR,
        WARN,
        INFO,
        DEBUG,
        VERBOSE
    }

    private FileLogger fileLogger;
    private Context context;
    private Level logLevel;

    /**
     * Returns a logger instance associated with a named component.
     *
     * @param componentName Component name.
     * @param context Context.
     * @return Logger instance.
     */
    public synchronized static SalesforceLogger getLogger(String componentName, Context context) {
        if (LOGGERS == null) {
            LOGGERS = new HashMap<>();
        }
        if (!LOGGERS.containsKey(componentName)) {
            final SalesforceLogger logger = new SalesforceLogger(componentName, context);
            LOGGERS.put(componentName, logger);
        }
        return LOGGERS.get(componentName);
    }

    /**
     * Returns the set of components that have loggers associated with them.
     *
     * @return Set of components being logged.
     */
    public synchronized static Set<String> getComponents() {
        if (LOGGERS == null || LOGGERS.size() == 0) {
            return null;
        }
        Set<String> components = LOGGERS.keySet();
        if (components.size() == 0) {
            components = null;
        }
        return components;
    }

    /**
     * Wipes all components currently being logged. Used only by tests.
     */
    public synchronized static void flushComponents() {
        LOGGERS = null;
    }

    private SalesforceLogger(String componentName, Context context) {
        this.context = context;
        if (isDebugMode()) {
            logLevel = Level.DEBUG;
        } else {
            logLevel = Level.ERROR;
        }
        try {
            fileLogger = new FileLogger(context, componentName);
        } catch (IOException e) {
            Log.e(TAG, "Couldn't create file logger", e);
        }
    }

    private boolean isDebugMode() {
        boolean debugMode = true;
        try {
            final PackageManager pm = context.getPackageManager();
            if (pm != null) {
                final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
                if (pi != null) {
                    final ApplicationInfo ai = pi.applicationInfo;
                    if (ai != null && ((ai.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0)) {
                        debugMode = false;
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            debugMode = true;
        }
        return debugMode;
    }

    /**
     * Returns the instance of FileLogger associated with this component.
     *
     * @return FileLogger instance.
     */
    public FileLogger getFileLogger() {
        return fileLogger;
    }

    /**
     * Returns the log level currently being used.
     *
     * @return Log level.
     */
    public Level getLogLevel() {
        return logLevel;
    }

    /**
     * Sets the log level to be used.
     *
     * @param level Log level.
     */
    public synchronized void setLogLevel(Level level) {
        this.logLevel = level;
    }

    /**
     * Disables file logging.
     */
    public synchronized void disableFileLogging() {
        if (fileLogger != null) {
            fileLogger.setMaxSize(0);
        }
    }

    /**
     * Enables file logging.
     *
     * @param maxSize Maximum number of log lines allowed to be stored at a time.
     */
    public synchronized void enableFileLogging(int maxSize) {
        if (fileLogger != null) {
            fileLogger.setMaxSize(maxSize);
        }
    }

    /**
     * Returns if file logging is enabled or not.
     *
     * @return True - if enabled, False - otherwise.
     */
    public boolean isFileLoggingEnabled() {
        int maxSize = 0;
        if (fileLogger != null) {
            maxSize = fileLogger.getMaxSize();
        }
        return (maxSize > 0);
    }

    /**
     * Logs the message passed in at the level specified.
     *
     * @param level Log level.
     * @param tag Log tag.
     * @param message Log message.
     */
    public void log(Level level, String tag, String message) {
        switch (level) {
            case OFF:
                break;
            case ERROR:
                Log.e(tag, message);
                break;
            case WARN:
                Log.w(tag, message);
                break;
            case INFO:
                Log.i(tag, message);
                break;
            case DEBUG:
                Log.d(tag, message);
                break;
            case VERBOSE:
                Log.v(tag, message);
                break;
            default:
                Log.d(tag, message);
        }
        if (level != Level.OFF) {
            logToFile(level, tag, message, null);
        }
    }

    /**
     * Logs the message and throwable passed in at the level specified.
     *
     * @param level Log level.
     * @param tag Log tag.
     * @param message Log message.
     * @param e Throwable to be logged.
     */
    public void log(Level level, String tag, String message, Throwable e) {
        switch (level) {
            case OFF:
                break;
            case ERROR:
                Log.e(tag, message, e);
                break;
            case WARN:
                Log.w(tag, message, e);
                break;
            case INFO:
                Log.i(tag, message, e);
                break;
            case DEBUG:
                Log.d(tag, message, e);
                break;
            case VERBOSE:
                Log.v(tag, message, e);
                break;
            default:
                Log.d(tag, message, e);
        }
        if (level != Level.OFF) {
            logToFile(level, tag, message, e);
        }
    }

    private void logToFile(final Level level, final String tag, final String message, final Throwable e) {
        THREAD_POOL.execute(new Runnable() {

            @Override
            public void run() {
                if (fileLogger != null) {
                    String logLine;
                    if (e != null) {
                        logLine = String.format(LOG_LINE_FORMAT_WITH_EXCEPTION, level, tag, message, Log.getStackTraceString(e));
                    } else {
                        logLine = String.format(LOG_LINE_FORMAT, level, tag, message);
                    }
                    fileLogger.addLogLine(logLine);
                }
            }
        });
    }
}