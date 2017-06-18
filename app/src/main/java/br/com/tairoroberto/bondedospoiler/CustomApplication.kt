package br.com.tairoroberto.bondedospoiler

import android.app.Application

import org.json.JSONObject

/**
 * Created by tairo on 6/8/17.
 * Use a custom Application class to pass state data between Activities.
 */
class CustomApplication : Application() {
    var selectedUsers: List<JSONObject>? = null
    var selectedPlace: JSONObject? = null
}
