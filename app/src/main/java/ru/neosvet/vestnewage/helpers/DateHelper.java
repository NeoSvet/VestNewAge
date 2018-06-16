package ru.neosvet.vestnewage.helpers;

import android.content.Context;

import com.jakewharton.threetenabp.AndroidThreeTen;

import org.threeten.bp.Clock;
import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.LocalTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.Locale;

import ru.neosvet.vestnewage.R;

public class DateHelper {
    public static final short MONDAY = 1, SUNDAY = 0;
    public static final int MONTH_IN_SEC = 2592000, DAY_IN_SEC = 86400, SEC_IN_MILLS = 1000;
    private Context context;
    private DateTimeFormatter formatter = null;
    private LocalDate date;
    private LocalTime time = null;

    private DateHelper(Context context) {
        AndroidThreeTen.init(context);
        this.context = context;
    }

    private DateHelper(Context context, LocalDate date, LocalTime time) {
        AndroidThreeTen.init(context);
        this.context = context;
        this.date = date;
        this.time = time;
    }

    public static Builder newBuilder(Context context) {
        return new DateHelper(context).new Builder();
    }

    public static DateHelper initToday(Context context) {
        return new DateHelper(context, LocalDate.now(Clock.system(ZoneId.of("GMT"))), null);
    }

    public static DateHelper initNow(Context context) {
        Clock clock = Clock.system(ZoneId.of("GMT"));
        return new DateHelper(context, LocalDate.now(clock), LocalTime.now(clock));
    }

    public static DateHelper parse(Context context, String s) {
        //Fri, 15 Jun 2018 07:58:29 GMT or +0300
        boolean offset = s.contains("+0300");
        s = s.substring(0, s.lastIndexOf(" ")); //remove GMT
        DateTimeFormatter fDate = DateTimeFormatter
                .ofPattern("EEE, dd MMM yyyy HH:mm:ss")
                .withLocale(Locale.US);
        LocalDateTime dateTime = LocalDateTime.parse(s, fDate);
        if (offset)
            dateTime = dateTime.minusHours(3);
        return new DateHelper(context, dateTime.toLocalDate(), dateTime.toLocalTime());
    }

    public long getTimeInSeconds() {
        int sec = 0;
        if (time != null)
            sec = time.toSecondOfDay();
        return date.toEpochDay() * DAY_IN_SEC + sec;
    }

    public long getTimeInMills() {
        return getTimeInSeconds() * SEC_IN_MILLS;
    }

    public int getTimeInDays() {
        return (int) date.toEpochDay();
    }

    public String getMonthString() {
        return context.getResources().getStringArray(R.array.months)[getMonth() - 1];
    }

    public String getMY() {
        if (formatter == null)
            formatter = DateTimeFormatter.ofPattern("MM.yy").withZone(ZoneId.systemDefault());
        return formatter.format(date);
    }

    @Override
    public String toString() {
        ZoneOffset zoneOffset = ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now());
        LocalDateTime dateTime;
        if (time == null)
            dateTime = LocalDateTime.of(date, LocalTime.of(0, 0, 0));
        else
            dateTime = LocalDateTime.of(date, time);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yy").withZone(ZoneId.systemDefault());
        dateTime = dateTime.plusSeconds(zoneOffset.getTotalSeconds());
        return dateTime.format(formatter);
    }

    // DATE ~~~~~~~~~~~~~~~~~~~~~~~~
    public int getDay() {
        return date.getDayOfMonth();
    }

    public int getDayWeek() {
        return date.getDayOfWeek().getValue();
    }

    public int getMonth() {
        return date.getMonth().getValue();
    }

    public int getYear() {
        return date.getYear();
    }

    public void setDay(int day) {
        date = date.withDayOfMonth(day);
    }

    public void setMonth(int month) {
        date = date.withMonth(month);
    }

    public void setYear(int year) {
        date = date.withYear(year);
    }

    public void plusDay(int day) {
        date = date.plusDays(day);
    }

    public void plusMonth(int month) {
        date = date.plusMonths(month);
    }

    // TIME ~~~~~~~~~~~~~~~~~~~~~~~~
    public void setSeconds(int seconds) {
        if (time == null) return;
        time = time.withSecond(seconds);
    }

    public void minusSeconds(int seconds) {
        if (time == null) return;
        time = time.minusSeconds(seconds);
    }

    public void setMinutes(int min) {
        if (time == null) return;
        time = time.withMinute(min);
    }

    public void minusMinutes(int min) {
        if (time == null) return;
        time = time.minusMinutes(min);
    }

    public void plusMinutes(int min) {
        if (time == null) return;
        time = time.plusMinutes(min);
    }

    public void setHours(int hours) {
        if (time == null) return;
        time = time.withHour(hours);
    }

    public void plusHours(int hours) {
        if (time == null) return;
        time = time.plusHours(hours);
    }

    public int getHours() {
        if (time == null) return 0;
        return time.getHour();
    }

    public class Builder {
        public Builder() {
        }

        public Builder setDays(int days) {
            date = LocalDate.ofEpochDay(days);
            return this;
        }

        public Builder setMills(long mills) {
            return setSeconds((int) (mills / SEC_IN_MILLS));
        }

        public Builder setSeconds(int sec) {
            int days = sec / DAY_IN_SEC;
            int secs = sec % DAY_IN_SEC;
            date = LocalDate.ofEpochDay(days);
            time = LocalTime.ofSecondOfDay(secs);
            return this;
        }

        public Builder setYearMonth(int year, int month) {
            date = LocalDate.of(year, month, 1);
            return this;
        }

        public DateHelper build() {
            return DateHelper.this;
        }
    }
}
