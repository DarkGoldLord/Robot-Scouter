package com.supercilex.robotscouter.feature.exports

import android.Manifest
import android.app.IntentService
import android.content.Intent
import android.support.annotation.RequiresPermission
import android.support.v4.app.FragmentActivity
import android.support.v4.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.supercilex.robotscouter.Bridge
import com.supercilex.robotscouter.ExportServiceCompanion
import com.supercilex.robotscouter.core.CrashLogger
import com.supercilex.robotscouter.core.RobotScouter
import com.supercilex.robotscouter.core.asTask
import com.supercilex.robotscouter.core.await
import com.supercilex.robotscouter.core.data.exportsFolder
import com.supercilex.robotscouter.core.data.fetchAndActivateTask
import com.supercilex.robotscouter.core.data.getTeamListExtra
import com.supercilex.robotscouter.core.data.logExport
import com.supercilex.robotscouter.core.data.model.getScouts
import com.supercilex.robotscouter.core.data.model.getTemplatesQuery
import com.supercilex.robotscouter.core.data.model.scoutParser
import com.supercilex.robotscouter.core.data.putExtra
import com.supercilex.robotscouter.core.data.shouldShowRatingDialog
import com.supercilex.robotscouter.core.data.teams
import com.supercilex.robotscouter.core.data.waitForChange
import com.supercilex.robotscouter.core.fastAddOnSuccessListener
import com.supercilex.robotscouter.core.isOffline
import com.supercilex.robotscouter.core.isOnline
import com.supercilex.robotscouter.core.logFailures
import com.supercilex.robotscouter.core.model.Scout
import com.supercilex.robotscouter.core.model.Team
import com.supercilex.robotscouter.core.model.TemplateType
import com.supercilex.robotscouter.shared.PermissionRequestHandler
import com.supercilex.robotscouter.shared.RatingDialog
import kotlinx.coroutines.experimental.CancellationException
import kotlinx.coroutines.experimental.TimeoutCancellationException
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.awaitAll
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.withTimeout
import org.jetbrains.anko.design.snackbar
import org.jetbrains.anko.find
import org.jetbrains.anko.intentFor
import pub.devrel.easypermissions.EasyPermissions
import java.io.File
import java.util.concurrent.TimeUnit
import com.supercilex.robotscouter.R as RC

@Bridge
class ExportService : IntentService(TAG) {
    init {
        setIntentRedelivery(true)
    }

    @RequiresPermission(value = Manifest.permission.WRITE_EXTERNAL_STORAGE)
    override fun onHandleIntent(intent: Intent) {
        val notificationManager = ExportNotificationManager(this)

        if (isOffline) showToast(getString(R.string.export_offline_rationale))

        val teams: List<Team> = intent.getTeamListExtra().toMutableList().apply { sort() }
        val chunks = teams.chunked(SYNCHRONOUS_QUERY_CHUNK)
        notificationManager.startLoading(chunks.size)

        try {
            onHandleScouts(notificationManager, chunks.map {
                notificationManager.loading(it)

                runBlocking {
                    withTimeout(TIMEOUT, TimeUnit.MINUTES) {
                        it.map { async { it.getScouts() } }.awaitAll()
                    }
                }.also { notificationManager.updateLoadProgress() }
            }.flatten().withIndex().associate {
                teams[it.index] to it.value
            })
        } catch (t: Throwable) {
            notificationManager.abortCritical(t)
        }
    }

    private fun onHandleScouts(
            notificationManager: ExportNotificationManager,
            newScouts: Map<Team, List<Scout>>
    ) {
        if (newScouts.values.all { it.isEmpty() }) {
            notificationManager.stopEmpty()
            return
        }

        val zippedScouts = zipScouts(newScouts)
        val exportFolder = File(checkNotNull(exportsFolder) {
            "Couldn't get write access"
        }, "Robot Scouter export_${System.currentTimeMillis()}")

        notificationManager.setData(zippedScouts.size, newScouts.keys, exportFolder)

        runBlocking {
            val templateNames = getTemplateNames(zippedScouts.keys)
            withTimeout(TIMEOUT, TimeUnit.MINUTES) {
                zippedScouts.map { (templateId, scouts) ->
                    async {
                        if (!notificationManager.isStopped()) {
                            try {
                                TemplateExporter(
                                        scouts,
                                        this@ExportService,
                                        notificationManager,
                                        exportFolder,
                                        templateNames[templateId]
                                ).export()
                            } catch (t: Throwable) {
                                notificationManager.abortCritical(t)
                                throw CancellationException()
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun getTemplateNames(templateIds: Set<String>): Map<String, String?> {
        val unknownTemplateName: String = getString(R.string.export_unknown_template_title)

        val templatesSnapshot: List<DocumentSnapshot> = try {
            getTemplatesQuery().get().await().documents
        } catch (e: Exception) {
            CrashLogger.onFailure(e)
            emptyList()
        }
        val allPossibleTemplateNames: Map<String, String?> = templatesSnapshot.associate {
            scoutParser.parseSnapshot(it).let { it.id to it.name }
        }.toMutableMap().apply {
            putAll(TemplateType.values.associate {
                it.id.toString() to resources.getStringArray(RC.array.template_new_options)[it.id]
            })
        }

        val usedTemplates = mutableMapOf<String, Int>()
        return templateIds.associate {
            if (allPossibleTemplateNames.contains(it)) {
                it to allPossibleTemplateNames[it]
            } else {
                // User deleted template
                it to unknownTemplateName
            }
        }.mapValues { (_, name) ->
            if (name == null) return@mapValues null
            usedTemplates[name]?.let {
                usedTemplates[name] = it + 1
                "$name ($it)"
            } ?: run {
                usedTemplates[name] = 1
                name
            }
        }
    }

    private fun zipScouts(map: Map<Team, List<Scout>>): Map<String, Map<Team, List<Scout>>> {
        val zippedScouts = mutableMapOf<String, MutableMap<Team, MutableList<Scout>>>()
        for ((team, scouts) in map) {
            for (scout in scouts) {
                zippedScouts.getOrPut(scout.templateId) {
                    mutableMapOf()
                }.getOrPut(team) {
                    mutableListOf()
                }.add(scout)
            }
        }
        return zippedScouts
    }

    private fun ExportNotificationManager.abortCritical(t: Throwable) {
        if (t !is TimeoutCancellationException) CrashLogger(t)
        abort()
        if (t !is CancellationException) showToast("${getString(RC.string.error_unknown)}\n\n$t")
    }

    companion object : ExportServiceCompanion {
        private const val TAG = "ExportService"
        private const val SYNCHRONOUS_QUERY_CHUNK = 10
        private const val TIMEOUT = 10L

        private const val MIN_TEAMS_TO_RATE = 10

        override fun exportAndShareSpreadSheet(
                activity: FragmentActivity,
                permHandler: PermissionRequestHandler,
                teams: List<Team>
        ): Boolean {
            if (!EasyPermissions.hasPermissions(activity, *permHandler.perms.toTypedArray())) {
                permHandler.requestPerms(activity, R.string.export_write_storage_rationale)
                return false
            }

            snackbar(activity.find(RC.id.root), R.string.export_progress_hint)

            if (teams.isEmpty()) {
                getAllTeams()
            } else {
                Tasks.forResult(teams.toList())
            }.fastAddOnSuccessListener { exportedTeams ->
                exportedTeams.logExport()
                ContextCompat.startForegroundService(
                        RobotScouter,
                        RobotScouter.intentFor<ExportService>().putExtra(exportedTeams)
                )
            }.continueWithTask {
                if (it.result.size >= MIN_TEAMS_TO_RATE && isOnline) {
                    fetchAndActivateTask().logFailures()
                } else {
                    Tasks.forResult<Unit>(null)
                }
            }.addOnSuccessListener(activity) {
                if (it != null && shouldShowRatingDialog) {
                    RatingDialog.show(activity.supportFragmentManager)
                }
            }

            return true
        }

        private fun getAllTeams() = async { teams.waitForChange() }.asTask()
    }
}