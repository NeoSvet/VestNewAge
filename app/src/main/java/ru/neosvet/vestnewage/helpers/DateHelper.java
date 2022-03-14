package ru.neosvet.vestnewage.helpers;

import android.content.Context;

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

import ru.neosvet.utils.Const;
import ru.neosvet.vestnewage.R;

public class DateHelper {
    public static final byte MONDAY = 1, SUNDAY = 0;
    public static final int DAY_IN_SEC = 86400, //MONTH_IN_SEC = 2592000,
            HOUR_IN_MILLS = 3600000, SEC_IN_MILLS = 1000;
    private final Context context;
    private DateTimeFormatter formatter = null;
    private LocalDate date;
    private LocalTime time;

//    private DateHelper(Context context) {
//        AndroidThreeTen.init(context);
//        this.context = context;
//    }

    private DateHelper(Context context, LocalDate date, @Nullable LocalTime time) {
        AndroidThreeTen.init(context);
        this.context = context;
        this.date = date;
        this.time = time;
    }

    public static DateHelper putDays(Context context, int days) {
        return new DateHelper(context, LocalDate.ofEpochDay(days), null);
    }

    public static DateHelper putMills(Context context, long mills) {
        return putSeconds(context, (int) (mills / SEC_IN_MILLS));
    }

    public static DateHelper putSeconds(Context context, int sec) {
        int days = sec / DAY_IN_SEC;
        int secs = sec % DAY_IN_SEC;
        return new DateHelper(context, LocalDate.ofEpochDay(days), LocalTime.ofSecondOfDay(secs));
    }

    public static DateHelper putYearMonth(Context context, int year, int month) {
        return new DateHelper(context, LocalDate.of(year, month, 1), null);
    }

    public static DateHelper initToday(Context context) {
        return new DateHelper(context, LocalDate.now(Clock.system(ZoneId.of("UTC"))), null);
    }

    public static DateHelper initNow(Context context) {
        Clock clock = Clock.system(ZoneId.of("UTC"));
        return new DateHelper(context, LocalDate.now(clock), LocalTime.now(clock));
    }

    public static DateHelper parse(Context context, String s) {
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
                        .ofPattern("yyyy-MM-dd")
                        .withLocale(Locale.US);
                break;
        }
        if (fDate != null)
            return new DateHelper(context, LocalDate.parse(s, fDate), null);
        boolean offset = s.contains("+0300");
        if (s.contains("-")) { //2020-03-25T00:00:00+03:00
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

    public String getCalendarString() {
        return getMonthString() + Const.N + date.getYear();
    }

    public String getMY() {
        if (formatter == null)
            formatter = DateTimeFormatter.ofPattern("MM.yy").withZone(ZoneId.systemDefault());
        return formatter.format(date);
    }

    public String getDiffDate(long mills) {
        float time = getTimeInSeconds() - mills / (float) SEC_IN_MILLS;
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
        if (offset < 0 && time.getHour() == 0 && time.getMinute() == 0 && time.getSecond() == 0)
            this.changeDay(-1);
        time = time.plusSeconds(offset);
    }

    public void changeMinutes(int offset) {
        if (time == null) return;
        if (offset < 0 && time.getHour() == 0 && time.getMinute() == 0)
            this.changeDay(-1);
        time = time.plusMinutes(offset);
    }
}
