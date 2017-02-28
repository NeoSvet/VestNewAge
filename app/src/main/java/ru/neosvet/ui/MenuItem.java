package ru.neosvet.ui;

public class MenuItem {
    private String title;
    private int image;
    private boolean boolSelect = false;

    public boolean isSelect() {
        return boolSelect;
    }

    public void setSelect(boolean boolSelect) {
        this.boolSelect = boolSelect;
    }

    public int getImage() {
        return image;
    }

    public String getTitle() {
        return title;
    }

    public MenuItem(int image, String title) {
        this.image = image;
        this.title = title;

    }
}
