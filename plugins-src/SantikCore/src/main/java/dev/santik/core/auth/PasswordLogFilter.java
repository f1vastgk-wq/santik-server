package dev.santik.core.auth;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.util.regex.Pattern;

/** Не даёт командам /reg и /login с паролями попадать в консоль и logs/latest.log. */
public final class PasswordLogFilter extends AbstractFilter {
    private static final Pattern P = Pattern.compile(
            ".*issued server command: /(reg|register|login|l)( .*)?$", Pattern.CASE_INSENSITIVE);

    @Override
    public Result filter(LogEvent event) {
        if (event.getMessage() != null) {
            String m = event.getMessage().getFormattedMessage();
            if (m != null && P.matcher(m).matches()) return Result.DENY;
        }
        return Result.NEUTRAL;
    }
}
