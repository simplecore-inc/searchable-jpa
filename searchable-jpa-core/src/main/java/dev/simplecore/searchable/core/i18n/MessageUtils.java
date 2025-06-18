package dev.simplecore.searchable.core.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

public class MessageUtils {
    private static MessageSource messageSource;

    private MessageUtils() {
        // Private constructor to prevent instantiation
    }

    public static void init(MessageSource source) {
        messageSource = source;
    }

    public static String getMessage(String code) {
        return getMessage(code, null);
    }

    public static String getMessage(String code, Object[] args) {
        if (messageSource == null) {
            ResourceBundleMessageSource bundleMessageSource = new ResourceBundleMessageSource();
            bundleMessageSource.setBasenames("messages/message");
            bundleMessageSource.setDefaultEncoding("UTF-8");
            bundleMessageSource.setFallbackToSystemLocale(false);
            messageSource = bundleMessageSource;
        }
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }
} 