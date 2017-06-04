/**
 * Copyright (c) 2014-present, Facebook, Inc. All rights reserved.

 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Facebook.

 * As with any software that integrates with the Facebook platform, your use of
 * this software is subject to the Facebook Developer Principles and Policies
 * [http://developers.facebook.com/policy/]. This copyright notice shall be
 * included in all copies or substantial portions of the software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package br.com.tairoroberto.bondedospoiler

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.View.OnClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import com.facebook.login.DefaultAudience
import kotlinx.android.synthetic.main.list_perms_new.*
import java.util.*

class PermissionSelectActivity : Activity(), OnClickListener {

    internal var adapter: ArrayAdapter<String>? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.list_perms_new)
        val perms = resources.getStringArray(R.array.perms_array)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, perms)
        list.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        list.adapter = adapter!!
        confirm.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        val checked = list.checkedItemPositions
        val readPerms = ArrayList<String>()
        var writePri: String? = null
        val publishPerms = ArrayList<String>()
        for (i in 0..checked.size() - 1) {
            // Item position in adapter
            val position = checked.keyAt(i)
            // Add perm only if checked
            if (checked.valueAt(i)) {
                val checkedItem = adapter?.getItem(position)
                if (DefaultAudience.EVERYONE.toString() == checkedItem) {
                    writePri = "EVERYONE"
                } else if (DefaultAudience.FRIENDS.toString() == checkedItem) {
                    writePri = "FRIENDS"
                } else if (DefaultAudience.ONLY_ME.toString() == checkedItem) {
                    writePri = "ONLY_ME"
                } else if (PUBLISH_PERMS_LIST.contains(checkedItem)) {
                    publishPerms.add(checkedItem!!)
                } else
                    readPerms.add(checkedItem!!)
            }
        }

        val readPermsArr = readPerms.toTypedArray()
        val publishPermsArr = publishPerms.toTypedArray()
        val intent = Intent()
        intent.putExtra(EXTRA_SELECTED_READ_PARAMS, readPermsArr)
        intent.putExtra(EXTRA_SELECTED_WRITE_PRIVACY, writePri)
        intent.putExtra(EXTRA_SELECTED_PUBLISH_PARAMS, publishPermsArr)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    companion object {

        val TAG: String = PermissionSelectActivity::class.java.simpleName

        val EXTRA_SELECTED_READ_PARAMS = TAG + ".selectedReadPerms"
        val EXTRA_SELECTED_WRITE_PRIVACY = TAG + ".selectedWritePrivacy"
        val EXTRA_SELECTED_PUBLISH_PARAMS = TAG + ".selectedPublishPerms"

        // Permissions
        var PUBLISH_ACTIONS = "publish_actions"
        var PUBLISH_CHECKINS = "publish_checkins"
        var ADS_MANAGEMENT = "ads_management"
        var CREATE_EVENT = "create_event"
        var MANAGE_FRIENDLISTS = "manage_friendlists"
        var MANAGE_NOTIFICATIONS = "manage_notifications"
        var MANAGE_PAGES = "manage_pages"
        var RSVP_EVENT = "rsvp_event"

        val PUBLISH_PERMS_LIST: Set<String> = HashSet(Arrays.asList(
                PUBLISH_ACTIONS,
                PUBLISH_CHECKINS,
                ADS_MANAGEMENT,
                CREATE_EVENT,
                MANAGE_FRIENDLISTS,
                MANAGE_NOTIFICATIONS,
                MANAGE_PAGES,
                RSVP_EVENT))
    }
}
