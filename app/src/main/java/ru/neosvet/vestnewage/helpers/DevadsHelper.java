package ru.neosvet.vestnewage.helpers;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import ru.neosvet.ui.dialogs.CustomDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.storage.AdsStorage;

public class DevadsHelper {
    private AdsStorage storage;
    private CustomDialog alert;
    private final Context context;
    private long time = -1;
    private int index_ads = -1, index_warn = -1;
    private boolean isClosed, isNew;

    public DevadsHelper(Context context) {
        this.context = context;
        storage = new AdsStorage(context);
        isClosed = false;
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
        Cursor cursor = storage.getAll();
        String[] m = new String[3];
        if (!cursor.moveToFirst())
            return m;

        int iTitle = cursor.getColumnIndex(Const.TITLE);
        int iDes = cursor.getColumnIndex(Const.DESCTRIPTION);
        int iLink = cursor.getColumnIndex(Const.LINK);
        int n = 0;

        do {
            if (index == n) {
                m[0] = cursor.getString(iTitle);
                m[1] = cursor.getString(iDes);
                m[2] = cursor.getString(iLink);
                break;
            }
            n++;
        } while (cursor.moveToNext());
        cursor.close();
        return m;
    }

    public void loadList(ListAdapter list, boolean onlyUnread) throws Exception {
        Cursor cursor;
        String ad;
        if (onlyUnread) {
            cursor = storage.getUnread();
            ad = context.getString(R.string.ad) + ": ";
        } else {
            cursor = storage.getAll();
            ad = "";
        }
        if (!cursor.moveToFirst())
            return;
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
                        list.insertItem(0, new ListItem(ad + context.getString(R.string.access_new_version)));
                    } else {
                        list.insertItem(0, new ListItem(ad + context.getString(R.string.current_version)));
                    }
                    list.getItem(0).addHead(d);
                    list.getItem(0).addLink(context.getString(R.string.url_on_app));
                    break;
                default:
                    list.insertItem(0, new ListItem(ad + t));
                    if (m != AdsStorage.MODE_TD)
                        list.getItem(0).addLink(l);
                    if (m != AdsStorage.MODE_TL)
                        list.getItem(0).addHead(d);
                    break;
            }
            if (!onlyUnread && unread)
                list.getItem(0).setDes(context.getString(R.string.new_section));

        } while (cursor.moveToNext());
        cursor.close();
        list.notifyDataSetChanged();
    }

    public void clear() {
        time = 0;
        storage.clear();
    }

    public void showAd(String title, final String link, String des) {
        ContentValues row = new ContentValues();
        row.put(Const.UNREAD, 0);
        if (!storage.updateByTitle(title, row)) {
            storage.updateByDes(des, row);
        }

        if (des.equals("")) {// only link
            Lib lib = new Lib(context);
            lib.openInApps(link, null);
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
                Lib lib = new Lib(context);
                lib.openInApps(link, null);
                alert.dismiss();
            });
        }

        alert.show(dialogInterface -> index_ads = -1);
    }

    private boolean update(BufferedReader br) throws Exception {
        String s;
        String[] m = new String[]{"", "", ""};
        byte mode, n = 0, index = 0;
        index_warn = -1;
        boolean isNew = false;
        while ((s = br.readLine()) != null) {
            if (s.contains("<e>")) {
                if (m[0].contains("<u>"))
                    mode = AdsStorage.MODE_U;
                else if (m[2].contains("<d>"))
                    mode = AdsStorage.MODE_TLD;
                else if (m[1].contains("<d>"))
                    mode = AdsStorage.MODE_TD;
                else
                    mode = AdsStorage.MODE_TL;
                if (m[0].contains("<w>"))
                    index_warn = index;
                index++;
                m[0] = m[0].substring(3);
                if (isNew)
                    addRow(mode, m);
                else if (!storage.existsTitle(m[0])) {
                    clear();
                    isNew = true;
                    addRow(mode, m);
                }
                n = 0;
                m[2] = "";
            } else if (s.indexOf("<") != 0) {
                m[n - 1] += Const.N + s; //multiline des
            } else {
                m[n] = s;
                n++;
            }
        }
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
        row.put(Const.TITLE, m[0]);
        if (mode == AdsStorage.MODE_U || mode == AdsStorage.MODE_TD) {
            row.put(Const.DESCTRIPTION, m[1].substring(3));
        } else {
            row.put(Const.LINK, m[1].substring(3));
            if (mode == AdsStorage.MODE_TLD)
                row.put(Const.DESCTRIPTION, m[2].substring(3));
        }
        storage.insert(row);
    }

    public void close() {
        if (isClosed)
            return;
        storage.close();
        isClosed = true;
    }

    public void reopen() {
        if (isClosed) {
            storage = new AdsStorage(context);
            isClosed = false;
        }
    }

    public int getUnreadCount() {
        Cursor cursor = storage.getUnread();
        int k = cursor.getCount();
        cursor.close();
        return k;
    }

    public void loadAds() throws Exception {
        isNew = false;
        long t = getTime();
        String s = "http://neosvet.ucoz.ru/ads_vna.txt";
        Lib lib = new Lib(context);
        BufferedInputStream in = new BufferedInputStream(lib.getStream(s));
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        s = br.readLine();
        if (Long.parseLong(s) > t) {
            if (update(br)) {
                isNew = true;
                UnreadHelper unread = new UnreadHelper(context);
                unread.setBadge(getUnreadCount());
                unread.close();
            }
        }
        br.close();
        in.close();
    }
}

