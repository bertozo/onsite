package com.rodolfobertozo.onsite

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rodolfobertozo.onsite.data.AppDatabase
import com.rodolfobertozo.onsite.data.Site
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SiteFormUiState(
    val label: String = "",
    val addressQuery: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class SiteEditUiState(
    val original: Site,
    val label: String,
    val addressQuery: String,
    val latitude: Double?,
    val longitude: Double?
)

class SiteViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).siteDao()

    private val _uiState = MutableStateFlow(SiteFormUiState())
    val uiState: StateFlow<SiteFormUiState> = _uiState

    private val addressQueryFlow = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val suggestions: StateFlow<List<AddressSuggestion>> = addressQueryFlow
        .debounce(400)
        .distinctUntilChanged()
        .mapLatest { query ->
            if (query.length < 3) emptyList() else runCatching { searchAddresses(query) }.getOrDefault(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sites: StateFlow<List<Site>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editState = MutableStateFlow<SiteEditUiState?>(null)
    val editState: StateFlow<SiteEditUiState?> = _editState

    private val editAddressQueryFlow = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val editSuggestions: StateFlow<List<AddressSuggestion>> = editAddressQueryFlow
        .debounce(400)
        .distinctUntilChanged()
        .mapLatest { query ->
            if (query.length < 3) emptyList() else runCatching { searchAddresses(query) }.getOrDefault(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onLabelChanged(value: String) {
        _uiState.update { it.copy(label = value) }
    }

    fun onAddressQueryChanged(value: String) {
        _uiState.update { it.copy(addressQuery = value, latitude = null, longitude = null) }
        addressQueryFlow.value = value
    }

    fun selectAddress(suggestion: AddressSuggestion) {
        _uiState.update {
            it.copy(
                addressQuery = suggestion.label,
                latitude = suggestion.latitude,
                longitude = suggestion.longitude
            )
        }
        addressQueryFlow.value = ""
    }

    fun saveSite() {
        val state = _uiState.value
        val lat = state.latitude
        val lon = state.longitude
        if (lat == null || lon == null || state.label.isBlank()) return

        viewModelScope.launch {
            dao.insert(
                Site(
                    label = state.label.trim(),
                    address = state.addressQuery,
                    latitude = lat,
                    longitude = lon,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
            _uiState.update { SiteFormUiState() }
        }
    }

    fun deleteSite(site: Site) {
        viewModelScope.launch { dao.delete(site) }
    }

    fun startEditingSite(site: Site) {
        _editState.value = SiteEditUiState(
            original = site,
            label = site.label,
            addressQuery = site.address.orEmpty(),
            latitude = site.latitude,
            longitude = site.longitude
        )
    }

    fun onEditLabelChanged(value: String) {
        _editState.update { it?.copy(label = value) }
    }

    fun onEditAddressQueryChanged(value: String) {
        _editState.update { it?.copy(addressQuery = value, latitude = null, longitude = null) }
        editAddressQueryFlow.value = value
    }

    fun selectEditAddress(suggestion: AddressSuggestion) {
        _editState.update {
            it?.copy(
                addressQuery = suggestion.label,
                latitude = suggestion.latitude,
                longitude = suggestion.longitude
            )
        }
        editAddressQueryFlow.value = ""
    }

    fun cancelEditingSite() {
        _editState.value = null
        editAddressQueryFlow.value = ""
    }

    fun saveEditedSite() {
        val state = _editState.value ?: return
        val lat = state.latitude
        val lon = state.longitude
        if (lat == null || lon == null || state.label.isBlank()) return

        viewModelScope.launch {
            dao.update(
                state.original.copy(
                    label = state.label.trim(),
                    address = state.addressQuery,
                    latitude = lat,
                    longitude = lon
                )
            )
            _editState.value = null
            editAddressQueryFlow.value = ""
        }
    }
}
