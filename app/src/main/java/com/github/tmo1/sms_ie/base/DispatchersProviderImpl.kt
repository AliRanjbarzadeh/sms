package com.github.tmo1.sms_ie.base

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by Anonymous on 9/29/2022 AD.
 */
@Singleton
class DispatchersProviderImpl @Inject constructor() {
	val dispatcher = object : DispatchersProvider {
		override fun getIO(): CoroutineDispatcher {
			return Dispatchers.IO
		}

		override fun getMain(): CoroutineDispatcher {
			return Dispatchers.Main
		}

		override fun getDefault(): CoroutineDispatcher {
			return Dispatchers.Default
		}
	}

}