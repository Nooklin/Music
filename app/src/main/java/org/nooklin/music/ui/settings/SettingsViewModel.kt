package org.nooklin.music.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nooklin.music.data.local.AppFont
import org.nooklin.music.data.local.AppPreferences
import org.nooklin.music.data.local.AppThemeMode
import org.nooklin.music.data.local.ContrastColor
import org.nooklin.music.data.local.HomeSectionSetting
import org.nooklin.music.data.local.LibrarySectionSetting

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application)

    val themeMode: StateFlow<AppThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppThemeMode.SYSTEM)

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    val contrastColor: StateFlow<ContrastColor> = preferences.contrastColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContrastColor.DEFAULT)

    fun setContrastColor(color: ContrastColor) {
        viewModelScope.launch { preferences.setContrastColor(color) }
    }

    val colorful: StateFlow<Boolean> = preferences.colorful
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setColorful(value: Boolean) {
        viewModelScope.launch { preferences.setColorful(value) }
    }

    // null until the setting is read, so the tour does not flash on the screen
    val tourDone: StateFlow<Boolean?> = preferences.tourDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setTourDone(value: Boolean) {
        viewModelScope.launch { preferences.setTourDone(value) }
    }

    val librarySections: StateFlow<List<LibrarySectionSetting>?> = preferences.librarySections
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setLibrarySections(sections: List<LibrarySectionSetting>) {
        viewModelScope.launch { preferences.setLibrarySections(sections) }
    }

    val homeSections: StateFlow<List<HomeSectionSetting>?> = preferences.homeSections
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setHomeSections(sections: List<HomeSectionSetting>) {
        viewModelScope.launch { preferences.setHomeSections(sections) }
    }

    val appFont: StateFlow<AppFont> = preferences.appFont
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppFont.INTER)

    fun setAppFont(font: AppFont) {
        viewModelScope.launch { preferences.setAppFont(font) }
    }
}
