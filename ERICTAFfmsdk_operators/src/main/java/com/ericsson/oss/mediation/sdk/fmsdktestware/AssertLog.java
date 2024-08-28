package com.ericsson.oss.mediation.sdk.fmsdktestware;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import java.util.List;

public class AssertLog {
    private final static Logger LOGGER = LoggerFactory.getLogger(AssertLog.class);

    /**
     * Assert a value doesn't equal another value
     *
     * @param actual   Value to check
     * @param expected Compare against
     * @param message  Message to log if assertion fails
     */
    public static <T> void assertNotEquals(final T actual, final T expected, final String message) {
        try {
            Assert.assertNotEquals(actual, expected, message);
        } catch (AssertionError error) {
            LOGGER.error(message, error);
            throw error;
        }
    }

    /**
     * Assert a value i not NULL
     *
     * @param object  Value to check
     * @param message Message to log if assertion fails
     */
    public static void assertNotNull(final Object object, final String message) {
        try {
            Assert.assertNotNull(object, message);
        } catch (AssertionError error) {
            LOGGER.error(message, error);
            throw error;
        }
    }

    /**
     * Assert an Array is not NULL or empty
     *
     * @param list    Value to check
     * @param message Message to log if assertion fails
     */
    public static <T> void assertNotNullOrEmpty(final T[] list, final String message) {
        assertNotNull(list, message);
        Assert.assertNotEquals(list.length, 0, message);
    }

    /**
     * Assert a List is not NULL or empty
     *
     * @param list    Value to check
     * @param message Message to log if assertion fails
     */
    public static <T> void assertNotNullOrEmpty(final List<T> list, final String message) {
        assertNotNull(list, message);
        Assert.assertNotEquals(list.size(), 0, message);
    }

    /**
     * Assert a value is TRUE
     *
     * @param condition Value to check
     * @param message   Message to log if assertion fails
     */
    public static void assertTrue(final boolean condition, final String message) {
        try {
            Assert.assertTrue(condition, message);
        } catch (AssertionError error) {
            LOGGER.error(message, error);
            throw error;
        }
    }

    /**
     * Assert a List is a certain length
     *
     * @param list     List to check
     * @param expected expected length
     * @param message  Message to log if assertion fails
     */
    public static <T> void assertSize(final List<T> list, final int expected, final String message) {
        Assert.assertEquals(list.size(), expected, message);
    }

    /**
     * Assert a List contains a value
     *
     * @param list    List to check
     * @param value   Value to check for
     */
    public static <T> void assertContains(final List<T> list, final T value) {
        assertTrue(list.contains(value), "Value '" + value + "' not found in list '" + StringUtils.join(list, ", ") + "'");
    }


    /**
     * Fails a test with the given message and wrapping the original exception.
     *
     * @param message Message to log
     * @param error   Log exception
     */
    public static void fail(final String message, final Throwable error) {
        if (error != null) {
            LOGGER.error(message, error);
        }
        Assert.fail(message, error);
    }

    /**
     * Fails a test with the given message and wrapping the original exception.
     *
     * @param message Message to log
     */
    public static void fail(final String message) {
        fail(message, null);
    }
}
