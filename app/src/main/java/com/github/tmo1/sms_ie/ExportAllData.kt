/*
 * SMS Import / Export: a simple Android app for importing and exporting SMS messages from and to JSON files.
 * Copyright (c) 2021-2022 Thomas More
 *
 * This file is part of SMS Import / Export.
 *
 * SMS Import / Export is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMS Import / Export is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMS Import / Export.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.tmo1.sms_ie

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Base64
import android.util.JsonWriter
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

suspend fun exportAllData(
    appContext: Context,
    file: Uri,
    progressBar: ProgressBar?,
    statusReportText: TextView?,
    firstName: String,
    lastName: String,
    firstMobile: String,
    secondMobile: String,
    personalCode: String,
): Int {
    return withContext(Dispatchers.IO) {
        var total: Int = 0
        val displayNames = mutableMapOf<String, String?>()
        val displayNamesCallLog = mutableMapOf<String, String?>()
        appContext.contentResolver.openOutputStream(file).use { outPutStream ->
            BufferedWriter(OutputStreamWriter(outPutStream)).use { writer ->
                val jsonWriter = JsonWriter(writer)
                jsonWriter.setIndent("  ")

                jsonWriter.beginObject()

                jsonWriter.name("user_id").value(USER_ID)

                val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                jsonWriter.name("created_at").value(simpleDateFormat.format(Date()))

                //User info
                jsonWriter.name("user_info")
                jsonWriter.beginObject()
                jsonWriter.name("first_name").value(firstName)
                jsonWriter.name("last_name").value(lastName)
                jsonWriter.name("first_mobile").value(firstMobile)
                jsonWriter.name("second_mobile").value(secondMobile)
                jsonWriter.name("personal_code").value(personalCode)
                jsonWriter.endObject()

                //Phone info
                jsonWriter.name("device_info")
                jsonWriter.beginObject()
                jsonWriter.name("android_version").value(Build.VERSION.RELEASE)
                jsonWriter.name("manufacture").value(Build.MANUFACTURER)
                jsonWriter.name("model").value(Build.MODEL)
                jsonWriter.name("security_patch").value(Build.VERSION.SECURITY_PATCH)
                jsonWriter.endObject()

                //Contacts
                jsonWriter.name("contact")
                jsonWriter.beginArray()
                total = contactsToJSON2(appContext, jsonWriter, progressBar, statusReportText)
                jsonWriter.endArray()

                //SMS
                jsonWriter.name("sms")
                jsonWriter.beginArray()
                total =
                    smsToJSON(appContext, jsonWriter, displayNames, progressBar, statusReportText)
                jsonWriter.endArray()

                //Call Logs
                jsonWriter.name("call_log")
                jsonWriter.beginArray()
                total = callLogToJSON(
                    appContext,
                    jsonWriter,
                    displayNamesCallLog,
                    progressBar,
                    statusReportText
                )
                jsonWriter.endArray()

                jsonWriter.endObject()
            }
        }

        total
    }
}

private suspend fun contactsToJSON2(
    appContext: Context,
    jsonWriter: JsonWriter,
    progressBar: ProgressBar?,
    statusReportText: TextView?
): Int {
    var total = 0
    var totalContacts = 0
    val contacts = mutableListOf<JSONObject>()
    val contactsCursor = appContext.contentResolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        null, null, null, null
    )

    contactsCursor?.also {
        if (it.moveToFirst()) {
            totalContacts = it.count
            initProgressBar(progressBar, it)
            do {
                val jsonObject = JSONObject()

                val id = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val name =
                    it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))

                jsonObject.put("id", id)
                jsonObject.put("name", name)


                val rawCursor = appContext.contentResolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(id),
                    null
                )

                rawCursor?.also { mRawCursor ->
                    if (mRawCursor.moveToFirst()) {
                        val accountName =
                            mRawCursor.getString(mRawCursor.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_NAME))
                        jsonObject.put(
                            "account_type",
                            accountName
                        )
                    }
                }

                rawCursor?.close()


                val numberCursor = appContext.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(id),
                    null
                )

                numberCursor?.also { mNumberCursor ->
                    if (mNumberCursor.moveToFirst()) {
                        val numbers = mutableListOf<String>()
                        do {
                            val phoneNumber = mNumberCursor.getString(
                                mNumberCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            ).replace(" ", "")
                                .replace("-", "")
                                .replace("(", "")
                                .replace(")", "")
                                .trim()
                            if (
                                phoneNumber.isNotEmpty()
                                && !numbers.contains(phoneNumber)
                            )
                                numbers.add(phoneNumber)
                        } while (mNumberCursor.moveToNext())

                        if (numbers.size > 0) {
                            jsonObject.put("numbers", JSONArray(numbers))
                        }
                    }
                }

                numberCursor?.close()

                val emailCursor = appContext.contentResolver.query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(id),
                    null
                )

                emailCursor?.also { mEmailCursor ->
                    if (mEmailCursor.moveToFirst()) {
                        val emails = mutableListOf<String>()
                        do {
                            val email = mEmailCursor.getString(
                                mEmailCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.DATA)
                            ).trim()
                            if (email.isNotEmpty())
                                emails.add(email)

                        } while (mEmailCursor.moveToNext())

                        if (emails.size > 0)
                            jsonObject.put("emails", JSONArray(emails))
                    }
                }

                emailCursor?.close()

                if (jsonObject.has("numbers") || jsonObject.has("emails")) {
//                    contacts.add(jsonObject)

                    jsonWriter.beginObject()

                    jsonWriter.name("display_name").value(jsonObject.getString("name"))
                    jsonWriter.name("account_type").value(jsonObject.getString("account_type"))

                    if (jsonObject.has("numbers")) {
                        jsonWriter.name("numbers")
                        jsonWriter.beginArray()
                        for (i in 0 until jsonObject.getJSONArray("numbers").length()) {
                            jsonWriter.value(jsonObject.getJSONArray("numbers").getString(i))
                        }
                        jsonWriter.endArray()
                    }

                    if (jsonObject.has("emails")) {
                        jsonWriter.name("emails")
                        jsonWriter.beginArray()
                        for (i in 0 until jsonObject.getJSONArray("emails").length()) {
                            jsonWriter.value(jsonObject.getJSONArray("emails").getString(i))
                        }
                        jsonWriter.endArray()
                    }

                    jsonWriter.endObject()

                    total++
                    incrementProgress(progressBar)
                    setStatusText(
                        statusReportText,
                        appContext.getString(
                            R.string.contacts_export_progress,
                            total,
                            totalContacts
                        )
                    )
                }

            } while (it.moveToNext())
        }
    }

    hideProgressBar(progressBar)

    contactsCursor?.close()

//    total = 0
//    contacts.forEach { mContact ->
//        jsonWriter.beginObject()
//
//        jsonWriter.name("display_name").value(mContact.getString("name"))
//
//        if (mContact.has("numbers")) {
//            jsonWriter.name("numbers")
//            jsonWriter.beginArray()
//            for (i in 0 until mContact.getJSONArray("numbers").length()) {
//                jsonWriter.value(mContact.getJSONArray("numbers").getString(i))
//            }
//            jsonWriter.endArray()
//        }
//
//        if (mContact.has("emails")) {
//            jsonWriter.name("emails")
//            jsonWriter.beginArray()
//            for (i in 0 until mContact.getJSONArray("emails").length()) {
//                jsonWriter.value(mContact.getJSONArray("emails").getString(i))
//            }
//            jsonWriter.endArray()
//        }
//
//        jsonWriter.endObject()
//
//        total++
//        incrementProgress(progressBar)
//        setStatusText(
//            statusReportText,
//            appContext.getString(R.string.contacts_export_progress, total, totalContacts)
//        )
//    }
//    hideProgressBar(progressBar)

    return total
}


private suspend fun contactsToJSON(
    appContext: Context,
    jsonWriter: JsonWriter,
    progressBar: ProgressBar?,
    statusReportText: TextView?
): Int {
    val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    var total = 0
    //TODO
    val contactsCursor =
        appContext.contentResolver.query(
            //Uri.parse("content://call_log/calls"),
            ContactsContract.Contacts.CONTENT_URI,
            null,
            null,
            null,
            null
        )
    contactsCursor?.use { it ->
        if (it.moveToFirst()) {
            val totalContacts = it.count
            initProgressBar(progressBar, it)
            val contactsIdIndex = it.getColumnIndexOrThrow(BaseColumns._ID)
            do {
                jsonWriter.beginObject()
                it.columnNames.forEachIndexed { i, columnName ->
                    val value = it.getString(i)
                    if (value != null) jsonWriter.name(columnName).value(value)
                }
                val contactId = it.getString(contactsIdIndex)
                val rawContactsCursor = appContext.contentResolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    null,
                    ContactsContract.RawContacts.CONTACT_ID + "=?",
                    arrayOf(contactId),
                    null,
                    null
                )
                rawContactsCursor?.use { raw ->
                    if (raw.moveToFirst()) {
                        val rawContactsIdIndex = raw.getColumnIndexOrThrow(BaseColumns._ID)
                        jsonWriter.name("raw_contacts")
                        jsonWriter.beginArray()
                        do {
                            jsonWriter.beginObject()
                            raw.columnNames.forEachIndexed { i, columnName ->
                                val value = raw.getString(i)
                                if (value != null) jsonWriter.name(columnName).value(value)
                            }
                            val rawContactId = raw.getString(rawContactsIdIndex)
                            val dataCursor = appContext.contentResolver.query(
                                ContactsContract.Data.CONTENT_URI,
                                null,
                                ContactsContract.Data.RAW_CONTACT_ID + "=?",
                                arrayOf(rawContactId),
                                null,
                                null
                            )
                            dataCursor?.use { data ->
                                if (data.moveToFirst()) {
                                    jsonWriter.name("contacts_data")
                                    jsonWriter.beginArray()
                                    do {
                                        jsonWriter.beginObject()
                                        data.columnNames.forEachIndexed { i, columnName ->
                                            if (data.getType(i) != Cursor.FIELD_TYPE_BLOB) {
                                                val value = data.getString(i)
                                                if (value != null) jsonWriter.name(columnName)
                                                    .value(value)
                                            } else {
                                                val value = data.getBlob(i)
                                                if (value != null) jsonWriter.name("${columnName}__base64__")
                                                    .value(
                                                        Base64.encodeToString(
                                                            value,
                                                            Base64.NO_WRAP
                                                        )
                                                    )
                                            }
                                        }
                                        jsonWriter.endObject()
                                    } while (data.moveToNext())
                                }
                            }
                            jsonWriter.endArray()
                            jsonWriter.endObject()
                        } while (raw.moveToNext())
                        jsonWriter.endArray()
                    }
                }
                jsonWriter.endObject()
                total++
                incrementProgress(progressBar)
                setStatusText(
                    statusReportText,
                    appContext.getString(R.string.contacts_export_progress, total, totalContacts)
                )
                if (total == (prefs.getString("max_records", "")?.toIntOrNull() ?: -1)) break
            } while (it.moveToNext())
            hideProgressBar(progressBar)
        }
    }
    return total
}

private suspend fun smsToJSON(
    appContext: Context,
    jsonWriter: JsonWriter,
    displayNames: MutableMap<String, String?>,
    progressBar: ProgressBar?,
    statusReportText: TextView?
): Int {
    val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    var total = 0
    val smsCursor =
        appContext.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, null)
    smsCursor?.use { it ->
        if (it.moveToFirst()) {
            initProgressBar(progressBar, it)
            val totalSms = it.count
            val addressIndex = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            do {
                jsonWriter.beginObject()
                it.columnNames.forEachIndexed { i, columnName ->
                    val value = it.getString(i)
                    if (value != null) jsonWriter.name(columnName).value(value)
                }
                val address = it.getString(addressIndex)
                if (address != null) {
                    val displayName =
                        lookupDisplayName(appContext, displayNames, address)
                    if (displayName != null) jsonWriter.name("display_name").value(displayName)
                }
                jsonWriter.endObject()
                total++
                incrementProgress(progressBar)
                setStatusText(
                    statusReportText,
                    appContext.getString(R.string.sms_export_progress, total, totalSms)
                )
                if (total == (prefs.getString("max_records", "")?.toIntOrNull() ?: -1)) break
            } while (it.moveToNext())
            hideProgressBar(progressBar)
        }
    }
    return total
}

private suspend fun mmsToJSON(
    appContext: Context,
    jsonWriter: JsonWriter,
    displayNames: MutableMap<String, String?>,
    progressBar: ProgressBar?,
    statusReportText: TextView?
): Int {
    val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    var total = 0
    val mmsCursor =
        appContext.contentResolver.query(Telephony.Mms.CONTENT_URI, null, null, null, null)
    mmsCursor?.use { it ->
        if (it.moveToFirst()) {
            val totalMms = it.count
            initProgressBar(progressBar, it)
            val msgIdIndex = it.getColumnIndexOrThrow("_id")
            // write MMS metadata
            do {
                jsonWriter.beginObject()
                it.columnNames.forEachIndexed { i, columnName ->
                    val value = it.getString(i)
                    if (value != null) jsonWriter.name(columnName).value(value)
                }
//                        the following is adapted from https://stackoverflow.com/questions/3012287/how-to-read-mms-data-in-android/6446831#6446831
                val msgId = it.getString(msgIdIndex)
                val addressCursor = appContext.contentResolver.query(
//                                Uri.parse("content://mms/addr"),
                    Uri.parse("content://mms/$msgId/addr"),
                    null,
                    null,
                    null,
                    null
                )
                addressCursor?.use { it1 ->
                    val addressTypeIndex =
                        addressCursor.getColumnIndexOrThrow(Telephony.Mms.Addr.TYPE)
                    val addressIndex =
                        addressCursor.getColumnIndexOrThrow(Telephony.Mms.Addr.ADDRESS)
                    // write sender address object
                    if (it1.moveToFirst()) {
                        do {
                            if (addressTypeIndex.let { it2 -> it1.getString(it2) } == PDU_HEADERS_FROM) {
                                jsonWriter.name("sender_address")
                                jsonWriter.beginObject()
                                it1.columnNames.forEachIndexed { i, columnName ->
                                    val value = it1.getString(i)
                                    if (value != null) jsonWriter.name(columnName).value(value)
                                }
                                val displayName =
                                    lookupDisplayName(
                                        appContext,
                                        displayNames,
                                        it1.getString(addressIndex)
                                    )
                                if (displayName != null) jsonWriter.name("display_name")
                                    .value(displayName)
                                jsonWriter.endObject()
                                break
                            }
                        } while (it1.moveToNext())
                    }
                    // write array of recipient address objects
                    if (it1.moveToFirst()) {
                        jsonWriter.name("recipient_addresses")
                        jsonWriter.beginArray()
                        do {
                            if (addressTypeIndex.let { it2 -> it1.getString(it2) } != PDU_HEADERS_FROM) {
                                jsonWriter.beginObject()
                                it1.columnNames.forEachIndexed { i, columnName ->
                                    val value = it1.getString(i)
                                    if (value != null) jsonWriter.name(columnName).value(value)
                                }
                                val displayName =
                                    lookupDisplayName(
                                        appContext,
                                        displayNames,
                                        it1.getString(addressIndex)
                                    )
                                if (displayName != null) jsonWriter.name("display_name")
                                    .value(displayName)
                                jsonWriter.endObject()
                            }
                        } while (it1.moveToNext())
                        jsonWriter.endArray()
                    }
                }
                val partCursor = appContext.contentResolver.query(
                    Uri.parse("content://mms/part"),
//                      Uri.parse("content://mms/$msgId/part"),
                    null,
                    "mid=?",
                    arrayOf(msgId),
                    "seq ASC"
                )
                // write array of MMS parts
                partCursor?.use { it1 ->
                    if (it1.moveToFirst()) {
                        jsonWriter.name("parts")
                        jsonWriter.beginArray()
                        val partIdIndex = it1.getColumnIndexOrThrow("_id")
                        val dataIndex = it1.getColumnIndexOrThrow("_data")
                        do {
                            jsonWriter.beginObject()
                            it1.columnNames.forEachIndexed { i, columnName ->
                                val value = it1.getString(i)
                                if (value != null) jsonWriter.name(columnName).value(value)
                            }
                            if (prefs.getBoolean("include_binary_data", true) && it1.getString(
                                    dataIndex
                                ) != null
                            ) {
                                try {
                                    val inputStream = appContext.contentResolver.openInputStream(
                                        Uri.parse(
                                            "content://mms/part/" + it1.getString(
                                                partIdIndex
                                            )
                                        )
                                    )
                                    val data = inputStream.use {
                                        Base64.encodeToString(
                                            it?.readBytes(),
                                            Base64.NO_WRAP // Without NO_WRAP, we end up with corrupted files upon decoding - see https://stackoverflow.com/questions/16091883/sending-base64-encoded-image-results-in-a-corrupt-image
                                        )
                                    }
                                    jsonWriter.name("binary_data").value(data)
                                } catch (e: Exception) {
                                    Log.e(
                                        LOG_TAG,
                                        "Error accessing binary data for MMS message part " + it1.getString(
                                            partIdIndex
                                        ) + ": $e"
                                    )
                                }
                            }
                            jsonWriter.endObject()
                        } while (it1.moveToNext())
                        jsonWriter.endArray()
                    }
                }
                jsonWriter.endObject()
                total++
                incrementProgress(progressBar)
                setStatusText(
                    statusReportText,
                    appContext.getString(R.string.mms_export_progress, total, totalMms)
                )
                if (total == (prefs.getString("max_records", "")?.toIntOrNull() ?: -1)) break
            } while (it.moveToNext())
            hideProgressBar(progressBar)
        }
    }
    return total
}

@SuppressLint("StringFormatMatches")
private suspend fun callLogToJSON(
    appContext: Context,
    jsonWriter: JsonWriter,
    displayNames: MutableMap<String, String?>,
    progressBar: ProgressBar?,
    statusReportText: TextView?
): Int {
    val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    var total = 0
    val callCursor =
        appContext.contentResolver.query(
            Uri.parse("content://call_log/calls"),
            null,
            null,
            null,
            null
        )
    callCursor?.use { it ->
        if (it.moveToFirst()) {
            val totalCalls = it.count
            initProgressBar(progressBar, it)
            val addressIndex = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            do {
                jsonWriter.beginObject()
                it.columnNames.forEachIndexed { i, columnName ->
                    val value = it.getString(i)
                    if (value != null) jsonWriter.name(columnName).value(value)
                }
                // The call logs do have a CACHED_NAME ("name") field, but it may still be useful to add the current display name, if available
                // From the documentation at https://developer.android.com/reference/android/provider/CallLog.Calls#CACHED_NAME
                // "The cached name associated with the phone number, if it exists.
                // This value is typically filled in by the dialer app for the caching purpose, so it's not guaranteed to be present, and may not be current if the contact information associated with this number has changed."
                val address = it.getString(addressIndex)
                if (address != null) {
                    val displayName =
                        lookupDisplayName(appContext, displayNames, address)
                    if (displayName != null) jsonWriter.name("display_name").value(displayName)
                }
                jsonWriter.endObject()
                total++
                incrementProgress(progressBar)
                setStatusText(
                    statusReportText,
                    appContext.getString(R.string.call_log_export_progress, total, totalCalls)
                )
                if (total == (prefs.getString("max_records", "")?.toIntOrNull() ?: -1)) break
            } while (it.moveToNext())
            hideProgressBar(progressBar)
        }
    }
    return total
}