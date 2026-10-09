package com.regivolley.testsupport;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures everything the application and its libraries log anywhere in the JVM (root logger, at the levels the configuration enables) while a test runs, to
 * prove what never reaches a log. The in-process SMTP server of the tests ({@code com.icegreen}) is left out: it plays the
 * <em>remote</em> mail server and logs the whole conversation by design.
 */
public final class LogCapture implements AutoCloseable {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    public LogCapture() {
        appender.start();
        root.addAppender(appender);
    }

    /** Every captured line: formatted message, logger name, MDC values and any throwable (class, message, stack), as one text. */
    public String everything() {
        List<String> lines = new ArrayList<>();
        for (ILoggingEvent event : events()) {
            lines.add(event.getLevel() + " " + event.getLoggerName() + " " + event.getFormattedMessage() + " " + event.getMDCPropertyMap());
            if (event.getThrowableProxy() instanceof ThrowableProxy proxy) {
                lines.add(proxy.getThrowable().toString());
                for (StackTraceElement element : proxy.getThrowable().getStackTrace()) {
                    lines.add(element.toString());
                }
            }
        }
        return String.join("\n", lines);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list).stream().filter(event -> !event.getLoggerName().startsWith("com.icegreen")).toList();
    }

    @Override
    public void close() {
        root.detachAppender(appender);
    }
}
