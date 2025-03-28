package ru.neosvet.vestnewage.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jakewharton.threetenabp.AndroidThreeTen;

import org.threeten.bp.Clock;
import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.LocalTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.Locale;

import ru.neosvet.vestnewage.App;
import ru.neosvet.vestnewage.R;

public class DateUnit {
    public static final byte MONDAY = 1, SUNDAY = 0;
    public static final int DAY_IN_SEC = 86400, DAY_IN_MILLS = 86400000,
            HOUR_IN_MILLS = 3600000, MIN_IN_MILLS = 60000, SEC_IN_MILLS = 1000,
            GRAD = 60, DAY_IN_HOUR = 24, OFFSET_MSK = 10800;
    public static final long MONTH_IN_MILLS = 2592000000L;
    private DateTimeFormatter formatter = null;
    private LocalDate date;
    private LocalTime time = null;

    private DateUnit(LocalDate date, @Nullable LocalTime time) {
        AndroidThreeTen.init(App.context);
        this.date = date;
        this.time = time;
    }

    public static DateUnit putDays(int days) {
        return new DateUnit(LocalDate.ofEpochDay(days), null);
    }

    public static DateUnit putMills(long mills) {
        return putSeconds((int) (mills / SEC_IN_MILLS));
    }

    public static DateUnit putSeconds(int sec) {
        int days = sec / DAY_IN_SEC;
        int secs = sec % DAY_IN_SEC;
        return new DateUnit(LocalDate.ofEpochDay(days), LocalTime.ofSecondOfDay(secs));
    }

    public static DateUnit putYearMonth(int year, int month) {
        return new DateUnit(LocalDate.of(year, month, 1), null);
    }

    public static DateUnit initToday() {
        return new DateUnit(LocalDate.now(Clock.system(ZoneId.of("UTC"))), null);
    }

    public static DateUnit initNow() {
        return putMills(System.currentTimeMillis());
    }

    public static DateUnit initMskNow() {
        DateUnit d = putMills(System.currentTimeMillis());
        d.changeSeconds(DateUnit.OFFSET_MSK - d.getOffset());
        return d;
    }

    public static DateUnit parse(String s) {
        DateTimeFormatter fDate = null;
        switch (s.length()) {
            case 5:
                s = "01." + s;
            case 8:
                fDate = DateTimeFormatter
                        .ofPattern("dd.MM.yy")
                        .withLocale(Locale.US);
                break;
            case 9:
                fDate = DateTimeFormatter
                        .ofPattern("yyyy-M-dd")
                        .withLocale(Locale.US);
                break;
            case 10:
                fDate = DateTimeFormatter
                        .ofPattern(s.contains("-") ? "yyyy-MM-dd" : "dd.MM.yyyy")
                        .withLocale(Locale.US);
                break;
        }
        if (fDate != null)
            return new DateUnit(LocalDate.parse(s, fDate), null);
        boolean offset = s.contains("+03");
        if (s.length() == 16) { //for addition
            offset = true;
            fDate = DateTimeFormatter
                    .ofPattern("dd.MM.yyyy HH:mm")
                    .withLocale(Locale.US);
        } else if (s.length() == 14) {
            offset = true;
            fDate = DateTimeFormatter
                    .ofPattern("dd.MM.yy HH:mm")
                    .withLocale(Locale.US);
        } else if (s.contains("-")) { //2020-03-25T00:00:00+03:00
            s = s.substring(0, s.length() - 6).replace("T", " ");
            fDate = DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withLocale(Locale.US);
        } else {  //Fri, 15 Jun 2018 07:58:29 GMT or +0300
            s = s.substring(0, s.lastIndexOf(" ")); //remove GMT
            fDate = DateTimeFormatter
                    .ofPattern("EEE, dd MMM yyyy HH:mm:ss")
                    .withLocale(Locale.US);
        }
        LocalDateTime dateTime = LocalDateTime.parse(s, fDate);
        if (offset)
            dateTime = dateTime.minusHours(3);
        return new DateUnit(dateTime.toLocalDate(), dateTime.toLocalTime());
    }

    public static boolean isLongAgo(long time) {
        return System.currentTimeMillis() - time > HOUR_IN_MILLS * 3;
    }

    public static boolean isVeryLongAgo(long time) {
        return System.currentTimeMillis() - time > DAY_IN_MILLS;
    }

    public void createTime() {
        time = LocalTime.ofSecondOfDay(0);
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
        return App.context.getResources().getStringArray(R.array.months)[getMonth() - 1];
    }

    public String getCalendarString() {
        return getMonthString() + " " + date.getYear();
    }

    public String getMY() {
        if (formatter == null)
            formatter = DateTimeFormatter.ofPattern("MM.yy").withZone(ZoneId.systemDefault());
        return formatter.format(date);
    }

    public static String getDiffDate(long mills1, long mills2) {
        float t = (mills1 - mills2) / (float) SEC_IN_MILLS;
        int k;
        if (t < 59.95f) {
            if (t < 1) t = 1;
            else t = (int) t;
            k = 0;
        } else {
            t = t / 60f;
            if (t < 59.95f)
                k = 3;
            else {
                t = t / 60f;
                if (t < 23.95f)
                    k = 6;
                else {
                    t = t / 24f;
                    k = 9;
                }
            }
        }
        String result;
        String[] arrTime = App.context.getResources().getStringArray(R.array.time);
        if (t > 4.95f && t < 20.95f)
            result = formatFloat(t) + arrTime[1 + k];
        else {
            if (t == 1f)
                result = arrTime[k];
            else {
                int n = (t - Math.floor(t) < 0.95f ? 0 : 1);
                n = ((int) t + n) % 10;
                if (n == 1)
                    result = formatFloat(t) + " " + arrTime[k];
                else if (n > 1 && n < 5)
                    result = formatFloat(t) + arrTime[2 + k];
                else
                    result = formatFloat(t) + arrTime[1 + k];
            }
        }

        return result;
    }

    private static String formatFloat(float f) {
        String s = String.format(Locale.FRANCE, "%.1f", f);
        if (s.contains(",0"))
            return s.substring(0, s.length() - 2);
        return s;
    }

    public static long detectPeriod(long time) {
        int diff = (int) (System.currentTimeMillis() - time);
        if (diff < DateUnit.MIN_IN_MILLS)
            return 10 * DateUnit.SEC_IN_MILLS;
        if (diff < DateUnit.HOUR_IN_MILLS)
            return DateUnit.MIN_IN_MILLS;
        return 15 * DateUnit.MIN_IN_MILLS;
    }

    @NonNull
    public String toTimeString() {
        if (time == null)
            return "";
        ZoneOffset zoneOffset = ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
        LocalDateTime dateTime = LocalDateTime.of(date, time);
        dateTime = dateTime.plusSeconds(zoneOffset.getTotalSeconds());
        return dateTime.format(formatter);
    }

    @NonNull
    public String toAlterString() {
        ZoneOffset zoneOffset = ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now());
        LocalDateTime dateTime;
        DateTimeFormatter formatter;
        if (time == null) {
            formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());
            dateTime = LocalDateTime.of(date, LocalTime.of(0, 0, 0));
        } else {
            formatter = DateTimeFormatter.ofPattern("HH:mm, dd.MM.yyyy").withZone(ZoneId.systemDefault());
            dateTime = LocalDateTime.of(date, time);
        }
        dateTime = dateTime.plusSeconds(zoneOffset.getTotalSeconds());
        return dateTime.format(formatter);
    }

    @NonNull
    public String toShortDateString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yy").withZone(ZoneId.systemDefault());
        return date.format(formatter);
    }

    @NonNull
    public String toDateString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault());
        return date.format(formatter);
    }

    @NonNull
    @Override
    public String toString() {
        ZoneOffset zoneOffset = ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now());
        LocalDateTime dateTime;
        DateTimeFormatter formatter;
        if (time == null) {
            formatter = DateTimeFormatter.ofPattern("dd.MM.yy").withZone(ZoneId.systemDefault());
            dateTime = LocalDateTime.of(date, LocalTime.of(0, 0, 0));
        } else {
            formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yy").withZone(ZoneId.systemDefault());
            dateTime = LocalDateTime.of(date, time);
        }
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

    public int getHour() {
        return time.getHour();
    }

    public int getMinute() {
        return time.getMinute();
    }

    public void setSeconds(int seconds) {
        time = time.withSecond(seconds);
    }

    public void setMinutes(int min) {
        time = time.withMinute(min);
    }

    public void setHours(int hours) {
        time = time.withHour(hours);
    }

    public void changeSeconds(int offset) {
        if (time == null) return;
        int i = time.getSecond() + offset;
        if (i < 0) {
            changeMinutes(i / GRAD - 1);
            i = i % GRAD;
            if (i == 0)
                setSeconds(0);
            else
                setSeconds(i + GRAD);
        } else if (i >= GRAD) {
            changeMinutes(i / GRAD);
            setSeconds(i % GRAD);
        } else
            time = time.plusSeconds(offset);
    }

    public void changeMinutes(int offset) {
        if (time == null) return;
        int i = time.getMinute() + offset;
        if (i < 0) {
            changeHours(i / GRAD - 1);
            i = i % GRAD;
            if (i == 0)
                setMinutes(0);
            else
                setMinutes(i + GRAD);
        } else if (i >= GRAD) {
            changeHours(i / GRAD);
            setMinutes(i % GRAD);
        } else
            time = time.plusMinutes(offset);
    }

    public void changeHours(int offset) {
        if (time == null) return;
        int i = time.getHour() + offset;
        if (i < 0) {
            changeDay(i / DAY_IN_HOUR - 1);
            i = i % DAY_IN_HOUR;
            if (i == 0)
                setHours(0);
            else
                setHours(i + DAY_IN_HOUR);
        } else if (i >= DAY_IN_HOUR) {
            changeDay(i / DAY_IN_HOUR);
            setHours(i % DAY_IN_HOUR);
        } else
            time = time.plusHours(offset);
    }

    public int getOffset() {
        ZoneOffset zoneOffset = ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now());
        return zoneOffset.getTotalSeconds();
    }

    //@Override
    public boolean equalsDate(Object obj) {
        if (this == obj) return true;

        if (obj instanceof DateUnit) {
            DateUnit d = (DateUnit) obj;
           //if (toTimeString().equals(d.toTimeString()))
                return d.getDay() == getDay() && d.getMonth() == getMonth() && d.getYear() == getYear();
        }
        return false;
    }
}
