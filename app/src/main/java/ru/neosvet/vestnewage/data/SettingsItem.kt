package ru.neosvet.vestnewage.data

import android.widget.TextView

sealed class SettingsItem {
    data class CheckList(
        val title: String,
        val isSingleSelect: Boolean,
        val list: List<CheckItem>,
        val onChecked: (Int, Boolean) -> Unit //index, checked
    ) : SettingsItem()

    data class CheckListButton(
        val title: String,
        val list: List<CheckItem>,
        val buttonLabel: String,
        val onClick: (List<Int>) -> Unit //list indexes of checked
    ) : SettingsItem()

    data class Notification(
        val title: String,
        val offLabel: String,
        val onLabel: String,
        val valueSeek: Int,
        val maxSeek: Int,
        val changeValue: (TextView, Int) -> Unit, //label, value
        val stopTracking: (Int) -> Unit, //value
        val onClick: () -> Unit //set_notif
    ) : SettingsItem()

    data class Message(
        val title: String,
        val text: String,
        val buttonLabel: String,
        val onClick: () -> Unit
    ) : SettingsItem()
}