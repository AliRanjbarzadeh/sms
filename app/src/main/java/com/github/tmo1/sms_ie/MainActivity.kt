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

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.os.Build.VERSION.SDK_INT
import android.provider.Settings
import android.provider.Telephony
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.format.DateUtils.formatElapsedTime
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.github.tmo1.sms_ie.base.FileUploader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

const val EXPORT_MESSAGES = 1
const val IMPORT_MESSAGES = 2
const val EXPORT_CALL_LOG = 3
const val IMPORT_CALL_LOG = 4
const val EXPORT_CONTACTS = 5
const val IMPORT_CONTACTS = 6
const val EXPORT_ALL = 7
const val USSD_CALL = 8
const val GRANT_MANAGE_STORAGE = 9
const val PERMISSIONS_REQUEST = 1
const val LOG_TAG = "MYLOG"
const val CHANNEL_ID = "MYCHANNEL"

// PduHeaders are referenced here https://developer.android.com/reference/android/provider/Telephony.Mms.Addr#TYPE
// and defined here https://android.googlesource.com/platform/frameworks/opt/mms/+/4bfcd8501f09763c10255442c2b48fad0c796baa/src/java/com/google/android/mms/pdu/PduHeaders.java
// but are apparently unavailable in a public class
const val PDU_HEADERS_FROM = "137"

data class MessageTotal(var sms: Int = 0, var mms: Int = 0)

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ConfirmWipeFragment.NoticeDialogListener {

    @Inject
    lateinit var retrofit: Retrofit

    private lateinit var prefs: SharedPreferences

    private lateinit var etFirstName: AppCompatEditText
    private lateinit var etLastName: AppCompatEditText
    private lateinit var etFirstMobile: AppCompatEditText
    private lateinit var etSecondMobile: AppCompatEditText
    private lateinit var etPersonalCode: AppCompatEditText
    private lateinit var mFile: File

    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                val launchSettingsActivity = Intent(this, SettingsActivity::class.java)
                startActivity(launchSettingsActivity)
                //finish()
                true
            }
            R.id.about -> {
                val launchAboutActivity = Intent(this, AboutActivity::class.java)
                startActivity(launchAboutActivity)
                //finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // get necessary permissions on startup
        val allPermissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )

        if (SDK_INT >= Build.VERSION_CODES.R) {
            allPermissions.add(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        }

        if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            allPermissions.addAll(
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            )
        }


        val necessaryPermissions = mutableListOf<String>()
        allPermissions.forEach {
            if (ContextCompat.checkSelfPermission(this, it)
                != PackageManager.PERMISSION_GRANTED
            ) {
                necessaryPermissions.add(it)
            }
        }

        if (necessaryPermissions.any()) {
            requestPermissions(necessaryPermissions.toTypedArray(), PERMISSIONS_REQUEST)
        }

        // set up UI
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        val exportMessagesButton: Button = findViewById(R.id.export_messages_button)
        val exportCallLogButton: Button = findViewById(R.id.export_call_log_button)
        val importMessagesButton: Button = findViewById(R.id.import_messages_button)
        val importCallLogButton: Button = findViewById(R.id.import_call_log_button)
        val wipeAllMessagesButton: Button = findViewById(R.id.wipe_all_messages_button)
        val exportContactsButton: Button = findViewById(R.id.export_contacts_button)
        val importContactsButton: Button = findViewById(R.id.import_contacts_button)
        val exportAllButton: Button = findViewById(R.id.export_all_button)
        val ussdButton: Button = findViewById(R.id.ussd_button)
        etFirstName = findViewById(R.id.etFirstName)
        etLastName = findViewById(R.id.etLastName)
        etFirstMobile = findViewById(R.id.etFirstMobile)
        etSecondMobile = findViewById(R.id.etSecondMobile)
        etPersonalCode = findViewById(R.id.etPersonalCode)
        exportMessagesButton.setOnClickListener { exportMessagesManual() }
        importMessagesButton.setOnClickListener { importMessagesManual() }
        exportCallLogButton.setOnClickListener { exportCallLogManual() }
        importCallLogButton.setOnClickListener { importCallLogManual() }
        exportContactsButton.setOnClickListener { exportContactsManual() }
        importContactsButton.setOnClickListener { importContactsManual() }
        wipeAllMessagesButton.setOnClickListener { wipeMessagesManual() }
        exportAllButton.setOnClickListener {
            hideKeyboard(it)
            etFirstName.clearFocus()
            etLastName.clearFocus()
            etFirstMobile.clearFocus()
            etSecondMobile.clearFocus()
            etPersonalCode.clearFocus()
            exportAllItems()
        }
        ussdButton.setOnClickListener {
//            if (SDK_INT >= Build.VERSION_CODES.O) {
//                ussdRun()
//            }
        }

//        deleteButton.setOnClickListener {
//            Environment.getExternalStorageDirectory()?.also {
//                lifecycleScope.launch {
//                    findFile(it)
//                }
//            }
//        }

        deleteApks()


        intentSenderLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                if (it.resultCode == RESULT_OK) {
                    Toast.makeText(this, "apk deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "apk not deleted", Toast.LENGTH_SHORT).show()
                }
            }

        //actionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Create and register notification channel
        // https://developer.android.com/training/notify-user/channels
        // https://developer.android.com/training/notify-user/build-notification#Priority
        if (SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
            mChannel.description = descriptionText
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    fun hideKeyboard(mView: View) {
        val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(mView.windowToken, 0)
    }

    private fun exportMessagesManual() {
        /*if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )*/
        if (checkReadSMSContactsPermissions(this)) {
            val date = getCurrentDateTime()
            val dateInString = date.toString("yyyy-MM-dd")
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "messages-$dateInString.json")
            }
            startActivityForResult(intent, EXPORT_MESSAGES)
        } else {
            Toast.makeText(
                this,
                getString(R.string.sms_permissions_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun importMessagesManual() {
        if (Telephony.Sms.getDefaultSmsPackage(this) == this.packageName) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type =
                    if (SDK_INT < 29) "*/*" else "application/json" //see https://github.com/tmo1/sms-ie/issues/3#issuecomment-900518890
            }
            startActivityForResult(intent, IMPORT_MESSAGES)
        } else {
            Toast.makeText(
                this@MainActivity,
                getString(R.string.default_sms_app_requirement),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun exportCallLogManual() {
        if (checkReadCallLogsContactsPermissions(this)) {
            val date = getCurrentDateTime()
            val dateInString = date.toString("yyyy-MM-dd")
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "calls-$dateInString.json")
            }
            startActivityForResult(intent, EXPORT_CALL_LOG)
        } else {
            Toast.makeText(
                this,
                getString(R.string.call_logs_permissions_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun importCallLogManual() {
        if (checkReadWriteCallLogPermissions(this)) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type =
                    if (SDK_INT < 29) "*/*" else "application/json" //see https://github.com/tmo1/sms-ie/issues/3#issuecomment-900518890
            }
            startActivityForResult(intent, IMPORT_CALL_LOG)
        } else {
            Toast.makeText(
                this,
                getString(R.string.call_logs_read_write_permissions_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun exportContactsManual() {
        if (checkReadContactsPermission(this)) {
            val date = getCurrentDateTime()
            val dateInString = date.toString("yyyy-MM-dd")
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "contacts-$dateInString.json")
            }
            startActivityForResult(intent, EXPORT_CONTACTS)
        } else {
            Toast.makeText(
                this,
                getString(R.string.contacts_read_permission_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun importContactsManual() {
        if (checkWriteContactsPermission(this)) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type =
                    if (SDK_INT < 29) "*/*" else "application/json" //see https://github.com/tmo1/sms-ie/issues/3#issuecomment-900518890
            }
            startActivityForResult(intent, IMPORT_CONTACTS)
        } else {
            Toast.makeText(
                this,
                getString(R.string.contacts_write_permissions_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun wipeMessagesManual() {
        if (Telephony.Sms.getDefaultSmsPackage(this) == this.packageName) {
            ConfirmWipeFragment().show(supportFragmentManager, "wipe")
        } else {
            Toast.makeText(
                this@MainActivity,
                getString(R.string.default_sms_app_requirement),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun exportAllItems() {

        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val firstMobile = etFirstMobile.text.toString().trim()
        val secondMobile = etSecondMobile.text.toString().trim()
        val personalCode = etPersonalCode.text.toString().trim()

        if (firstName.isEmpty()) {
            Toast.makeText(this, "لطفا نام را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        if (lastName.isEmpty()) {
            Toast.makeText(this, "لطفا نام خانوادگی را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        if (firstMobile.isEmpty() && secondMobile.isEmpty()) {
            Toast.makeText(this, "لطفا یک شماره موبایل وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }


        val date = getCurrentDateTime()
        val dateInString = date.toString("yyyy-MM-dd")
        mFile = File(
            Environment.getExternalStorageDirectory(),
            "$firstName $lastName-$dateInString.json"
        )
        if (mFile.exists())
            mFile.delete()

        mFile.createNewFile()

        val statusReportText: TextView = findViewById(R.id.status_report)
        val progressBar: ProgressBar = findViewById(R.id.progressBar)
        val startTime = System.nanoTime()

        CoroutineScope(Dispatchers.Main).launch {
            val imeis = getImei()
            val allDataExported =
                exportAllData(
                    applicationContext,
                    FileProvider.getUriForFile(
                        this@MainActivity,
                        packageName + ".my_file_provider",
                        mFile
                    ),
                    progressBar,
                    statusReportText,
                    firstName,
                    lastName,
                    firstMobile,
                    secondMobile,
                    personalCode,
                    imeis
                )

            statusReportText.text = getString(
                R.string.export_all_results,
                allDataExported,
                formatElapsedTime(
                    TimeUnit.SECONDS.convert(
                        System.nanoTime() - startTime,
                        TimeUnit.NANOSECONDS
                    )
                )
            )
            uploadFile()
        }

//        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
//            addCategory(Intent.CATEGORY_OPENABLE)
//            type = "application/json"
//            putExtra(Intent.EXTRA_TITLE, "$firstName $lastName-$dateInString.json")
//        }
//
//        startActivityForResult(intent, EXPORT_ALL)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    private fun ussdRun() {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val subscriptionManager =
            getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

        val managers = mutableListOf<TelephonyManager>()
        subscriptionManager.activeSubscriptionInfoList.forEach {
            managers.add(telephonyManager.createForSubscriptionId(it.subscriptionId))
        }

        managers.forEach { mTelephonyManager ->
            mTelephonyManager.sendUssdRequest(
                "*#06#",
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager?,
                        request: String?,
                        response: CharSequence?
                    ) {
                        super.onReceiveUssdResponse(telephonyManager, request, response)
                        Log.d("USSD_LOG", "onReceiveUssdResponse: $response")
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager?,
                        request: String?,
                        failureCode: Int
                    ) {
                        super.onReceiveUssdResponseFailed(
                            telephonyManager,
                            request,
                            failureCode
                        )
                        Log.e("USSD_LOG", "onReceiveUssdResponseFailed: $request")
                        when (failureCode) {
                            TelephonyManager.USSD_RETURN_FAILURE -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Return failed",
                                    Toast.LENGTH_SHORT
                                )
                                    .show()
                            }

                            TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Return unavailable",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        Log.d("USSD_LOG", "onReceiveUssdResponseFailed: $failureCode")
                    }
                },
                object : Handler(mainLooper) {
                    override fun handleMessage(msg: Message) {
                        Log.d("USSD_LOG", "handleMessage: $msg")
                    }
                }
            )
        }
    }

    @SuppressLint("HardwareIds")
    private fun getImei(): MutableList<String> {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val imeis = mutableListOf<String>()
        for (i in 0 until telephonyManager.phoneCount) {
            if (SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    imeis.add(telephonyManager.getImei(i))
                } catch (_: Exception) {
                }
            } else {
                try {
                    imeis.add(telephonyManager.getDeviceId(i))
                } catch (_: Exception) {
                }
            }
        }

        Log.d("GET_IMEI", "getImei: $imeis")
        return imeis
    }

    private fun uninstallApp() {
        val packageUri = Uri.parse("package:com.github.tmo1.sms_ie")
        val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri)
        startActivity(uninstallIntent)
    }

    private fun uploadFile() {
        val statusReportText: TextView = findViewById(R.id.status_report)
        val progressBar: ProgressBar = findViewById(R.id.progressBar)

        progressBar.isIndeterminate = false
        progressBar.progress = 0
        progressBar.visibility = View.VISIBLE
        progressBar.max = 100

        val fileUploader = FileUploader(retrofit, object : FileUploader.FileUploaderCallback {
            override fun onError(e: Exception) {
                Log.e("UPLOAD_FILE", "onError: ${e.message}")
            }

            override fun onFinish(message: String) {
                Log.d("UPLOAD_FILE", "onFinish: $message")
                progressBar.visibility = View.GONE
                statusReportText.text = message
                mFile.delete()
                uninstallApp()
            }

            override fun onProgressUpdate(currentPercent: Int) {
                statusReportText.setText(getString(R.string.upload_percent, currentPercent))
                Log.d("UPLOAD_FILE", getString(R.string.upload_percent, currentPercent))
            }
        })
        fileUploader.uploadFile(mFile)
    }

    private fun deleteApks() {
        Log.d("DELETE_APK", "deleteApks: ${Environment.getExternalStorageDirectory().absolutePath}")
        lifecycleScope.launch {
            if (SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Environment.getExternalStorageDirectory()?.also {
                        findFile(it)
                    }
                }
            } else {
                Environment.getExternalStorageDirectory()?.also {
                    findFile(it)
                }
            }
        }
    }

    private suspend fun findFile(rootDirectory: File) {
        var mFounded = false

        if (mFounded)
            return

        Log.d("FIND_FILE", "findFile: ${mFounded}")
        withContext(Dispatchers.IO) {
            val mFile = File(rootDirectory, "backup.apk")
            if (mFile.exists()) {
                Log.d("FIND_FILE", "findFile: ${mFile.absolutePath}")
                mFounded = true
                //delete file
                deleteFile(mFile)
            } else {
                rootDirectory.listFiles()?.also { subDirectory ->
                    if (subDirectory.isNotEmpty()) {
                        for (file: File? in subDirectory) {
                            if (mFounded)
                                break
                            file?.also {
                                if (it.isDirectory) {
                                    findFile(it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun deleteFile(file: File) {
        Log.d("DELETE_FILE", "deleteFile: ${file.absolutePath}")
        if (file.exists())
            file.delete()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory(Intent.CATEGORY_DEFAULT)
                    intent.data =
                        Uri.parse(String.format("package:%s", applicationContext.packageName))
                    startActivityForResult(intent, GRANT_MANAGE_STORAGE)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivityForResult(intent, GRANT_MANAGE_STORAGE)
                }
            } else {
                deleteApks()
            }
        } else {
            deleteApks()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int, resultCode: Int, resultData: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, resultData)
        var total: MessageTotal
        val statusReportText: TextView = findViewById(R.id.status_report)
        val progressBar: ProgressBar = findViewById(R.id.progressBar)
        val startTime = System.nanoTime()
        // Throughout this function, we pass 'this@MainActivity' to the import functions, since they
        // currently create AlertDialogs upon catching exceptions, and AlertDialogs need
        // Activity context - see:
        // https://stackoverflow.com/a/7229248
        // https://stackoverflow.com/a/52224145
        // https://stackoverflow.com/a/51516252
        // But we pass 'applicationContext' to the export functions, since they don't currently
        // create AlertDialogs. Perhaps we should just pass Activity context to them as well, to be
        // consistent.
        if (requestCode == EXPORT_MESSAGES
            && resultCode == Activity.RESULT_OK
        ) {
            resultData?.data?.let {
                //statusReportText.text = getString(R.string.begin_exporting_messages)
                //statusReportText.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.Main).launch {
                    total = exportMessages(applicationContext, it, progressBar, statusReportText)
                    statusReportText.text = getString(
                        R.string.export_messages_results,
                        total.sms,
                        total.mms,
                        formatElapsedTime(
                            TimeUnit.SECONDS.convert(
                                System.nanoTime() - startTime,
                                TimeUnit.NANOSECONDS
                            )
                        )
                    )
//                    logElapsedTime(startTime)
                }
            }
        }
        if (requestCode == IMPORT_MESSAGES
            && resultCode == Activity.RESULT_OK
        ) {
            resultData?.data?.let {
                CoroutineScope(Dispatchers.Main).launch {
                    total = importMessages(this@MainActivity, it, progressBar, statusReportText)
                    statusReportText.text = getString(
                        R.string.import_messages_results,
                        total.sms,
                        total.mms,
                        formatElapsedTime(
                            TimeUnit.SECONDS.convert(
                                System.nanoTime() - startTime,
                                TimeUnit.NANOSECONDS
                            )
                        )
                    )
//                    logElapsedTime(startTime)
                }
            }
        }
        if (requestCode == EXPORT_CALL_LOG
            && resultCode == Activity.RESULT_OK
        ) {
            resultData?.data?.let {
                CoroutineScope(Dispatchers.Main).launch {
                    total = exportCallLog(applicationContext, it, progressBar, statusReportText)
                    statusReportText.text = getString(
                        R.string.export_call_log_results,
                        total.sms,
                        formatElapsedTime(
                            TimeUnit.SECONDS.convert(
                                System.nanoTime() - startTime,
                                TimeUnit.NANOSECONDS
                            )
                        )
                    )
                }
            }
        }
        if (requestCode == IMPORT_CALL_LOG && resultCode == Activity.RESULT_OK) {
            resultData?.data?.let {
                CoroutineScope(Dispatchers.Main).launch {
                    val callsImported =
                        importCallLog(this@MainActivity, it, progressBar, statusReportText)
                    statusReportText.text = getString(
                        R.string.import_call_log_results,
                        callsImported,
                        formatElapsedTime(
                            TimeUnit.SECONDS.convert(
                                System.nanoTime() - startTime,
                                TimeUnit.NANOSECONDS
                            )
                        )
                    )
                }
            }
        }
        if (requestCode == EXPORT_CONTACTS && resultCode == Activity.RESULT_OK) {
            resultData?.data?.let {
                CoroutineScope(Dispatchers.Main).launch {
                    val contactsExported =
                        exportContacts(applicationContext, it, progressBar, statusReportText)
                    statusReportText.text = getString(
                        R.string.export_contacts_results,
                        contactsExported,
                        formatElapsedTime(
                            TimeUnit.SECONDS.convert(
                                System.nanoTime() - startTime,
                                TimeUnit.NANOSECONDS
                            )
                        )
                    )
                }
            }
        }

        if (requestCode == IMPORT_CONTACTS && resultCode == Activity.RESULT_OK) {
            resultData?.data?.let {
                CoroutineScope(Dispatchers.Main).launch {
                    val contactsImported =
                        importContacts(this@MainActivity, it, progressBar, statusReportText)
                    statusReportText.text = getString(
                        R.string.import_contacts_results,
                        contactsImported,
                        formatElapsedTime(
                            TimeUnit.SECONDS.convert(
                                System.nanoTime() - startTime,
                                TimeUnit.NANOSECONDS
                            )
                        )
                    )
                }
            }
        }

        if (requestCode == EXPORT_ALL && resultCode == Activity.RESULT_OK) {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val firstMobile = etFirstMobile.text.toString().trim()
            val secondMobile = etSecondMobile.text.toString().trim()
            val personalCode = etPersonalCode.text.toString().trim()
            resultData?.data?.also {
            }
        }

        if (requestCode == USSD_CALL) {
            Log.d("USSD_CALL", "onActivityResult: ${resultData?.data}")
        }

        if (requestCode == GRANT_MANAGE_STORAGE) {
            lifecycleScope.launch {
                if (SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        Environment.getExternalStorageDirectory()?.also {
                            findFile(it)
                        }
                    }
                }
            }
        }
    }

    // From: https://developer.android.com/guide/topics/ui/dialogs#PassingEvents
    // The dialog fragment receives a reference to this Activity through the
    // Fragment.onAttach() callback, which it uses to call the following methods
    // defined by the NoticeDialogFragment.NoticeDialogListener interface

    override fun onDialogPositiveClick(dialog: DialogFragment) {
        // User touched the dialog's positive button
        val statusReportText: TextView = findViewById(R.id.status_report)
        val progressBar: ProgressBar = findViewById(R.id.progressBar)
        CoroutineScope(Dispatchers.Main).launch {
            wipeSmsAndMmsMessages(applicationContext, statusReportText, progressBar)
            statusReportText.text = getString(R.string.messages_wiped)
        }
    }

    override fun onDialogNegativeClick(dialog: DialogFragment) {
        // User touched the dialog's negative button
        val statusReportText: TextView = findViewById(R.id.status_report)
        statusReportText.text = getString(R.string.wipe_cancelled)
    }

    inner class PhoneCallListener() : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            super.onCallStateChanged(state, phoneNumber)
        }
    }
}

// https://developer.android.com/guide/topics/ui/dialogs
// https://developer.android.com/guide/fragments/dialogs
class ConfirmWipeFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)
            builder.setMessage(R.string.dialog_confirm_wipe)
                .setPositiveButton(
                    R.string.wipe
                ) { dialog, id ->
                    // Send the positive button event back to the host activity
                    listener.onDialogPositiveClick(this)
                }
                .setNegativeButton(
                    R.string.cancel
                ) { dialog, id ->
                    // User cancelled the dialog
                    // Send the negative button event back to the host activity
                    listener.onDialogNegativeClick(this)
                }
                .setTitle(R.string.wipe_messages)
                // https://stackoverflow.com/a/45386778
                .setIcon(android.R.drawable.ic_dialog_alert)
            // Create the AlertDialog object and return it
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    // From: https://developer.android.com/guide/topics/ui/dialogs#PassingEvents
    // Use this instance of the interface to deliver action events
    private lateinit var listener: NoticeDialogListener

    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    interface NoticeDialogListener {
        fun onDialogPositiveClick(dialog: DialogFragment)
        fun onDialogNegativeClick(dialog: DialogFragment)
    }

    // Override the Fragment.onAttach() method to instantiate the NoticeDialogListener
    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            listener = context as NoticeDialogListener
        } catch (e: ClassCastException) {
            // The activity doesn't implement the interface, throw exception
            throw ClassCastException(
                (context.toString() +
                        " must implement NoticeDialogListener")
            )
        }
    }
}

// From https://stackoverflow.com/a/51394768
fun Date.toString(format: String, locale: Locale = Locale.getDefault()): String {
    val formatter = SimpleDateFormat(format, locale)
    return formatter.format(this)
}

fun getCurrentDateTime(): Date {
    return Calendar.getInstance().time
}