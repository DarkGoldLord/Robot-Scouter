package com.supercilex.robotscouter.util.data

import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.PersistableBundle
import android.support.annotation.RequiresApi
import com.supercilex.robotscouter.data.model.Team
import com.supercilex.robotscouter.util.data.model.toBundle

const val TAB_KEY = "tab_key"
const val SCOUT_ARGS_KEY = "scout_args"
const val KEY_ADD_SCOUT = "add_scout"
const val KEY_OVERRIDE_TEMPLATE_KEY = "override_template_key"

fun Parcel.readBooleanCompat() = getBooleanForInt(readInt())

fun Parcel.writeBooleanCompat(value: Boolean) = writeInt(getIntForBoolean(value))

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun PersistableBundle.getBooleanCompat(key: String) = getBooleanForInt(getInt(key))

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
fun PersistableBundle.putBooleanCompat(key: String, value: Boolean) =
        putInt(key, getIntForBoolean(value))

private fun getBooleanForInt(value: Int) = value == 1

private fun getIntForBoolean(value: Boolean) = if (value) 1 else 0

fun getTabKeyBundle(key: String?) = Bundle().apply { putString(TAB_KEY, key) }

fun getTabKey(bundle: Bundle): String? = bundle.getString(TAB_KEY)

fun getScoutBundle(team: Team,
                   addScout: Boolean = false,
                   overrideTemplateKey: String? = null,
                   scoutKey: String? = null): Bundle {
    if (!addScout && overrideTemplateKey != null) {
        throw IllegalArgumentException("Can't use an override key without adding a scout.")
    }

    return team.toBundle().apply {
        putBoolean(KEY_ADD_SCOUT, addScout)
        putString(KEY_OVERRIDE_TEMPLATE_KEY, overrideTemplateKey)
        putAll(getTabKeyBundle(scoutKey))
    }
}
