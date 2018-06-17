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
    public static final byte MONDAY = 1, SUNDAY = 0;
    public static final int MONTH_IN_SEC = 2592000, DAY_IN_SEC = 86400,
            HOUR_IN_MILLS = 3600000, SEC_IN_MILLS = 1000;
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
        return new DateHelper(context, LocalDate.now(Clock.system(ZoneId.of("UTC"))), null);
    }

    public static DateHelper initNow(Context context) {
        Clock clock = Clock.system(ZoneId.of("UTC"));
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

    public String getDiffDate(long mills) {
        float time = getTimeInSeconds() - mills / SEC_IN_MILLS;
        int k;
        if (time < 59.95f) {
            if (time == 0)
                time = 1;
            k = 0;
        } else {
            time = time / 60f;
            if (time < 59.95f)
                k = 3;
            else {
                time = time / 60f;
                if (time < 23.95f)
                    k = 6;
                else {
                    time = time / 24f;
                    k = 9;
                }
            }
        }
        String result;
        if (time > 4.95f && time < 20.95f)
            result = formatFloat(time) + context.getResources().getStringArray(R.array.time)[1 + k];
        else {
            if (time == 1f)
                result = context.getResources().getStringArray(R.array.time)[k];
            else {
                int n = (time - Math.floor(time) < 0.95f ? 0 : 1);
                n = ((int) time + n) % 10;
                if (n == 1)
                    result = formatFloat(time) + " " + context.getResources().getStringArray(R.array.time)[k];
                else if (n > 1 && n < 5)
                    result = formatFloat(time) + context.getResources().getStringArray(R.array.time)[2 + k];
                else
                    result = formatFloat(time) + context.getResources().getStringArray(R.array.time)[1 + k];
            }
        }

        return result;
    }

    private String formatFloat(float f) {
        String s = String.format(Locale.FRANCE, "%.1f", f);
        if (s.contains(",0"))
            return s.substring(0, s.length() - 2);
        return s;
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

    public void changeDay(int offset) {
        date = date.plusDays(offset);
    }

    public void changeMonth(int offset) {
        date = date.plusMonths(offset);
    }

    public void changeYear(int offset) {
        date = date.plusYears(offset);
    }

    // TIME ~~~~~~~~~~~~~~~~~~~~~~~~
    public int getHours() {
        if (time == null) return 0;
        return time.getHour();
    }

    public void setSeconds(int seconds) {
        if (time == null) return;
        time = time.withSecond(seconds);
    }

    public void setMinutes(int min) {
        if (time == null) return;
        time = time.withMinute(min);
    }

    public void setHours(int hours) {
        if (time == null) return;
        time = time.withHour(hours);
    }

    public void changeSeconds(int offset) {
        if (time == null) return;
        time = time.plusSeconds(offset);
    }

    public void changeMinutes(int offset) {
        if (time == null) return;
        time = time.plusMinutes(offset);
    }

    public void changeHours(int offset) {
        if (time == null) return;
        time = time.plusHours(offset);
    }

    // BUILDER ~~~~~~~~~~~~~~~~~~~~~~~~
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
