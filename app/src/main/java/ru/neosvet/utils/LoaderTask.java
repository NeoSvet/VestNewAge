package ru.neosvet.utils;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import ru.neosvet.vestnewage.BrowserActivity;
import ru.neosvet.vestnewage.MainActivity;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.SiteFragment;

public class LoaderTask extends AsyncTask<String, Integer, Boolean> implements Serializable {
    private final int MAX = 6;
    private transient AppCompatActivity act;
    private transient Lib lib;
    private transient ProgressDialog di;
    private int prog = 0, sub_prog = 0;
    private String msg;
    private boolean boolStart = true;

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
//        prog = values[0];
        di.setProgress(prog);
        di.setMessage(msg + (sub_prog > 0 ? " (" + sub_prog + ")" : ""));
    }

    public void setAct(AppCompatActivity act) {
        this.act = act;
        lib = new Lib(act);
        if (di != null && boolStart) {
            di.dismiss();
            showD();
        }
    }

    public LoaderTask(AppCompatActivity act) {
        this.act = act;
        lib = new Lib(act);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (act instanceof MainActivity) {
            msg = act.getResources().getString(R.string.start);
            showD();
        }
    }

    private void showD() {
        di = new ProgressDialog(act);
        di.setTitle(act.getResources().getString(R.string.load));
        di.setMessage(msg);
        di.setMax(MAX);
        di.setProgress(prog);
        di.setIndeterminate(false);
        di.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        di.setProgressDrawable(act.getResources().getDrawable(R.drawable.progress_bar));
        di.setOnCancelListener(new ProgressDialog.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                boolStart = false;
            }
        });
        di.show();
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        if (act instanceof BrowserActivity) {
            ((BrowserActivity) act).finishLoad(result);
        } else {
            di.dismiss();
            if (boolStart) {
                ((MainActivity) act).finishAllLoad(result);
                boolStart = false;
            }
        }
    }

    private File getFile(String name) {
        return new File(act.getFilesDir() + name);
    }

    @Override
    protected Boolean doInBackground(String... params) {
        try {
            if (params.length == 0) {
                startTimer();
                refreshLists();
                downloadAll();
                return true;
            }
            String p0 = params[0], p1 = params[1];
            if (p0.contains(".png"))
                downloadFile(p0, p1);
            else {
                downloadStyle(params.length == 3);
                downloadPage(p0, p1, true);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            prog = MAX;
        }
        return false;
    }

    private void refreshLists() throws Exception {
        MainActivity mact = (MainActivity) act;
        sub_prog = 0;
        msg = act.getResources().getString(R.string.refresh_list)
                + " " + act.getResources().getString(R.string.rss);
        publishProgress();
        SummaryTask t1 = new SummaryTask(mact);
        t1.downloadList();
        if (!boolStart) return;

        msg = act.getResources().getString(R.string.refresh_list)
                + " " + act.getResources().getString(R.string.calendar);
        publishProgress();
        Date d = new Date();
        CalendarTask t2 = new CalendarTask(mact);
        int max_y = d.getYear() + 1, max_m = 12;
        prog++;
        sub_prog = 0;
        for (int y = 116; y < max_y && boolStart; y++) {
            if (y == max_y - 1)
                max_m = d.getMonth() + 1;
            for (int m = 0; m < max_m && boolStart; m++) {
                t2.downloadCalendar(y, m);
                sub_prog++;
            }
        }
        if (!boolStart) return;

        prog++;
        sub_prog = 0;
        msg = act.getResources().getString(R.string.refresh_list)
                + " " + act.getResources().getString(R.string.book);
        publishProgress();
        BookTask t3 = new BookTask(mact);
        t3.downloadData(true);
        if (!boolStart) return;
        t3.downloadData(false);

        prog++;
        msg = act.getResources().getString(R.string.refresh_list)
                + " " + act.getResources().getString(R.string.main);
        publishProgress();
        SiteTask t4 = new SiteTask(mact);
        String[] url = new String[]{
                Lib.SITE,
                Lib.SITE + "novosti.html",
                Lib.SITE + "media.html"
        };
        String[] file = new String[]{
                getFile(SiteFragment.MAIN).toString(),
                getFile(SiteFragment.NEWS).toString(),
                getFile(SiteFragment.MEDIA).toString()
        };
        for (int i = 0; i < url.length && boolStart; i++) {
//            Lib.LOG(url[i]);
            t4.downloadList(url[i]);
            t4.saveList(file[i]);
        }
    }

    private void downloadAll() throws Exception {
        if (!boolStart) return;
        File[] d = new File[]{
//                getFile(CalendarActivity.CALENDAR),
                getFile(Lib.LIST),
                getFile(SiteFragment.MAIN),
                getFile(SiteFragment.MEDIA)
        };
        prog++;
        msg = act.getResources().getString(R.string.download)
                + " " + act.getResources().getString(R.string.kat_n_pos);
        publishProgress();
        for (int i = 0; i < d.length && boolStart; i++) {
            if (i == 2) {
                sub_prog = 0;
                prog++;
                msg = act.getResources().getString(R.string.download)
                        + " " + act.getResources().getString(R.string.materials);
                publishProgress();
            }
            if (d[i].isDirectory()) {
                File[] f = d[i].listFiles();
                for (int j = 0; j < f.length && boolStart; j++) {
                    if (f[j].isFile())
                        downloadList(f[j].toString());
                }
            } else
                downloadList(d[i].toString());
//            Lib.LOG("sub_prog="+sub_prog);
        }
        prog = MAX;
        publishProgress();
    }

    private void startTimer() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    while (boolStart && prog < MAX) {
//                        TimeUnit.SECONDS.sleep(1);
                        Thread.sleep(1000);
                        publishProgress();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

    private void downloadFile(String url, String file) {
        try {
            File f = new File(file);
            //if(f.exists()) f.delete();
            OutputStream out = new FileOutputStream(f, false);
            byte[] buf = new byte[1024];
            InputStream in = new BufferedInputStream(lib.getStream(url));
            int i;
            while ((i = in.read(buf)) > 0) {
                out.write(buf, 0, i);
                out.flush();
            }
            out.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void downloadStyle(boolean bReplaceStyle) throws Exception {
        final File fLight = getFile(Lib.LIGHT);
        final File fDark = getFile(Lib.DARK);
        if (!fLight.exists() || !fDark.exists() || bReplaceStyle) {
            String line = "";
            int i;
            InputStream in = new BufferedInputStream(lib.getStream(Lib.SITE + "org/otk/tpl/otk/css/style-print.css"));
            BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
            BufferedWriter bwLight = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fLight)));
            BufferedWriter bwDark = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fDark)));
            for (i = 0; i < 7; i++) {
                br.readLine();
            }
            while ((line = br.readLine()) != null) {
                bwLight.write(line + Lib.N);
                if (line.contains("#000")) {
                    line = line.replace("000000", "000").replace("#000", "#fff");
                } else
                    line = line.replace("#fff", "#000");
                line = line.replace("333333", "333").replace("#333", "#ccc");
                bwDark.write(line + Lib.N);
                if (line.contains("body")) {
                    line = "    padding-left: 5px;\n    padding-right: 5px;";
                    bwLight.write(line + Lib.N);
                    bwDark.write(line + Lib.N);
                } else if (line.contains("print2")) {
                    line = br.readLine().replace("8pt/9pt", "12pt");
                    bwLight.write(line + Lib.N);
                    bwDark.write(line + Lib.N);
                }
                bwLight.flush();
                bwDark.flush();
            }
            br.close();
            bwLight.close();
            bwDark.close();
        }
    }

    private void downloadList(String path) throws Exception {
//        Lib.LOG("list="+path);
        downloadStyle(false);
        if (!boolStart) return;
        File f = new File(path);//context.getFilesDir() +
        BufferedReader br = new BufferedReader(new FileReader(f));
        String s;
        while ((s = br.readLine()) != null && boolStart) {
            if (s.contains(Lib.LINK)) {
                s = s.substring(Lib.LINK.length());
//                Lib.LOG("link=" + s);
                if (s.contains("/"))
                    f = lib.getPageFile(s);
                else {
                    f = getFile("/" + BrowserActivity.ARTICLE);
                    if (!f.exists())
                        f.mkdir();
                    f = getFile("/" + BrowserActivity.ARTICLE + "/" + s);
                }
//                Lib.LOG("path=" + f.toString());
                if (!f.exists()) {
//                    Lib.LOG("download");
                    downloadPage(s + Lib.PRINT, f.toString(), false);
                    sub_prog++;
                }
            }
        }
        br.close();
    }

    private void downloadPage(String link, String path, boolean bCounter) throws Exception {
        String line, s, url, end;
        url = Lib.SITE + link;
        if (link.contains(Lib.PRINT)) {
            end = "<!--/row-->";
        } else {
            end = "page-title";
        }
        InputStream in = new BufferedInputStream(lib.getStream(url));
        BufferedReader br = new BufferedReader(new InputStreamReader(in), 1000);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path)));
        boolean b = false;
        if (url.contains(Lib.PRINT))
            url = url.substring(0, url.length() - Lib.PRINT.length());
        while ((line = br.readLine()) != null) {
            if (b) {
                if (line.contains(end)) {
                    line = getNow();
                    line = "<div style=\"margin-top:20px\" class=\"print2\">\n"
                            + act.getResources().getString(R.string.page) + " " + url +
                            "<br>Copyright " + act.getResources().getString(R.string.copyright)
                            + " Leonid Maslov 2004-20" + line.substring(line.lastIndexOf(".") + 1) + "<br>"
                            + act.getResources().getString(R.string.downloaded) + " " + line;
                    bw.write(line + "\n</div></body></html>");
                    bw.flush();
                    b = false;
                } else if (line.contains("<")) {
                    line = line.trim();
                    if (line.length() < 7) continue;
//                    if(line.contains("color")) {
//                        i=line.indexOf("color");
//                    }
                    if (line.contains("iframe")) {
                        if (!line.contains("</iframe"))
                            line += br.readLine();
                        if (line.contains("?"))
                            s = line.substring(line.indexOf("video/") + 6,
                                    line.indexOf("?"));
                        else
                            s = line.substring(line.indexOf("video/") + 6,
                                    line.indexOf("\"", 65));
                        s = "<a href=\"https://vimeo.com/" +
                                s + "\">" +
                                act.getResources().getString
                                        (R.string.video_on_vimeo) + "</a>";
                        if (line.contains("center"))
                            line = "<center>" + s + "</center>";
                        else line = s;
                    }
                    bw.write(line.replace("color", "cvet") + Lib.N);
                    bw.flush();
                }
            } else if (line.contains("<h1")) {
                writeStartPage(bw, line);
                br.readLine();
                b = true;
            } else if (line.contains("counter") && bCounter) { // counters
                sendCounter(line);
                if (line.contains("rambler"))
                    break;
            }
        }
        br.close();
        bw.close();
        if (bCounter)
            checkNoreadList(link);
    }

    private String getNow() {
        Date d = new Date(System.currentTimeMillis());
        DateFormat df = new SimpleDateFormat("HH:mm:ss dd.MM.yy");
        return df.format(d);
    }

    private void sendCounter(String line) {
        int i = 0;
        while ((i = line.indexOf("img src", i)) > -1) {
            i += 9;
            final String link_counter = line.substring(i, line.indexOf("\"", i));
            new Thread(new Runnable() {
                public void run() {
                    try {
                        lib.getStream(link_counter);
                    } catch (Exception ex) {
                    }
                }
            }).start();
        }
    }

    private void checkNoreadList(String link) {
        try {
            File file = new File(act.getFilesDir() + File.separator + Lib.NOREAD);
            if (file.exists()) {
                boolean b = false;
                String t, l;
                final String N = "\n";
                StringBuilder f = new StringBuilder();
                BufferedReader br = new BufferedReader(new InputStreamReader(act.openFileInput(file.getName())));
                while ((t = br.readLine()) != null) {
                    l = br.readLine();
                    if (l.contains(link)) {
                        b = true;
                    } else {
                        f.append(t);
                        f.append(N);
                        f.append(l);
                        f.append(N);
                    }
                }
                br.close();
                if (b) {
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(act.openFileOutput(Lib.NOREAD, act.MODE_PRIVATE)));
                    bw.write(f.toString());
                    bw.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeStartPage(BufferedWriter bw, String line) throws Exception {
        bw.write("<html><head>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n<title>");
        int i;
        String s = line.trim().replace("&nbsp;", " ");
        while ((i = s.indexOf("<")) > -1) {
            s = s.substring(0, i) + s.substring(s.indexOf(">", i) + 1);
        }
        bw.write(s + "</title>\n<link rel=\"stylesheet\" type=\"text/css\" href=\".." +
                Lib.STYLE + "\">\n</head><body>");
        bw.write("\n" + line.substring(line.indexOf("<")) + "\n");
        bw.flush();
    }
}
