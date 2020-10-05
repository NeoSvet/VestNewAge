package ru.neosvet.ui.dialogs;

import android.app.Activity;
import android.content.DialogInterface;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import ru.neosvet.vestnewage.R;

public class CustomDialog {
    private Activity act;
    private AlertDialog dialog;
    private View dialogView;
    private EditText input = null;

    public CustomDialog(Activity act) {
        this.act = act;
        LayoutInflater inflater = act.getLayoutInflater();
        dialogView = inflater.inflate(R.layout.dialog_layout, null);
    }

    public void show(DialogInterface.OnDismissListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(act);
        builder.setView(dialogView);
        dialog = builder.create();
        if (listener != null)
            dialog.setOnDismissListener(listener);
        dialog.show();
    }

    public void dismiss() {
        dialog.dismiss();
    }

    public void setTitle(String title) {
        TextView tv = (TextView) dialogView.findViewById(R.id.title);
        tv.setText(title);
    }

    public void setMessage(@Nullable String msg) {
        TextView tv = (TextView) dialogView.findViewById(R.id.message);
        if (msg == null)
            tv.setVisibility(View.GONE);
        else
            tv.setText(msg);
    }

    public void setLeftButton(String title, View.OnClickListener click) {
        Button button = (Button) dialogView.findViewById(R.id.leftButton);
        button.setText(title);
        button.setOnClickListener(click);
        button.setVisibility(View.VISIBLE);
    }

    public void setRightButton(String title, View.OnClickListener click) {
        Button button = (Button) dialogView.findViewById(R.id.rightButton);
        button.setText(title);
        button.setOnClickListener(click);
        button.setVisibility(View.VISIBLE);
    }

    public void setInputText(String text, TextWatcher watcher) {
        input = (EditText) dialogView.findViewById(R.id.input);
        input.setVisibility(View.VISIBLE);
        input.setText(text);
        input.setSelection(text.length());
        input.addTextChangedListener(watcher);
    }

    public String getInputText() {
        if (input == null)
            return "";
        else
            return input.getText().toString();
    }
}
