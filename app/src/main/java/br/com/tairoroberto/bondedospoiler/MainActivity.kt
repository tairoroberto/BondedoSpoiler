package br.com.tairoroberto.bondedospoiler

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import com.facebook.*
import com.facebook.login.LoginManager
import com.facebook.share.ShareApi
import com.facebook.share.Sharer
import com.facebook.share.model.ShareLinkContent
import com.facebook.share.model.SharePhoto
import com.facebook.share.model.SharePhotoContent
import com.facebook.share.widget.ShareDialog
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONArray
import org.json.JSONException
import java.util.*

class MainActivity : AppCompatActivity() {

    private var callbackManager: CallbackManager? = null
    private var pendingAction = MainActivity.PendingAction.NONE
    private val PERMISSION = "publish_actions"
    private var canPresentShareDialogWithPhotos: Boolean = false
    private var canPresentShareDialog: Boolean = false
    private val PENDING_ACTION_BUNDLE_KEY = "br.com.tairoroberto.bondedospoiler:PendingAction"
    private var shareDialog: ShareDialog? = null
    private val REQUEST_CODE_PICK_WHATSAPP = 2

    private enum class PendingAction {
        NONE,
        POST_PHOTO,
        POST_STATUS_UPDATE
    }

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
            showProgress(false)
        }

        private fun showResult(title: String, alertMessage: String) {
            AlertDialog.Builder(this@MainActivity)
                    .setTitle(title)
                    .setMessage(alertMessage)
                    .setPositiveButton("OK", null)
                    .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        facebookSDKInitialize()
        val profile = Profile.getCurrentProfile()
        if (profile != null && !hasPublishPermission()) {
            // We need to get new permissions, then complete the action when we get called back.
            LoginManager.getInstance().logInWithPublishPermissions(this, Arrays.asList<String>(PERMISSION))
        }

        if (savedInstanceState != null) {
            val name = savedInstanceState.getString(PENDING_ACTION_BUNDLE_KEY)
            pendingAction = PendingAction.valueOf(name)
        }


        shareDialog = ShareDialog(this)
        shareDialog?.registerCallback(callbackManager, shareCallback)

        val intent = intent
        val jsondata = intent.getStringExtra("jsondata")

        val friendslist: JSONArray
        val friends = ArrayList<String>()

        try {
            friendslist = JSONArray(jsondata)
            for (l in 0..friendslist.length() - 1) {
                friends.add(friendslist.getJSONObject(l).getString("name"))
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }


        val adapter = ArrayAdapter(this, R.layout.activity_listview, friends) // simple textview for list item
        val listView = findViewById(R.id.listView) as ListView
        listView.adapter = adapter

        fab.setOnClickListener {
            onClickPostPhoto()
            showProgress(true)
        }

        // Can we present the share dialog for regular links?
        canPresentShareDialog = ShareDialog.canShow(ShareLinkContent::class.java)

        // Can we present the share dialog for photos?
        canPresentShareDialogWithPhotos = ShareDialog.canShow(SharePhotoContent::class.java)
    }

    fun facebookSDKInitialize() {
        FacebookSdk.sdkInitialize(applicationContext)
        callbackManager = CallbackManager.Factory.create()
    }

    private fun onClickPostPhoto() {
        performPublish(MainActivity.PendingAction.POST_PHOTO, canPresentShareDialogWithPhotos)
    }

    private fun performPublish(action: MainActivity.PendingAction, allowNoToken: Boolean) {
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
        pendingAction = MainActivity.PendingAction.NONE

        when (previouslyPendingAction) {
            MainActivity.PendingAction.NONE -> {
            }
            MainActivity.PendingAction.POST_PHOTO -> postPhoto()
            MainActivity.PendingAction.POST_STATUS_UPDATE -> postStatusUpdate()
        }
    }

    private fun postPhoto() {
        val image = BitmapFactory.decodeResource(this.resources, R.drawable.logan)
        val sharePhoto = SharePhoto.Builder().setBitmap(image).build()
        val photos = ArrayList<SharePhoto>()
        photos.add(sharePhoto)

        val profile = Profile.getCurrentProfile()
        val listPeople = ArrayList<String>()
        listPeople.add("100001130942482")

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

    private fun postStatusUpdate() {
        val profile = Profile.getCurrentProfile()
        val listPeople = ArrayList<String>()
        listPeople.add("100001130942482")

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

    private fun hasPublishPermission(): Boolean {
        val accessToken = AccessToken.getCurrentAccessToken()
        return accessToken != null && accessToken.permissions.contains("publish_actions")
    }


    private fun showProgress(show: Boolean) {
        val shortAnimTime = resources.getInteger(android.R.integer.config_shortAnimTime)

        content.visibility = if (show) View.GONE else View.VISIBLE
        content.animate().setDuration(shortAnimTime.toLong()).alpha(
                (if (show) 0 else 1).toFloat()).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                content.visibility = if (show) View.GONE else View.VISIBLE
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


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(PENDING_ACTION_BUNDLE_KEY, pendingAction.name)
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
