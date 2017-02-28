package ru.neosvet.ui;

public class CheckItem {
    private String title;
    private int id = 0;
    private boolean check = false;

    public CheckItem(int id, String title) {
        this.id = id;
        this.title = title;
    }

    public CheckItem(String title) {
        this.title = title;
    }

    public boolean isCheck() {
        return check;
    }

    public void setCheck(boolean check) {
        this.check = check;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }
}
