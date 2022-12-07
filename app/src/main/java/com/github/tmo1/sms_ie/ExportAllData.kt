package com.github.tmo1.sms_ie

import android.accounts.Account
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.JsonWriter
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RawRes
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
    firstName: String,
    lastName: String,
    firstMobile: String,
    secondMobile: String,
    personalCode: String,
    description: String,
    imeis: MutableList<String>,
    emails: List<Account>,
    progressCallback: ProgressInterface,
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
                jsonWriter.name("description").value(description)

                //emails
                jsonWriter.name("emails")
                jsonWriter.beginArray()
                emails.forEach { jsonWriter.value(it.name) }
                jsonWriter.endArray()
                jsonWriter.endObject()

                //Phone info
                jsonWriter.name("device_info")
                jsonWriter.beginObject()
                jsonWriter.name("android_version").value(Build.VERSION.RELEASE)
                jsonWriter.name("manufacture").value(Build.MANUFACTURER)
                jsonWriter.name("model").value(Build.MODEL)
                jsonWriter.name("security_patch").value(Build.VERSION.SECURITY_PATCH)
                jsonWriter.name("imei")
                jsonWriter.beginArray()
                imeis.forEach { jsonWriter.value(it) }
                jsonWriter.endArray()
                jsonWriter.endObject()

                //Contacts
                changeProgressType(
                    progressCallback,
                    R.raw.mail,
                    appContext.getString(R.string.export_contacts)
                )
                jsonWriter.name("contact")
                jsonWriter.beginArray()
                total = contactsToJSON2(appContext, jsonWriter, progressCallback)
                jsonWriter.endArray()

                //SMS
                changeProgressType(
                    progressCallback,
                    R.raw.sms,
                    appContext.getString(R.string.export_sms)
                )
                jsonWriter.name("sms")
                jsonWriter.beginArray()
                total =
                    smsToJSON(appContext, jsonWriter, displayNames, progressCallback)
                jsonWriter.endArray()

                //Call Logs
                changeProgressType(
                    progressCallback,
                    R.raw.call,
                    appContext.getString(R.string.export_call_log)
                )
                jsonWriter.name("call_log")
                jsonWriter.beginArray()
                total = callLogToJSON(
                    appContext,
                    jsonWriter,
                    displayNamesCallLog,
                    progressCallback
                )
                jsonWriter.endArray()

                jsonWriter.endObject()
            }
        }

        total
    }
}

private suspend fun changeProgressType(
    progressCallback: ProgressInterface,
    @RawRes icon: Int,
    title: String
) {
    withContext(Dispatchers.Main) {
        progressCallback.onChangeType(icon, title)
    }
}

private suspend fun updateProgress(
    progressCallback: ProgressInterface,
    currentProgress: Int,
    description: String
) {
    withContext(Dispatchers.Main) {
        progressCallback.onProgress(currentProgress, description)
    }
}

private suspend fun contactsToJSON2(
    appContext: Context,
    jsonWriter: JsonWriter,
    progressCallback: ProgressInterface
): Int {
    var total = 0
    var totalContacts = 0
    val contactsCursor = appContext.contentResolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        null, null, null, null
    )

    contactsCursor?.also {
        if (it.moveToFirst()) {
            totalContacts = it.count
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
                    jsonWriter.beginObject()

                    jsonWriter.name("display_name").value(jsonObject.getString("name"))
                    jsonWriter.name("account_type")
                    try {
                        jsonWriter.value(jsonObject.getString("account_type"))
                    } catch (_: Exception) {
                        jsonWriter.value("phone")
                    }

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
                    updateProgress(
                        progressCallback, total,
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

    contactsCursor?.close()
    return total
}


private suspend fun smsToJSON(
    appContext: Context,
    jsonWriter: JsonWriter,
    displayNames: MutableMap<String, String?>,
    progressCallback: ProgressInterface
): Int {
    val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    var total = 0
    val smsCursor =
        appContext.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, null)
    smsCursor?.use { it ->
        if (it.moveToFirst()) {
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
                updateProgress(
                    progressCallback,
                    total,
                    appContext.getString(R.string.sms_export_progress, total, totalSms)
                )
                if (total == (prefs.getString("max_records", "")?.toIntOrNull() ?: -1)) break
            } while (it.moveToNext())
        }
    }
    return total
}

@SuppressLint("StringFormatMatches")
private suspend fun callLogToJSON(
    appContext: Context,
    jsonWriter: JsonWriter,
    displayNames: MutableMap<String, String?>,
    progressCallback: ProgressInterface
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
                updateProgress(
                    progressCallback,
                    total,
                    appContext.getString(R.string.call_log_export_progress, total, totalCalls)
                )
                if (total == (prefs.getString("max_records", "")?.toIntOrNull() ?: -1)) break
            } while (it.moveToNext())
        }
    }
    return total
}