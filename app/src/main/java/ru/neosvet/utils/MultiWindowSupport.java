package ru.neosvet.utils;

import android.support.annotation.RequiresApi;
import android.view.ViewGroup;
import android.widget.TextView;

import ru.neosvet.vestnewage.R;

/**
 * Created by NeoSvet on 17.02.2018.
 */
@RequiresApi(24)
public class MultiWindowSupport {
    public static void resizeFloatTextView(TextView tv, boolean isInMultiWindowMode) {
        ViewGroup.LayoutParams params = tv.getLayoutParams();
        if (isInMultiWindowMode) {
            float dpi = tv.getResources().getDisplayMetrics().density;
            params.width = (int) (40 * dpi);
            params.height = (int) (40 * dpi);
        } else {
            params.width = (int) tv.getResources().getDimension(R.dimen.fab_size);
            params.height = (int) tv.getResources().getDimension(R.dimen.fab_size);
        }
        tv.setLayoutParams(params);
    }
}
