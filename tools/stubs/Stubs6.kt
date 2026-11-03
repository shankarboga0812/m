package androidx.lifecycle
abstract class ViewModel { protected open fun onCleared() {} }
interface ViewModelProvider { interface Factory { fun <T : ViewModel> create(modelClass: Class<T>): T } }
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
val ViewModel.viewModelScope: kotlinx.coroutines.CoroutineScope get() = kotlinx.coroutines.GlobalScope
