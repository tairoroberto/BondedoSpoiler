package br.com.tairoroberto.bondedospoiler

import android.Manifest.permission.READ_CONTACTS
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import com.facebook.*
import com.facebook.login.LoginManager
import com.facebook.share.ShareApi
import com.facebook.share.Sharer
import com.facebook.share.model.ShareLinkContent
import com.facebook.share.model.SharePhoto
import com.facebook.share.model.SharePhotoContent
import com.facebook.share.widget.ShareDialog
import kotlinx.android.synthetic.main.activity_login.*
import org.json.JSONException
import java.util.*
import kotlin.collections.ArrayList

/**
 * A login screen that offers login via email/password.
 */
class LoginActivity : AppCompatActivity() {
    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private var callbackManager: CallbackManager? = null

    private val GRAPH_PATH = "me/permissions"
    private val SUCCESS = "success"
    private val PICK_PERMS_REQUEST = 0
    private var pendingAction = PendingAction.NONE
    private val PERMISSION = "publish_actions"
    private var canPresentShareDialogWithPhotos: Boolean = false
    private var canPresentShareDialog: Boolean = false
    private var shareDialog: ShareDialog? = null
    private var profileTracker: ProfileTracker? = null
    private val REQUEST_CODE_PICK_WHATSAPP = 2

    private val PENDING_ACTION_BUNDLE_KEY = "br.com.tairoroberto.bondedospoiler:PendingAction"


    private val shareCallback = object : FacebookCallback<Sharer.Result> {
        override fun onCancel() {
            Log.d("HelloFacebook", "Canceled")
        }

        override fun onError(error: FacebookException) {
            Log.d("HelloFacebook", String.format("Error: %s", error.toString()))
            val title = "Erro compartilhar"
            val alertMessage = error.message
            showResult(title, alertMessage!!)
        }

        override fun onSuccess(result: Sharer.Result) {
            Log.d("HelloFacebook", "Success!")
            if (result.postId != null) {
                val title = "Sucesso"
                val id = result.postId
                val alertMessage = getString(R.string.successfully_posted_post, id)
                showResult(title, alertMessage)
            }
        }

        private fun showResult(title: String, alertMessage: String) {
            AlertDialog.Builder(this@LoginActivity)
                    .setTitle(title)
                    .setMessage(alertMessage)
                    .setPositiveButton("OK", null)
                    .show()
            showProgress(false)
        }
    }

    private enum class PendingAction {
        NONE,
        POST_PHOTO,
        POST_STATUS_UPDATE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        callbackManager = CallbackManager.Factory.create()
        shareDialog = ShareDialog(this)
        shareDialog?.registerCallback(callbackManager, shareCallback)

        if (savedInstanceState != null) {
            val name = savedInstanceState.getString(PENDING_ACTION_BUNDLE_KEY)
            pendingAction = PendingAction.valueOf(name)
        }

        object : ProfileTracker() {
            override fun onCurrentProfileChanged(
                    oldProfile: Profile?,
                    currentProfile: Profile?) {
                updateUI()
            }
        }

        mayRequestContacts()

        emailSignInButton.setOnClickListener({
            if( isLoggedIn() ) {
                val callback = GraphRequest.Callback { response ->
                    try {
                        if (response.error != null) {
                            Toast.makeText(this, "Falha ao deslogar", Toast.LENGTH_LONG).show()
                        } else if (response.jsonObject.getBoolean(SUCCESS)) {
                            LoginManager.getInstance().logOut()
                        }
                    } catch (ex: JSONException) { }
                }
                val request = GraphRequest(AccessToken.getCurrentAccessToken(), GRAPH_PATH, Bundle(), HttpMethod.DELETE, callback)
                request.executeAsync()
            }else{
                LoginManager.getInstance().logInWithPublishPermissions(this, Arrays.asList<String>(PERMISSION))
            }
        })
        postButton.setOnClickListener({
            onClickPostPhoto()
            showProgress(true)
        })

        // Can we present the share dialog for regular links?
        canPresentShareDialog = ShareDialog.canShow(ShareLinkContent::class.java)

        // Can we present the share dialog for photos?
        canPresentShareDialogWithPhotos = ShareDialog.canShow(SharePhotoContent::class.java)


        /*val c : Cursor = contentResolver.query(
        ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts.CONTACT_ID, ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY),
        ContactsContract.RawContacts.ACCOUNT_TYPE + "= ?", arrayOf("com.whatsapp"), null)

        val myWhatsappContacts : ArrayList<String>  = ArrayList()
        val contactNameColumn = c.getColumnIndex(ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY)
        val contactNameColumnNumber = c.getColumnIndex(ContactsContract.RawContacts.DATA_SET)
        while (c.moveToNext()) {
            myWhatsappContacts.add(c.getString(contactNameColumn))
        }
        Log.i("LOG","myWhatsappContacts: $myWhatsappContacts" )
        Log.i("LOG","myWhatsappContacts Numbers: $contactNameColumnNumber" )


        val intent:Intent  = Intent(Intent.ACTION_PICK)
        intent.`package` = "com.whatsapp"
        try{
            startActivityForResult(intent, REQUEST_CODE_PICK_WHATSAPP)
        } catch (e:Exception) {
            Toast.makeText(this, "Whattsapp não instalado", Toast.LENGTH_SHORT).show()
        }

        val sendIntent:Intent = Intent("android.intent.action.MAIN");
        sendIntent.component = ComponentName("com.whatsapp", "com.whatsapp.Conversation")
        sendIntent.putExtra("jid", PhoneNumberUtils.stripSeparators("+5511972781404") + "@s.whatsapp.net")
        startActivity(sendIntent)*/

    }


    private fun mayRequestContacts(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        if (checkSelfPermission(READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        if (shouldShowRequestPermissionRationale(READ_CONTACTS)) {
            Snackbar.make(emailLoginForm, R.string.permission_rationale, Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok, { requestPermissions(arrayOf(READ_CONTACTS), PICK_PERMS_REQUEST) })
        } else {
            requestPermissions(arrayOf(READ_CONTACTS), PICK_PERMS_REQUEST)
        }
        return false
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == PICK_PERMS_REQUEST) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // populateAutoComplete()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        profileTracker?.stopTracking()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(PENDING_ACTION_BUNDLE_KEY, pendingAction.name)
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    private fun showProgress(show: Boolean) {
        val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime)

        loginForm.visibility = if (show) View.GONE else View.VISIBLE
        loginForm.animate().setDuration(shortAnimTime.toLong()).alpha(
                (if (show) 0 else 1).toFloat()).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                loginForm.visibility = if (show) View.GONE else View.VISIBLE
            }
        })

        loginProgress.visibility = if (show) View.VISIBLE else View.GONE
        loginProgress.animate().setDuration(shortAnimTime.toLong()).alpha(
                (if (show) 1 else 0).toFloat()).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                loginProgress.visibility = if (show) View.VISIBLE else View.GONE
            }
        })
    }

    private fun isLoggedIn(): Boolean {
        val accesstoken = AccessToken.getCurrentAccessToken()
        return !(accesstoken == null || accesstoken.permissions.isEmpty())
    }

    private fun updateUI() {
        val profile = Profile.getCurrentProfile()
        if (profile != null) {
            userPicture.profileId = profile.id
            userName.text = String.format("%s %s", profile.firstName, profile.lastName)
        } else {
            userPicture.profileId = null
            userName.text = "Welcome"
        }
        showProgress(false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_PICK_WHATSAPP -> if (resultCode == RESULT_OK) {
                if (intent.hasExtra("contact")) {
                    val address = intent.getStringExtra("contact")
                    Log.d("LOG", "The selected Whatsapp address is: " + address)
                }
            }
            else -> {
                callbackManager?.onActivityResult(requestCode, resultCode, data)
            }
        }
    }

    private fun hasPublishPermission(): Boolean {
        val accessToken = AccessToken.getCurrentAccessToken()
        return accessToken != null && accessToken.permissions.contains("publish_actions")
    }

    private fun onClickPostPhoto() {
        performPublish(PendingAction.POST_PHOTO, canPresentShareDialogWithPhotos)
    }

    private fun postStatusUpdate() {
        val profile = Profile.getCurrentProfile()
        val listPeople = ArrayList<String>()
        listPeople.add("691329721")

        val linkContent = ShareLinkContent.Builder()
                .setContentTitle("Hello Facebook")
                .setContentDescription(
                        "Teste com api facbook kkkkkkkkkk")
                .setContentUrl(Uri.parse("http://developers.facebook.com/docs/android"))
                .setPeopleIds(listPeople)
                .build()

        if (profile != null && hasPublishPermission()) {
            ShareApi.share(linkContent, shareCallback)
        } else {
            pendingAction = PendingAction.POST_STATUS_UPDATE
        }
    }

    private fun performPublish(action: PendingAction, allowNoToken: Boolean) {
        val accessToken = AccessToken.getCurrentAccessToken()
        if (accessToken != null || allowNoToken) {
            pendingAction = action
            handlePendingAction()
        }
    }

    private fun handlePendingAction() {
        val previouslyPendingAction = pendingAction
        // These actions may re-set pendingAction if they are still pending, but we assume they
        // will succeed.
        pendingAction = PendingAction.NONE

        when (previouslyPendingAction) {
            LoginActivity.PendingAction.NONE -> {
            }
            LoginActivity.PendingAction.POST_PHOTO -> postPhoto()
            LoginActivity.PendingAction.POST_STATUS_UPDATE -> postStatusUpdate()
        }
    }

    private fun postPhoto() {
        val image = BitmapFactory.decodeResource(this.resources, R.drawable.logan)
        val sharePhoto = SharePhoto.Builder().setBitmap(image).build()
        val photos = ArrayList<SharePhoto>()
        photos.add(sharePhoto)

        val profile = Profile.getCurrentProfile()
        val listPeople = ArrayList<String>()
        listPeople.add("538273729")

        val sharePhotoContent = SharePhotoContent.Builder()
                .setPhotos(photos)
                .setContentUrl(Uri.parse("http://developers.facebook.com/docs/android"))
                .setPeopleIds(listPeople)
                .build()

        if (profile != null && hasPublishPermission()) {
            ShareApi.share(sharePhotoContent, shareCallback)
        } else {
            pendingAction = PendingAction.POST_PHOTO
            // We need to get new permissions, then complete the action when we get called back.
            LoginManager.getInstance().logInWithPublishPermissions(this, Arrays.asList<String>(PERMISSION))
        }
    }
}

