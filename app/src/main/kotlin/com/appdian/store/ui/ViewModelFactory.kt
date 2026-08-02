package com.appdian.store.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.appdian.store.AppDianApp
import com.appdian.store.vm.AppViewModelFactory

/** 从 Application 容器构造 VM 工厂 */
@Composable
fun viewModelFactory(): AppViewModelFactory {
    val context = LocalContext.current
    return AppViewModelFactory(context.applicationContext as AppDianApp)
}
