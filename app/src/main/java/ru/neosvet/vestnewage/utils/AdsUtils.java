package ru.neosvet.vestnewage.utils;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.data.ListItem;
import ru.neosvet.vestnewage.network.NeoClient;
import ru.neosvet.vestnewage.storage.AdsStorage;
import ru.neosvet.vestnewage.view.dialog.CustomDialog;

public class AdsUtils {
    public static final int TITLE = 0, LINK = 1, DES = 2;
    private AdsStorage storage;
    private CustomDialog alert;
    private final Context context;
    private long time = -1;
    private int index_ads = -1, index_warn = -1;
    private boolean isClosed = true, isNew;

    public AdsUtils(Context context) {
        this.context = context;
    }

    public boolean hasNew() {
        return isNew;
    }

    public int getIndex() {
        return index_ads;
    }

    public void setIndex(int index) {
        index_ads = index;
    }

    public int getWarnIndex() {
        return index_warn;
    }

    public long getTime() {
        if (time == -1) {
            open();
            Cursor cursor = storage.getTime();
            if (cursor.moveToFirst())
                time = Long.parseLong(cursor.getString(0));
            else
                time = 0;
            cursor.close();
        }
        return time;
    }

    public String[] getItem(int index) {
        open();
        Cursor cursor = storage.getAll();
        String[] m = new String[]{"", "", ""};
        if (!cursor.moveToFirst())
            return m;

        int iTitle = cursor.getColumnIndex(Const.TITLE);
        int iDes = cursor.getColumnIndex(Const.DESCTRIPTION);
        int iLink = cursor.getColumnIndex(Const.LINK);
        int n = 0;

        do {
            if (index == n) {
                m[TITLE] = cursor.getString(iTitle);
                m[DES] = cursor.getString(iDes);
                m[LINK] = cursor.getString(iLink);
                break;
            }
            n++;
        } while (cursor.moveToNext());
        cursor.close();
        return m;
    }

    public List<ListItem> loadList(boolean onlyUnread) throws Exception {
        List<ListItem> list = new ArrayList<>();
        Cursor cursor;
        String ad;
        open();
        if (onlyUnread) {
            cursor = storage.getUnread();
            ad = context.getString(R.string.ad) + ": ";
        } else {
            cursor = storage.getAll();
            ad = "";
        }
        if (!cursor.moveToFirst())
            return list;
        int iMode = cursor.getColumnIndex(Const.MODE);
        int iTitle = cursor.getColumnIndex(Const.TITLE);
        int iDes = cursor.getColumnIndex(Const.DESCTRIPTION);
        int iLink = cursor.getColumnIndex(Const.LINK);
        int iUnread = cursor.getColumnIndex(Const.UNREAD);
        int m;
        String t, d, l;
        boolean unread;

        do {
            t = cursor.getString(iTitle);
            d = cursor.getString(iDes);
            l = cursor.getString(iLink);
            m = cursor.getInt(iMode);
            unread = cursor.getInt(iUnread) == 1;
            if (onlyUnread && !unread)
                continue;
            switch (m) {
                case AdsStorage.MODE_T:
                    continue;
                case AdsStorage.MODE_U:
                    m = Integer.parseInt(t);
                    if (m > context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode) {
                        list.add(0, new ListItem(ad + context.getString(R.string.access_new_version)));
                    } else {
                        list.add(0, new ListItem(ad + context.getString(R.string.current_version)));
                    }
                    list.get(0).addHead(d);
                    list.get(0).addLink(context.getString(R.string.url_on_app));
                    break;
                default:
                    list.add(0, new ListItem(ad + t));
                    if (m != AdsStorage.MODE_TD)
                        list.get(0).addLink(l);
                    if (m != AdsStorage.MODE_TL)
                        list.get(0).addHead(d);
                    break;
            }
            if (!onlyUnread && unread)
                list.get(0).setDes(context.getString(R.string.new_section));

        } while (cursor.moveToNext());
        cursor.close();
        return list;
    }

    public void showAd(String title, final String link, String des) {
        ContentValues row = new ContentValues();
        row.put(Const.UNREAD, 0);
        open();
        if (!storage.updateByTitle(title, row)) {
            storage.updateByDes(des, row);
        }

        if (des.isEmpty()) {// only link
            Lib.openInApps(link, null);
            index_ads = -1;
            return;
        }

        if (!(context instanceof Activity))
            return;

        Activity act = (Activity) context;
        alert = new CustomDialog(act);
        alert.setTitle(context.getString(R.string.ad));
        alert.setMessage(des);

        if (link.equals("")) { // only des
            alert.setRightButton(context.getString(android.R.string.ok),
                    view -> alert.dismiss());
        } else {
            alert.setRightButton(context.getString(R.string.open_link), view -> {
                Lib.openInApps(link, null);
                alert.dismiss();
            });
        }

        alert.show(dialogInterface -> index_ads = -1);
    }

    private boolean update(BufferedReader br) throws Exception {
        String s;
        String[] m = new String[]{"", "", ""};
        List<String> titles = new ArrayList<>();
        byte mode, index = 0;
        index_warn = -1;
        boolean isNew = false;
        open();
        while ((s = br.readLine()) != null) {
            if (s.contains("<e>")) {
                if (m[TITLE].contains("<u>"))
                    mode = AdsStorage.MODE_U;
                else if (m[LINK].isEmpty())
                    mode = AdsStorage.MODE_TD;
                else if (m[DES].isEmpty())
                    mode = AdsStorage.MODE_TL;
                else
                    mode = AdsStorage.MODE_TLD;
                if (m[TITLE].contains("<w>"))
                    index_warn = index;
                index++;
                m[TITLE] = m[TITLE].substring(3);
                titles.add(m[TITLE]);
                if (!storage.existsTitle(m[TITLE])) {
                    isNew = true;
                    addRow(mode, m);
                }
                m = new String[]{"", "", ""};
            } else if (s.indexOf("<") != 0) {
                m[DES] += Const.N + s; //multiline des
            } else if (s.contains("<d>"))
                m[DES] = s.substring(3);
            else if (s.contains("<l>"))
                m[LINK] = s.substring(3);
            else
                m[TITLE] = s;
        }
        storage.deleteItems(titles);
        ContentValues row = new ContentValues();
        row.put(Const.MODE, AdsStorage.MODE_T);
        row.put(Const.UNREAD, 0);
        time = System.currentTimeMillis();
        row.put(Const.TITLE, time);
        if (!storage.updateTime(row))
            storage.insert(row);
        return isNew;
    }

    private void addRow(byte mode, String[] m) {
        ContentValues row = new ContentValues();
        row.put(Const.MODE, mode);
        row.put(Const.TITLE, m[TITLE]);
        row.put(Const.DESCTRIPTION, m[DES]);
        row.put(Const.LINK, m[LINK]);
        storage.insert(row);
    }

    public void close() {
        if (isClosed)
            return;
        storage.close();
        isClosed = true;
    }

    private void open() {
        if (!isClosed) return;
        storage = new AdsStorage();
        isClosed = false;
    }

    public int getUnreadCount() {
        open();
        Cursor cursor = storage.getUnread();
        int k = cursor.getCount();
        cursor.close();
        return k;
    }

    public void loadAds() throws Exception {
        isNew = false;
        long t = getTime();
        String s = "http://neosvet.ucoz.ru/ads_vna.txt";
        BufferedInputStream in = NeoClient.getStream(s);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        s = br.readLine();
        if (Long.parseLong(s) > t) {
            if (update(br)) {
                isNew = true;
                UnreadUtils unread = new UnreadUtils();
                unread.setBadge(getUnreadCount());
            }
        }
        br.close();
        in.close();
    }
}

