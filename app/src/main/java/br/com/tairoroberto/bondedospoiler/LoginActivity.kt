package br.com.tairoroberto.bondedospoiler

import android.Manifest.permission.READ_CONTACTS
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.facebook.*
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.facebook.share.ShareApi
import com.facebook.share.Sharer
import com.facebook.share.model.ShareLinkContent
import com.facebook.share.model.SharePhoto
import com.facebook.share.model.SharePhotoContent
import com.facebook.share.widget.ShareDialog
import kotlinx.android.synthetic.main.activity_login.*
import java.util.*
import kotlin.collections.ArrayList

/**
 * A login screen that offers login via email/password.
 */
class LoginActivity : AppCompatActivity() {
    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private var mAuthTask: UserLoginTask? = null
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

        loginButton.registerCallback(callbackManager,
                object : FacebookCallback<LoginResult> {
                    override fun onSuccess(loginResult: LoginResult) {
                        handlePendingAction()
                        updateUI()
                    }

                    override fun onCancel() {
                        if (pendingAction != PendingAction.NONE) {
                            showAlert()
                            pendingAction = PendingAction.NONE
                        }
                        updateUI()
                    }

                    override fun onError(exception: FacebookException) {
                        if (pendingAction != PendingAction.NONE && exception is FacebookAuthorizationException) {
                            showAlert()
                            pendingAction = PendingAction.NONE
                        }
                        updateUI()
                    }

                    private fun showAlert() {
                        AlertDialog.Builder(this@LoginActivity)
                                .setTitle("Canclado")
                                .setMessage("Seem peermissão")
                                .setPositiveButton("OK", null)
                                .show()
                    }
                })

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

        passwordView.editText?.setOnEditorActionListener(TextView.OnEditorActionListener { _, id, _ ->
            if (id == R.id.emailView || id == EditorInfo.IME_NULL) {
                attemptLogin()
                return@OnEditorActionListener true
            }
            false
        })
        //emailSignInButton.setOnClickListener { attemptLogin() }

        mayRequestContacts()

        emailSignInButton.setOnClickListener({
            onClickPostPhoto()
        })

        // Can we present the share dialog for regular links?
        canPresentShareDialog = ShareDialog.canShow(ShareLinkContent::class.java)

        // Can we present the share dialog for photos?
        canPresentShareDialogWithPhotos = ShareDialog.canShow(SharePhotoContent::class.java)
    }


    private fun mayRequestContacts(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        if (checkSelfPermission(READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        if (shouldShowRequestPermissionRationale(READ_CONTACTS)) {
            Snackbar.make(passwordView, R.string.permission_rationale, Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok, { requestPermissions(arrayOf(READ_CONTACTS), REQUEST_READ_CONTACTS) })
        } else {
            requestPermissions(arrayOf(READ_CONTACTS), REQUEST_READ_CONTACTS)
        }
        return false
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_READ_CONTACTS) {
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
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private fun attemptLogin() {
        if (mAuthTask != null) {
            return
        }

        // Reset errors.
        emailView.error = null
        passwordView.error = null

        // Store values at the time of the login attempt.
        val email = emailView.editText?.text.toString()
        val password = passwordView.editText?.text.toString()

        var cancel = false
        var focusView: View? = null

        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            passwordView.error = getString(R.string.error_invalid_password)
            focusView = passwordView
            cancel = true
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            emailView.error = getString(R.string.error_field_required)
            focusView = emailView
            cancel = true
        } else if (!isEmailValid(email)) {
            emailView.error = getString(R.string.error_invalid_email)
            focusView = emailView
            cancel = true
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView?.requestFocus()
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true)
            mAuthTask = UserLoginTask(email, password)
            mAuthTask!!.execute(null as Void?)
        }
    }

    private fun isEmailValid(email: String): Boolean {
        //TODO: Replace this with your own logic
        return email.contains("@")
    }

    private fun isPasswordValid(password: String): Boolean {
        //TODO: Replace this with your own logic
        return password.length > 4
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

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    inner class UserLoginTask internal constructor(private val mEmail: String, private val mPassword: String) : AsyncTask<Void, Void, Boolean>() {

        override fun doInBackground(vararg params: Void): Boolean? {
            // TODO: attempt authentication against a network service.

            try {
                // Simulate network access.
                Thread.sleep(2000)
            } catch (e: InterruptedException) {
                return false
            }

            // TODO: register the new account here.
            return DUMMY_CREDENTIALS
                    .map { credential -> credential.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray() }
                    .firstOrNull { it[0] == mEmail }
                    ?.let {
                        // Account exists, return true if the password matches.
                        it[1] == mPassword
                    }
                    ?: true
        }

        override fun onPostExecute(success: Boolean?) {
            mAuthTask = null
            showProgress(false)

            if (success!!) {
                finish()
            } else {
                passwordView.error = getString(R.string.error_incorrect_password)
                passwordView.requestFocus()
            }
        }

        override fun onCancelled() {
            mAuthTask = null
            showProgress(false)
        }
    }

    companion object {
        /**
         * Id to identity READ_CONTACTS permission request.
         */
        private val REQUEST_READ_CONTACTS = 0
        private val DUMMY_CREDENTIALS = arrayOf("tairoroberto@gmail.com:123456", "teste@gmail.com.com:123456")
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
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager?.onActivityResult(requestCode, resultCode, data)
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

