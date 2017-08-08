package com.supercilex.robotscouter.ui.teamlist

import android.app.Activity
import android.content.Intent
import android.support.design.widget.Snackbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.ErrorCodes
import com.firebase.ui.auth.IdpResponse
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.appindexing.FirebaseAppIndex
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.supercilex.robotscouter.R
import com.supercilex.robotscouter.data.client.AccountMergeService
import com.supercilex.robotscouter.data.model.User
import com.supercilex.robotscouter.util.ALL_PROVIDERS
import com.supercilex.robotscouter.util.data.hasShownSignInTutorial
import com.supercilex.robotscouter.util.data.model.add
import com.supercilex.robotscouter.util.isFullUser
import com.supercilex.robotscouter.util.isInTestMode
import com.supercilex.robotscouter.util.isSignedIn
import com.supercilex.robotscouter.util.logLoginEvent
import com.supercilex.robotscouter.util.signInAnonymouslyDbInit
import com.supercilex.robotscouter.util.uid
import com.supercilex.robotscouter.util.user

class AuthHelper(private val activity: TeamListActivity) : View.OnClickListener {
    private val rootView: View = activity.findViewById(R.id.root)

    private lateinit var signInMenuItem: MenuItem

    fun init(): Task<Nothing> =
            if (isSignedIn) Tasks.forResult(null) else signInAnonymously().continueWith { null }

    fun initMenu(menu: Menu) {
        signInMenuItem = menu.findItem(R.id.action_sign_in)
        updateMenuState()
    }

    fun signIn() = activity.startActivityForResult(
            AuthUI.getInstance().createSignInIntentBuilder()
                    .setAvailableProviders(
                            if (isInTestMode) listOf(AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build())
                            else ALL_PROVIDERS)
                    .setLogo(R.drawable.ic_logo)
                    .setPrivacyPolicyUrl("https://supercilex.github.io/Robot-Scouter/privacy-policy/")
                    .setIsAccountLinkingEnabled(true, AccountMergeService::class.java)
                    .build(),
            RC_SIGN_IN)

    private fun signInAnonymously() = signInAnonymouslyDbInit()
            .addOnFailureListener(activity) {
                Snackbar.make(rootView, R.string.anonymous_sign_in_failed, Snackbar.LENGTH_LONG)
                        .setAction(R.string.sign_in, this)
                        .show()
            }

    fun signOut() = AuthUI.getInstance()
            .signOut(activity)
            .addOnSuccessListener {
                FirebaseAuth.getInstance().signInAnonymously()
                FirebaseAppIndex.getInstance().removeAll()
            }
            .addOnSuccessListener(activity) { updateMenuState() }

    fun showSignInResolution() =
            Snackbar.make(rootView, R.string.sign_in_required, Snackbar.LENGTH_LONG)
                    .setAction(R.string.sign_in, this)
                    .show()

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RC_SIGN_IN) {
            val response: IdpResponse? = IdpResponse.fromResultIntent(data)

            if (resultCode == Activity.RESULT_OK) {
                Snackbar.make(rootView, R.string.signed_in, Snackbar.LENGTH_LONG).show()
                hasShownSignInTutorial = true
                updateMenuState()

                val user: FirebaseUser = user!!
                User(uid!!, user.email, user.displayName, user.photoUrl).add()

                logLoginEvent()
            } else {
                if (response == null) return // User cancelled sign in

                if (response.errorCode == ErrorCodes.NO_NETWORK) {
                    Snackbar.make(rootView, R.string.no_connection, Snackbar.LENGTH_LONG)
                            .setAction(R.string.try_again, this)
                            .show()
                    return
                }

                Snackbar.make(rootView, R.string.sign_in_failed, Snackbar.LENGTH_LONG)
                        .setAction(R.string.try_again, this)
                        .show()
            }
        }
    }

    override fun onClick(v: View) = signIn()

    private fun updateMenuState() {
        signInMenuItem.isVisible = !isFullUser
    }

    private companion object {
        const val RC_SIGN_IN = 100
    }
}
