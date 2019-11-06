package ru.neosvet.utils;

/**
 * Created by NeoSvet on 14.01.2018.
 */

public interface Const {
    int MAX_ON_PAGE = 15, TIMEOUT = 20;
    String SITE = "http://blagayavest.info/", SITE2 = "http://medicina.softlan.lclients.ru/";
    String N = "\n", NN = "\n\n", AND = "&", BR = "<br>", KV_OPEN = "“", KV_CLOSE = "”";
    String LIGHT = "/style/light.css", DARK = "/style/dark.css", HTML = ".html", PRINT = "?styletpl=print";
    String ENCODING = "cp1251", USER_AGENT = "User-Agent", COOKIE = "Cookie", SET_COOKIE = "Set-Cookie";
    String TASK = "task", HREF = "href", DIALOG = "dialog", POEMS = "poems", FIRST = "first";

    String COUNT_IN_MENU = "count_in_menu", START_NEW = "start_new", START_SCEEN = "start_screen", CUR_ID = "cur_id", TAB = "tab";
    String DAY = "Day", MONTH = "Month", YEAR = "Year", UNREAD = "Unread", ERROR = "ERROR", LIST = "list", PAGE = "page", RENAME = "rename";
    String START = "start", END = "end", MODE = "mode", STRING = "string", EMAIL = "email", PASSWORD = "password", PANEL = "panel";
    String LOGIN = "login", GET_WORDS = "get_words", SELECT_WORD = "select_word", SELECT = "select", CURRENT_DATE = "current_date";
    String TIME = "time", SUMMARY = "Summary", PROM = "Prom", POSLANIYA = "pos", KATRENY = "kat", OTKR = "otkr", ADS = "ads";
    String MSG = "msg", PROG = "prog", MAX = "max", FROM_OTKR = "from_otkr";

    byte TURN_OFF = -1, SCREEN_MENU = 0, SCREEN_CALENDAR = 1, SCREEN_SUMMARY = 2;
}
