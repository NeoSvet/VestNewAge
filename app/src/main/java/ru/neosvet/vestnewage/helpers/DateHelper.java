package ru.neosvet.vestnewage.helpers;

import android.content.Context;

import com.jakewharton.threetenabp.AndroidThreeTen;

import org.threeten.bp.Clock;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.temporal.ChronoField;
import org.threeten.bp.temporal.ChronoUnit;
import org.threeten.bp.temporal.TemporalAccessor;

import ru.neosvet.utils.Lib;

public class DateHelper {
    private final String ZONE_MOSCOW = "Europe/Moscow";
    private DateTimeFormatter formatter = null;
    private Instant date;

    public DateHelper(Context context) {
        AndroidThreeTen.init(context);
        date = Instant.now(Clock.system(ZoneId.of(ZONE_MOSCOW)));
    }

    public DateHelper(long time) {
        this.date = date; //todo
    }

    public DateHelper(int year, int month, int day) {
        this.date = date; //todo
    }

    public String getMY() {
        if (formatter == null)
            formatter = DateTimeFormatter.ofPattern("MM.yy").withZone(ZoneId.of(ZONE_MOSCOW));
        return formatter.format(date);
    }

    public long getTime() {
        //return date.getLong(ChronoField.INSTANT_SECONDS);
        return date.toEpochMilli(); //debug
    }

    public int getDay() {
        return date.get(ChronoField.DAY_OF_MONTH);
    }

    public int getDayWeek() {
        return date.get(ChronoField.DAY_OF_WEEK);
    }

    public int getMonth() {
        return date.get(ChronoField.MONTH_OF_YEAR);
    }

    public String getMonthString() {
        //getResources().getStringArray(R.array.months)[date.getMonth()]
        DateTimeFormatter df = DateTimeFormatter.ofPattern("MMMM").withZone(ZoneId.of(ZONE_MOSCOW));
        return df.format(date);
    }

    public int getYear() {
        TemporalAccessor ta = date;
        //Temporal ta = Clock.systemUTC().instant();
        return ta.get(ChronoField.YEAR);
    }

    public void setDay(int day) {
        date.with(ChronoField.DAY_OF_MONTH, day);
    }

    public void setMonth(int month) {
        date.with(ChronoField.MONTH_OF_YEAR, month);
    }

    public void setYear(int year) {
        date.with(ChronoField.YEAR, year);
    }

    public void plusDay(int day) {
        date.plus(day, ChronoUnit.DAYS);
    }

    public void plusMonth(int month) {
        date.plus(month, ChronoUnit.MONTHS);
    }

    public static long parse(String s) {
        Lib.LOG("parse: "+s);
        return 0;
    }

    public String getTimeString() {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yy").withZone(ZoneId.systemDefault());
        TemporalAccessor ta = date;
        return df.format(ta);
    }

//    public class Builder {
//
//        private Builder() {
//            // private constructor
//        }
//
//        public Builder setUserId(String userId) {
//            DateHelper.this.userId = userId;
//            return this;
//        }
//
//        public Builder setToken(String token) {
//            DateHelper.this.token = token;
//            return this;
//        }
//
//        public DateHelper build() {
//            return DateHelper.this;
//        }
//    }
}
