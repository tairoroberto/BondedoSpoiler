package br.com.tairoroberto.bondedospoiler

import android.Manifest.permission.READ_CONTACTS
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.TargetApi
import android.app.Activity
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
import android.widget.Toast
import com.facebook.*
import com.facebook.login.DefaultAudience
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.facebook.share.ShareApi
import com.facebook.share.Sharer
import com.facebook.share.model.ShareLinkContent
import com.facebook.share.model.SharePhoto
import com.facebook.share.model.SharePhotoContent
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
    private var mAuthTask: UserLoginTask? = null
    private var callbackManager: CallbackManager? = null

    private val GRAPH_PATH = "me/permissions"
    private val SUCCESS = "success"
    private val PICK_PERMS_REQUEST = 0
    private var pendingAction = PendingAction.NONE
    private val PERMISSION = "publish_actions"
    private var canPresentShareDialogWithPhotos: Boolean = false

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

        passwordView.editText?.setOnEditorActionListener(TextView.OnEditorActionListener { _, id, _ ->
            if (id == R.id.emailView || id == EditorInfo.IME_NULL) {
                attemptLogin()
                return@OnEditorActionListener true
            }
            false
        })
        //emailSignInButton.setOnClickListener { attemptLogin() }

        callbackManager = CallbackManager.Factory.create()
        mayRequestContacts()

        emailSignInButton.setOnClickListener(View.OnClickListener {
            /*if (!isLoggedIn()) {
                Toast.makeText(this@LoginActivity, "Não logado", Toast.LENGTH_LONG).show()
                return@OnClickListener
            }
            val callback = GraphRequest.Callback { response ->
                try {
                    if (response.error != null) {
                        Toast.makeText(this@LoginActivity, "Falha ao deslogar",Toast.LENGTH_LONG).show()
                    } else if (response.jsonObject.getBoolean(SUCCESS)) {
                        LoginManager.getInstance().logOut()
                        // updateUI();?
                    }
                } catch (ex: JSONException) { /* no op */
                }
            }
            val request = GraphRequest(AccessToken.getCurrentAccessToken(), GRAPH_PATH, Bundle(), HttpMethod.DELETE, callback)
            request.executeAsync()*/
            onClickPostPhoto()
        })

        // Callback registration
        loginButton.registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(loginResult: LoginResult) {
                // App code
                Toast.makeText(this@LoginActivity, "Sucsso", Toast.LENGTH_LONG).show()
                updateUI()
            }

            override fun onCancel() {
                // App code
                Toast.makeText(this@LoginActivity, "Cancelado", Toast.LENGTH_LONG).show()
            }

            override fun onError(exception: FacebookException) {
                // App code
                Toast.makeText(this@LoginActivity, "Erro ao logar", Toast.LENGTH_LONG).show()
            }
        })


        perms.setOnClickListener {
            val selectPermsIntent = Intent(this@LoginActivity, PermissionSelectActivity::class.java)
            startActivityForResult(selectPermsIntent, PICK_PERMS_REQUEST)
        }
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

        /**
         * A dummy authentication store containing known user names and passwords.
         * TODO: remove after connecting to a real authentication system.
         */
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
            userName.text = "Bem vindo"
        }
    }

    override fun onActivityResult(
            requestCode: Int,
            resultCode: Int,
            data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PERMS_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                val readPermsArr = data
                        .getStringArrayExtra(PermissionSelectActivity.EXTRA_SELECTED_READ_PARAMS)
                val writePrivacy = data
                        .getStringExtra(PermissionSelectActivity.EXTRA_SELECTED_WRITE_PRIVACY)
                val publishPermsArr = data
                        .getStringArrayExtra(
                                PermissionSelectActivity.EXTRA_SELECTED_PUBLISH_PARAMS)

                loginButton?.clearPermissions()

                if (readPermsArr != null) {
                    if (readPermsArr.isNotEmpty()) {
                        loginButton?.setReadPermissions(*readPermsArr)
                    }
                }

                if ((readPermsArr == null || readPermsArr.isEmpty()) && publishPermsArr != null) {
                    if (publishPermsArr.isNotEmpty()) {
                        loginButton?.setPublishPermissions(*publishPermsArr)
                    }
                }
                // Set write privacy for the user
                if (writePrivacy != null) {
                    val audience: DefaultAudience
                    if (DefaultAudience.EVERYONE.toString() == writePrivacy) {
                        audience = DefaultAudience.EVERYONE
                    } else if (DefaultAudience.FRIENDS.toString() == writePrivacy) {
                        audience = DefaultAudience.FRIENDS
                    } else {
                        audience = DefaultAudience.ONLY_ME
                    }
                    loginButton?.defaultAudience = audience
                }
            }
        } else {
            callbackManager?.onActivityResult(requestCode, resultCode, data)
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
        val listPeople:MutableList<String> = ArrayList()

        listPeople.add(0,"691329721")

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
        val image = BitmapFactory.decodeResource(this.resources, R.mipmap.ic_launcher)
        val sharePhoto = SharePhoto.Builder().setBitmap(image).build()
        val photos = ArrayList<SharePhoto>()
        photos.add(sharePhoto)

        val sharePhotoContent = SharePhotoContent.Builder().setPhotos(photos).build()
        if (hasPublishPermission()) {
            ShareApi.share(sharePhotoContent, shareCallback)
        } else {
            pendingAction = PendingAction.POST_PHOTO
            // We need to get new permissions, then complete the action when we get called back.
            LoginManager.getInstance().logInWithPublishPermissions(this, Arrays.asList<String>(PERMISSION))
        }
    }
}
