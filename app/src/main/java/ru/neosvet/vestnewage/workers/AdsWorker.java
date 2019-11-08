package ru.neosvet.vestnewage.workers;

import android.content.Context;
import android.support.annotation.NonNull;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;

import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;

public class AdsWorker extends Worker {
    private Context context;

    public AdsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            String t = "0";
            BufferedReader br;
            File file = new File(context.getFilesDir() + File.separator + Const.ADS);
            if (file.exists()) {
                br = new BufferedReader(new FileReader(file));
                t = br.readLine();
                br.close();
            }
            String s = "http://neosvet.ucoz.ru/ads_vna.txt";
            Lib lib = new Lib(context);
            br = new BufferedReader(new InputStreamReader(lib.getStream(s)));
            s = br.readLine();
            if (Long.parseLong(s) > Long.parseLong(t)) {
                BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                bw.write(System.currentTimeMillis() + Const.N);
                while ((s = br.readLine()) != null) {
                    bw.write(s + Const.N);
                    bw.flush();
                }
                bw.close();
            }
            br.close();
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            Lib.LOG("AdsWorker error: " + e.getMessage());
        }
        return Result.failure();
    }
}
