package com.github.tmo1.sms_ie.base

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Created by Anonymous on 9/29/2022 AD.
 */
interface DispatchersProvider {
	fun getIO(): CoroutineDispatcher
	fun getMain(): CoroutineDispatcher
	fun getDefault(): CoroutineDispatcher
}