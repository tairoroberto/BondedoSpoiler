package br.com.tairoroberto.bondedospoiler

import android.Manifest.permission.READ_CONTACTS
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import com.facebook.*
import com.facebook.AccessToken
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.facebook.login.widget.LoginButton
import kotlinx.android.synthetic.main.activity_login.*
import org.json.JSONException


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
    private var profileTracker: ProfileTracker? = null
    private val REQUEST_CODE_PICK_WHATSAPP = 2
    private val ID = "id"
    private val NAME = "name"
    private var loading = false



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        facebookSDKInitialize()

        object : ProfileTracker() {
            override fun onCurrentProfileChanged(
                    oldProfile: Profile?,
                    currentProfile: Profile?) {
                updateUI()
            }
        }

        mayRequestContacts()

        if( isLoggedIn() ) {
            getFriends(AccessToken.getCurrentAccessToken())

        }else {
            loginButton.setReadPermissions("email", "public_profile","user_friends")
            getLoginDetails(loginButton)
        }

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
            }
        })
    }


    fun facebookSDKInitialize() {
        FacebookSdk.sdkInitialize(applicationContext)
        callbackManager = CallbackManager.Factory.create()
    }

    fun getLoginDetails(loginButton: LoginButton) {
        // Callback registration
        loginButton.registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(loginResult: LoginResult) {
                getFriends(loginResult.accessToken)
            }

            override fun onCancel() {
                Log.i("LOG", "onCancel")
            }

            override fun onError(exception: FacebookException) {
                Log.i("LOG", "onError: ${exception.message}")
            }
        })
    }

    fun getFriends(token: AccessToken){
        GraphRequest(
                token,
                //AccessToken.getCurrentAccessToken(),
                "/me/taggable_friends",
                null,
                HttpMethod.GET,
                GraphRequest.Callback { response ->
                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    try {

                        Log.i("LOG", response.jsonObject.toString())

                        val rawName = response.jsonObject.getJSONArray("data")
                        intent.putExtra("jsondata", rawName.toString())
                        startActivity(intent)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
        ).executeAsync()
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
}

