package ru.neosvet.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;

import ru.neosvet.vestnewage.R;

public class ProgressDialog extends Dialog {
    private Context context;
    private int max;
    private TextView tvTitle, tvMessage;
    private ProgressBar progressBar;

    public ProgressDialog(Context context, int max) {
        super(context);
        this.context = context;
        this.max = max;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_progress);

        tvTitle = (TextView) findViewById(R.id.title);
        tvTitle.setText(context.getResources().getString(R.string.load));
        tvMessage = (TextView) findViewById(R.id.message);
        progressBar = (ProgressBar) findViewById(R.id.progress);
        if (max == 0) {
            progressBar.setVisibility(View.GONE);
            findViewById(R.id.circle).setVisibility(View.VISIBLE);
        } else
            progressBar.setMax(max);
    }

    public void setProgress(int progress) {
        progressBar.setProgress(progress);
    }

    public void setTitle(String title) {
        tvTitle.setText(title);
    }

    public void setMessage(String message) {
        tvMessage.setText(message);
    }
}