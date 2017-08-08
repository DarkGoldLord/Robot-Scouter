package com.supercilex.robotscouter.util.ui

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.os.Handler
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.supercilex.robotscouter.R
import com.supercilex.robotscouter.ui.teamlist.TutorialHelper
import com.supercilex.robotscouter.util.data.hasShownAddTeamTutorial
import com.supercilex.robotscouter.util.data.hasShownSignInTutorial
import uk.co.samuelwall.materialtaptargetprompt.ActivityResourceFinder
import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt

fun showAddTeamTutorial(helper: TutorialHelper, owner: LifecycleActivity) {
    helper.hasShownAddTeamTutorial.observe(owner, object : Observer<Boolean?> {
        private val prompt = MaterialTapTargetPrompt.Builder(object : ActivityResourceFinder(owner) {
            override fun getPromptParentView(): ViewGroup = owner.findViewById(R.id.root)
        }, R.style.RobotScouter_Tutorial)
                .setTarget(R.id.fab)
                .setPrimaryText(R.string.create_first_team)
                .setAutoDismiss(false)
                .setPromptStateChangeListener { _, state ->
                    runIfPressed(state) { hasShownAddTeamTutorial = true }
                }
                .create()

        override fun onChanged(hasShownTutorial: Boolean?) {
            if (hasShownTutorial == false) {
                if (owner.findViewById<View?>(R.id.material_target_prompt_view)?.visibility == View.VISIBLE) return

                prompt.show()
                val promptView: View = owner.findViewById(R.id.material_target_prompt_view)
                (promptView.layoutParams as CoordinatorLayout.LayoutParams).behavior =
                        PromptTouchEventForwarder(promptView)
            } else {
                prompt.dismiss()
            }
        }
    })
}

fun showSignInTutorial(helper: TutorialHelper, owner: LifecycleActivity) = Handler().post {
    helper.hasShownSignInTutorial.observe(owner, object : Observer<Boolean?> {
        private val prompt: MaterialTapTargetPrompt?
            get() = MaterialTapTargetPrompt.Builder(owner, R.style.RobotScouter_Tutorial_Menu)
                    .setTarget(R.id.action_sign_in)
                    .setPrimaryText(R.string.sign_in)
                    .setSecondaryText(R.string.sign_in_rationale)
                    .setPromptStateChangeListener { _, state ->
                        runIfPressed(state) { hasShownSignInTutorial = true }
                    }
                    .create()

        override fun onChanged(hasShownTutorial: Boolean?) {
            if (hasShownAddTeamTutorial && hasShownTutorial == false) prompt?.show()
            else prompt?.dismiss()
        }
    })
}

private inline fun runIfPressed(state: Int, block: () -> Unit) {
    if (state == MaterialTapTargetPrompt.STATE_FOCAL_PRESSED) block()
}

class UniqueMutableLiveData<T> : MutableLiveData<T>() {
    override fun postValue(value: T) {
        if (this.value != value) {
            super.postValue(value)
        }
    }

    override fun setValue(value: T) {
        if (this.value != value) {
            super.setValue(value)
        }
    }
}

private class PromptTouchEventForwarder(private val prompt: View) : CoordinatorLayout.Behavior<View>() {
    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View) =
            dependency is FloatingActionButton

    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: View, event: MotionEvent) =
            prompt.onTouchEvent(event)
}
