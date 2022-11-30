package com.github.tmo1.sms_ie.base

/**
 * Created by Anonymous on 9/29/2022 AD.
 */
interface ResponseObject<out DomainObject : Any?> {
    fun toDomain(): DomainObject
}

