package ru.neosvet.vestnewage.helpers;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.view.View;

import java.io.BufferedReader;

import ru.neosvet.ui.dialogs.CustomDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.DataBase;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;

public class DevadsHelper {
    public static final String NAME = "devads";
    private final byte MODE_T = 0, MODE_U = 1, MODE_TLD = 2, MODE_TD = 3, MODE_TL = 4;
    private DataBase db;
    private CustomDialog alert;
    private final Context context;
    private long time = -1;
    private int index_ads = -1;
    private boolean isClosed;

    public DevadsHelper(Context context) {
        this.context = context;
        db = new DataBase(context, NAME);
        isClosed = false;
    }

    public int getIndex() {
        return index_ads;
    }

    public void setIndex(int index) {
        index_ads = index;
    }

    public long getTime() {
        if (time == -1) {
            Cursor cursor = db.query(NAME, new String[]{Const.TITLE}, Const.MODE + DataBase.Q, MODE_T);
            if (cursor.moveToFirst())
                time = Long.parseLong(cursor.getString(0));
            else
                time = 0;
            cursor.close();
        }
        return time;
    }

    public void loadList(ListAdapter list, boolean onlyUnread) throws Exception {
        Cursor cursor;
        String ad;
        if (onlyUnread) {
            cursor = db.query(NAME, null, Const.UNREAD + DataBase.Q, 1);
            ad = context.getResources().getString(R.string.ad) + ": ";
        } else {
            cursor = db.query(NAME, null);
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
                case MODE_T:
                    continue;
                case MODE_U:
                    m = Integer.parseInt(t);
                    if (m > context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode) {
                        list.insertItem(0, new ListItem(ad + context.getResources().getString(R.string.access_new_version)));
                    } else {
                        list.insertItem(0, new ListItem(ad + context.getResources().getString(R.string.current_version)));
                    }
                    list.getItem(0).addHead(d);
                    list.getItem(0).addLink(context.getResources().getString(R.string.url_on_app));
                    break;
                default:
                    list.insertItem(0, new ListItem(ad + t));
                    if (m != MODE_TD)
                        list.getItem(0).addLink(l);
                    if (m != MODE_TL)
                        list.getItem(0).addHead(d);
                    break;
            }
            if (!onlyUnread && unread)
                list.getItem(0).setDes(context.getResources().getString(R.string.new_section));

        } while (cursor.moveToNext());
        cursor.close();
        list.notifyDataSetChanged();
    }

    private void clear() {
        db.delete(NAME, Const.MODE + " != ?", MODE_T);
    }

    public void showAd(String title, final String link, String des) {
        ContentValues cv = new ContentValues();
        cv.put(Const.UNREAD, 0);
        if (db.update(NAME, cv, Const.TITLE + DataBase.Q, title) == 0) {
            db.update(NAME, cv, Const.DESCTRIPTION + DataBase.Q, des);
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
        alert.setTitle(context.getResources().getString(R.string.ad));
        alert.setMessage(des);

        if (link.equals("")) { // only des
            alert.setRightButton(context.getResources().getString(android.R.string.ok), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    alert.dismiss();
                }
            });
        } else {
            alert.setRightButton(context.getResources().getString(R.string.open_link), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Lib lib = new Lib(context);
                    lib.openInApps(link, null);
                    alert.dismiss();
                }
            });
        }

        alert.show(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                index_ads = -1;
            }
        });
    }

    public boolean update(BufferedReader br) throws Exception {
        String s;
        String[] m = new String[]{"", "", ""};
        byte mode, n = 0;
        Cursor cursor;
        boolean isNew = false;
        while ((s = br.readLine()) != null) {
            if (s.contains("<e>")) {
                if (m[0].contains("<u>"))
                    mode = MODE_U;
                else if (m[2].contains("<d>"))
                    mode = MODE_TLD;
                else if (m[1].contains("<d>"))
                    mode = MODE_TD;
                else
                    mode = MODE_TL;
                m[0] = m[0].substring(3);
                if (isNew)
                    addRow(mode, m);
                else {
                    cursor = db.query(NAME, new String[]{Const.TITLE}, Const.TITLE + DataBase.Q, m[0]);
                    if (!cursor.moveToFirst()) {
                        clear();
                        isNew = true;
                        addRow(mode, m);
                    }
                    cursor.close();
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
        ContentValues cv = new ContentValues();
        cv.put(Const.MODE, MODE_T);
        cv.put(Const.UNREAD, 0);
        time = System.currentTimeMillis();
        cv.put(Const.TITLE, time);
        if (db.update(NAME, cv, Const.MODE + DataBase.Q, MODE_T) == 0)
            db.insert(NAME, cv);
        return isNew;
    }

    private void addRow(byte mode, String[] m) {
        ContentValues cv = new ContentValues();
        cv.put(Const.MODE, mode);
        cv.put(Const.TITLE, m[0]);
        if (mode == MODE_U || mode == MODE_TD) {
            cv.put(Const.DESCTRIPTION, m[1].substring(3));
        } else {
            cv.put(Const.LINK, m[1].substring(3));
            if (mode == MODE_TLD)
                cv.put(Const.DESCTRIPTION, m[2].substring(3));
        }
        db.insert(NAME, cv);
    }

    public void close() {
        db.close();
        isClosed = true;
    }

    public void reopen() {
        if (isClosed) {
            db = new DataBase(context, NAME);
            isClosed = false;
        }
    }

    public int getUnreadCount() {
        Cursor cursor = db.query(NAME, new String[]{Const.TITLE}, Const.UNREAD + DataBase.Q, 1);
        int k = cursor.getCount();
        cursor.close();
        return k;
    }
}

