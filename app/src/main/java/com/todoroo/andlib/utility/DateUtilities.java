/*
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */

package com.todoroo.andlib.utility;

import static org.tasks.Strings.isNullOrEmpty;
import static org.tasks.date.DateTimeUtils.newDateTime;
import static org.tasks.time.DateTimeUtils.currentTimeMillis;

import android.content.Context;
import android.text.format.DateFormat;
import androidx.annotation.Nullable;
import com.todoroo.astrid.data.Task;
import org.tasks.BuildConfig;
import org.tasks.R;
import org.tasks.locale.Locale;
import org.tasks.time.DateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;

public class DateUtilities {

  /** Represents a single hour */
  public static final long ONE_HOUR = 3600000L;
  /** Represents a single day */
  public static final long ONE_DAY = 24 * ONE_HOUR;
  /** Represents a single week */
  public static final long ONE_WEEK = 7 * ONE_DAY;
  /** Represents a single minute */
  public static final long ONE_MINUTE = 60000L;

  static Boolean is24HourOverride = null;

  /** Returns unixtime for current time */
  public static long now() {
    return currentTimeMillis();
  }

  /* ======================================================================
   * =========================================================== formatters
   * ====================================================================== */

  private static boolean is24HourFormat(Context context) {
    return BuildConfig.DEBUG && is24HourOverride != null
        ? is24HourOverride
        : DateFormat.is24HourFormat(context);
  }

  public static String getTimeString(Context context, DateTime date) {
    String value;
    if (is24HourFormat(context)) {
      value = "HH:mm";
    } else if (date.getMinuteOfHour() == 0) {
      value = "h a";
    } else {
      value = "h:mm a";
    }
    return date.toString(value);
  }

  public static String getLongDateString(DateTime date, java.util.Locale locale) {
    return getFullDate(date, locale, FormatStyle.LONG);
  }

  /**
   * @param date date to format
   * @return date, with month, day, and year
   */
  public static String getDateString(Context context, DateTime date) {
    return getRelativeDay(
        context, date.getMillis(), Locale.getInstance().getLocale(), FormatStyle.MEDIUM);
  }

  static String getWeekday(DateTime date, java.util.Locale locale) {
    return date.toLocalDate().getDayOfWeek().getDisplayName(TextStyle.FULL, locale);
  }

  /** @return weekday */
  public static String getWeekdayShort(DateTime date, java.util.Locale locale) {
    return date.toLocalDate().getDayOfWeek().getDisplayName(TextStyle.SHORT, locale);
  }

  public static String getLongDateStringWithTime(long timestamp, java.util.Locale locale) {
    return getFullDateTime(newDateTime(timestamp), locale, FormatStyle.LONG);
  }

  public static String getRelativeDateTime(
      Context context, long date, java.util.Locale locale, FormatStyle style) {
    return getRelativeDateTime(context, date, locale, style, false);
  }

  public static String getRelativeDateTime(
      Context context, long date, java.util.Locale locale, FormatStyle style, boolean lowercase) {
    String day = getRelativeDay(context, date, locale, isAbbreviated(style), lowercase);
    if (!isNullOrEmpty(day)) {
      if (Task.hasDueTime(date)) {
        String time = getTimeString(context, newDateTime(date));
        return newDateTime().startOfDay().equals(newDateTime(date).startOfDay()) ? time : String.format("%s %s", day, time);
      } else {
        return day;
      }
    }
    return Task.hasDueTime(date)
            ? getFullDateTime(newDateTime(date), locale, style)
            : getFullDate(newDateTime(date), locale, style);
  }

  private static boolean isAbbreviated(FormatStyle style) {
    return style == FormatStyle.SHORT || style == FormatStyle.MEDIUM;
  }

  public static String getRelativeDay(
      Context context,
      long date,
      java.util.Locale locale,
      FormatStyle style) {
    return getRelativeDay(context, date, locale, style, false);
  }

  public static String getRelativeDay(
      Context context,
      long date,
      java.util.Locale locale,
      FormatStyle style,
      boolean lowercase) {
    String relativeDay = getRelativeDay(context, date, locale, isAbbreviated(style), lowercase);
    return isNullOrEmpty(relativeDay)
        ? getFullDate(newDateTime(date), locale, style)
        : relativeDay;
  }

  private static String getFullDate(DateTime date, java.util.Locale locale, FormatStyle style) {
    return stripYear(
        DateTimeFormatter.ofLocalizedDate(style)
            .withLocale(locale)
            .format(date.toLocalDate()),
        newDateTime().getYear());
  }

  private static String getFullDateTime(DateTime date, java.util.Locale locale, FormatStyle style) {
    return stripYear(
        DateTimeFormatter.ofLocalizedDateTime(style, FormatStyle.SHORT)
            .withLocale(locale)
            .format(date.toLocalDateTime()),
        newDateTime().getYear());
  }

  private static String stripYear(String date, int year) {
    return date.replaceAll("(?: de |, |/| )?" + year + "(?:年|년 | г\\.)?", "");
  }

  private static @Nullable String getRelativeDay(Context context, long date, java.util.Locale locale, boolean abbreviated, boolean lowercase) {
    DateTime startOfToday = newDateTime().startOfDay();
    DateTime startOfDate = newDateTime(date).startOfDay();

    if (startOfToday.equals(startOfDate)) {
      return context.getString(lowercase ? R.string.today_lowercase : R.string.today);
    }

    if (startOfToday.plusDays(1).equals(startOfDate)) {
      return context.getString(
          abbreviated
              ? R.string.tmrw
              : lowercase ? R.string.tomorrow_lowercase : R.string.tomorrow);
    }

    if (startOfDate.plusDays(1).equals(startOfToday)) {
      return context.getString(
          abbreviated
              ? R.string.yest
              : lowercase ? R.string.yesterday_lowercase : R.string.yesterday);
    }

    if (Math.abs(startOfToday.getMillis() - startOfDate.getMillis()) <= DateUtilities.ONE_DAY * 6) {
      DateTime dateTime = newDateTime(date);
      return abbreviated
          ? DateUtilities.getWeekdayShort(dateTime, locale)
          : DateUtilities.getWeekday(dateTime, locale);

    }
    return null;
  }
}
