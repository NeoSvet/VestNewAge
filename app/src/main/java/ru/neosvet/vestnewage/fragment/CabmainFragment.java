package ru.neosvet.vestnewage.fragment;

import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;

import ru.neosvet.ui.NeoFragment;
import ru.neosvet.ui.SoftKeyboard;
import ru.neosvet.ui.dialogs.CustomDialog;
import ru.neosvet.utils.Const;
import ru.neosvet.utils.Lib;
import ru.neosvet.vestnewage.R;
import ru.neosvet.vestnewage.activity.CabpageActivity;
import ru.neosvet.vestnewage.helpers.ProgressHelper;
import ru.neosvet.vestnewage.list.ListAdapter;
import ru.neosvet.vestnewage.list.ListItem;
import ru.neosvet.vestnewage.model.CabModel;
import ru.neosvet.vestnewage.model.SummaryModel;
import ru.neosvet.vestnewage.model.basic.NeoState;
import ru.neosvet.vestnewage.model.basic.NeoViewModel;
import ru.neosvet.vestnewage.workers.CabWorker;

public class CabmainFragment extends NeoFragment {
    public static String error = null;
    private ListAdapter adMain;
    private SoftKeyboard softKeyboard;
    private CheckBox cbRemEmail, cbRemPassword;
    private EditText etEmail, etPassword;
    private View fabEnter, fabExit, pMain;
    private byte mode_list = 0;
    private CabModel model;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.cabmain_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        setViews();
        restoreState(savedInstanceState);
        model = new ViewModelProvider(this).get(CabModel.class);
        if (ProgressHelper.isBusy())
            setStatus(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (error != null)
            act.status.setError(error);
    }

    @Override
    public void onStatusClick(boolean reset) {
        error = null;
        if (reset) {
            act.status.setError(null);
            return;
        }
        act.status.onClick();
    }

    @Override
    public boolean onBackPressed() {
        if (ProgressHelper.isBusy()) {
            ProgressHelper.cancelled();
            ProgressHelper.setBusy(false);
            return false;
        }
        if (mode_list == CabModel.LOGIN)
            return true;
        else {
            mode_list--;
            if (mode_list == CabModel.LOGIN) {
                pMain.setVisibility(View.VISIBLE);
                fabEnter.setVisibility(View.VISIBLE);
                fabExit.setVisibility(View.GONE);
                adMain.clear();
                loginList();
            } else { //CabModel.CABINET
                mode_list = CabModel.LOGIN;
                initLoad();
                model.login(etEmail.getText().toString(), etPassword.getText().toString());
            }
            return false;
        }
    }

    @Override
    public void onChanged(Data result) {
        if (result.getBoolean(Const.START, false)) {
            act.status.loadText();
            return;
        }
        if (!result.getBoolean(Const.FINISH, false))
            return;
        ProgressHelper.setBusy(false);
        String error = result.getString(Const.ERROR);
        if (error != null) {
            if (error.equals("Connection reset") || error.equals("Read timed out"))
                error = getString(R.string.cab_fail);
            act.status.setError(error);
            return;
        }
        act.status.setLoad(false);
        switch (result.getInt(Const.MODE, 0)) {
            case CabWorker.SELECTED_WORD:
                int select = result.getInt(Const.SELECT, -1);
                if (select == -1)
                    initCabinet(getString(R.string.selected_status),
                            result.getString(Const.DESCTRIPTION));
                else
                    initCabinet(getString(R.string.selected_status),
                            adMain.getItem(select).getTitle());
                break;
            case CabWorker.NO_SELECTED:
                initCabinet(getString(R.string.send_status),
                        getString(R.string.select_status));
                break;
            case CabWorker.WORD_LIST:
                initWordList(result.getStringArray(Const.DESCTRIPTION));
                break;
            case CabWorker.TIMEOUT:
                initCabinet(getString(R.string.send_status),
                        result.getString(Const.DESCTRIPTION));
                break;
            case CabWorker.ERROR:
                final CustomDialog alert = new CustomDialog(act);
                alert.setTitle(getString(R.string.error));
                alert.setMessage(result.getString(Const.DESCTRIPTION));
                alert.setRightButton(getString(android.R.string.ok), view -> alert.dismiss());
                alert.show(null);
                return;
        }
        pMain.setVisibility(View.GONE);
        fabEnter.setVisibility(View.GONE);
        fabExit.setVisibility(View.VISIBLE);
    }

    private void restoreState(Bundle state) {
        if (state == null) {
            SharedPreferences pref = act.getSharedPreferences(this.getClass().getSimpleName(), Context.MODE_PRIVATE);
            String s = pref.getString(Const.EMAIL, "");
            if (s.length() > 0) {
                cbRemEmail.setChecked(true);
                etEmail.setText(s);
                s = pref.getString(Const.PASSWORD, "");
                if (s.length() > 0) {
                    cbRemPassword.setChecked(true);
                    etPassword.setText(uncriptPassword(s));
                }
            }
            loginList();
        } else {
            mode_list = state.getByte(Const.PANEL);
            if (mode_list > CabModel.LOGIN) {
                pMain.setVisibility(View.GONE);
                fabEnter.setVisibility(View.GONE);
                fabExit.setVisibility(View.VISIBLE);
            }
            String d;
            for (String t : state.getStringArray(Const.LINK)) {
                if (t.contains("#")) {
                    d = t.substring(t.indexOf("#") + 1);
                    t = t.substring(0, t.indexOf("#"));
                    adMain.addItem(new ListItem(t), d);
                } else
                    adMain.addItem(new ListItem(t));
            }
        }
        adMain.notifyDataSetChanged();
        softKeyboard.closeSoftKeyboard();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putByte(Const.PANEL, mode_list);
        String[] m = new String[adMain.getCount()];
        String d;
        for (int i = 0; i < adMain.getCount(); i++) {
            d = adMain.getItem(i).getDes();
            m[i] = adMain.getItem(i).getTitle() + (d == null ? "" : "#" + d);
        }
        outState.putStringArray(Const.LINK, m);
        super.onSaveInstanceState(outState);
    }

    private void initViews(View container) {
        act.setTitle(getString(R.string.cabinet));
        pMain = container.findViewById(R.id.pMain);
        fabEnter = container.findViewById(R.id.fabEnter);
        fabExit = container.findViewById(R.id.fabExit);
        etEmail = container.findViewById(R.id.etEmail);
        etPassword = container.findViewById(R.id.etPassword);
        cbRemEmail = container.findViewById(R.id.cbRemEmail);
        cbRemPassword = container.findViewById(R.id.cbRemPassword);
        InputMethodManager im = (InputMethodManager) act.getSystemService(Service.INPUT_METHOD_SERVICE);
        LinearLayout mainLayout = container.findViewById(R.id.content_cabmain);
        softKeyboard = new SoftKeyboard(mainLayout, im);
        ListView lvList = container.findViewById(R.id.lvList);
        adMain = new ListAdapter(act);
        lvList.setAdapter(adMain);
        lvList.setOnItemClickListener((adapterView, view, pos, l) -> {
            if (act.checkBusy()) return;
            if (mode_list == CabModel.LOGIN) {
                String s;
                switch (pos) {
                    case 0: //не открывает
                        s = "http://neosvet.ucoz.ru/vna/vpn.html";
                        Lib.openInApps(s, null);
                        return;
                    case 1: //восстановить доступ
                        s = "sendpass.html";
                        break;
                    case 2: //зарегистрироваться
                        s = "register.html";
                        break;
                    case 3: //о регистрации
                        s = "reginfo.html";
                        break;
                    case 4: //стастистика регистраций
                        s = "regstat.html";
                        break;
                    default: //(5) стастистика слов
                        s = "trans.html";
                        break;
                }
                CabpageActivity.openPage(s);
            } else if (mode_list == CabModel.CABINET) {
                switch (pos) {
                    case 0: //передача ощущений
                        if (adMain.getItem(pos).getDes().equals(
                                getString(R.string.select_status))) {
                            initLoad();
                            model.getListWord();
                        } else
                            Lib.showToast(getString(R.string.send_unlivable));
                        break;
                    case 1: //анкета
                        CabpageActivity.openPage("edinenie/anketa.html");
                        break;
                    case 2: //единомышленники
                        CabpageActivity.openPage("edinenie/edinomyshlenniki.html");
                        break;
                    default:
                        break;
                }
            } else if (mode_list == CabModel.WORDS) {
                initLoad();
                model.selectWord(pos);
            }
        });
    }

    private void initLoad() {
        act.status.setLoad(true);
        act.status.startText();
    }

    private void loginList() {
        for (int i = 0; i < getResources().getStringArray(R.array.cabinet_main).length; i += 2) {
            adMain.addItem(new ListItem(getResources().getStringArray(R.array.cabinet_main)[i]),
                    getResources().getStringArray(R.array.cabinet_main)[i + 1]);
        }
        adMain.notifyDataSetChanged();
    }

    private void setViews() {
        TextWatcher textWatcher = new TextWatcher() {
            public void afterTextChanged(Editable s) {
                boolean ready = false;
                if (etEmail.length() > 5 && etPassword.length() > 5) {
                    ready = (etEmail.getText().toString().contains("@")
                            && etEmail.getText().toString().contains("."));
                }
                if (ready) { //ready to login
                    fabEnter.setVisibility(View.VISIBLE);
                } else {
                    fabEnter.setVisibility(View.GONE);
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        };
        etEmail.addTextChangedListener(textWatcher);
        etPassword.addTextChangedListener(textWatcher);

        etPassword.setOnKeyListener((view, keyCode, keyEvent) -> {
            if ((keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)
                    || keyCode == EditorInfo.IME_ACTION_GO) {
                if (fabEnter.getVisibility() == View.VISIBLE)
                    subLogin();
                return true;
            }
            return false;
        });
        cbRemEmail.setOnCheckedChangeListener((compoundButton, check) -> {
            cbRemPassword.setEnabled(check);
            if (!check)
                cbRemPassword.setChecked(false);
        });
        fabEnter.setOnClickListener(view -> subLogin());
        fabExit.setOnClickListener(view -> {
            mode_list = CabModel.LOGIN;
            CabModel.cookie = null;
            CabModel.email = "";
            adMain.clear();
            loginList();
            fabEnter.setVisibility(View.VISIBLE);
            fabExit.setVisibility(View.GONE);
            pMain.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onDestroyView() {
        if (!cbRemPassword.isChecked()) {
            SharedPreferences pref = act.getSharedPreferences(this.getClass().getSimpleName(), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(Const.PASSWORD, "");
            if (!cbRemEmail.isChecked())
                editor.putString(Const.EMAIL, "");
            editor.apply();
        }
        super.onDestroyView();
    }

    private String criptPassword(String password) {
        char[] c = password.toCharArray();
        StringBuilder s = new StringBuilder();
        for (int a = 0; a < c.length; a++) {
            s.append((char) (Character.codePointAt(c, a) - a - 1));
        }
        return s.toString();
    }

    private String uncriptPassword(String password) {
        try {
            char[] c = password.toCharArray();
            StringBuilder s = new StringBuilder();
            for (int a = 0; a < c.length; a++) {
                s.append((char) (Character.codePointAt(c, a) + a + 1));
            }
            return s.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void subLogin() {
        softKeyboard.closeSoftKeyboard();
        if (ProgressHelper.isBusy())
            return;
        if (adMain.getCount() == 1) {
            adMain.clear();
            loginList();
        }
        if (cbRemEmail.isChecked()) {
            SharedPreferences pref = act.getSharedPreferences(this.getClass().getSimpleName(), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString(Const.EMAIL, etEmail.getText().toString());
            if (cbRemPassword.isChecked()) {
                editor.putString(Const.PASSWORD, criptPassword(etPassword.getText().toString()));
            }
            editor.apply();
        }
        initLoad();
        model.login(etEmail.getText().toString(), etPassword.getText().toString());
    }

    private void initWordList(@Nullable String[] words) {
        mode_list = CabModel.WORDS;
        adMain.clear();
        if (words != null)
            for (String word : words) {
                adMain.addItem(new ListItem(word));
            }
        adMain.notifyDataSetChanged();
    }

    private void initCabinet(String title, String des) {
        mode_list = CabModel.CABINET;
        adMain.clear();
        adMain.addItem(new ListItem(title));
        adMain.getItem(0).setDes(des);
        for (int i = 0; i < getResources().getStringArray(R.array.cabinet_enter).length; i++) {
            adMain.addItem(new ListItem(getResources().getStringArray(R.array.cabinet_enter)[i]));
        }
        adMain.notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NeoViewModel initViewModel() {
        return new SummaryModel(); //заглушка
    }

    @Override
    public void onChangedState(@NonNull NeoState state) {
    }

    @Override
    public void onViewCreated(Bundle savedInstanceState) {
    }
}
