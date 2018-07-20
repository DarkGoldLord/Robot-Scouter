package com.supercilex.robotscouter.server.functions

import com.supercilex.robotscouter.common.FIRESTORE_ACTIVE_TOKENS
import com.supercilex.robotscouter.common.FIRESTORE_MEDIA
import com.supercilex.robotscouter.common.FIRESTORE_METRICS
import com.supercilex.robotscouter.common.FIRESTORE_NAME
import com.supercilex.robotscouter.common.FIRESTORE_NUMBER
import com.supercilex.robotscouter.common.FIRESTORE_OWNERS
import com.supercilex.robotscouter.common.FIRESTORE_PREV_UID
import com.supercilex.robotscouter.common.FIRESTORE_REF
import com.supercilex.robotscouter.common.FIRESTORE_SCOUTS
import com.supercilex.robotscouter.common.FIRESTORE_TIMESTAMP
import com.supercilex.robotscouter.common.FIRESTORE_TOKEN
import com.supercilex.robotscouter.common.FIRESTORE_WEBSITE
import com.supercilex.robotscouter.common.isPolynomial
import com.supercilex.robotscouter.server.utils.FieldValue
import com.supercilex.robotscouter.server.utils.batch
import com.supercilex.robotscouter.server.utils.firestore
import com.supercilex.robotscouter.server.utils.teams
import com.supercilex.robotscouter.server.utils.toMap
import com.supercilex.robotscouter.server.utils.types.CallableContext
import com.supercilex.robotscouter.server.utils.types.Change
import com.supercilex.robotscouter.server.utils.types.DeltaDocumentSnapshot
import com.supercilex.robotscouter.server.utils.types.HttpsError
import kotlinx.coroutines.experimental.asPromise
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.await
import kotlinx.coroutines.experimental.awaitAll
import kotlin.js.Date
import kotlin.js.Json
import kotlin.js.Promise
import kotlin.js.json

fun updateOwners(data: Json, context: CallableContext): Promise<*>? {
    val auth = context.auth
    val token = data[FIRESTORE_TOKEN] as? String
    val path = data[FIRESTORE_REF] as? String
    val prevUid = data[FIRESTORE_PREV_UID]

    if (auth == null) throw HttpsError("unauthenticated")
    if (token == null || path == null) throw HttpsError("invalid-argument")
    if (prevUid != null) {
        if (prevUid !is String) {
            throw HttpsError("invalid-argument")
        } else if (prevUid == auth.uid) {
            throw HttpsError("already-exists", "Cannot add and remove the same user")
        }
    }
    prevUid as String?

    val value = run {
        val number = data[FIRESTORE_NUMBER] as? Number
        val timestamp = data[FIRESTORE_TIMESTAMP] as? Number

        @Suppress("IMPLICIT_CAST_TO_ANY")
        when {
            number != null -> number
            timestamp != null -> Date(timestamp)
            else -> throw HttpsError("invalid-argument")
        }
    }

    val ref = firestore.doc(path)
    val oldOwnerPath = prevUid?.let { "$FIRESTORE_OWNERS.$it" }
    val newOwnerPath = "$FIRESTORE_OWNERS.${auth.uid}"

    return async {
        val content = ref.get().await()

        if (!content.exists) throw HttpsError("not-found")
        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        if ((content.get(FIRESTORE_ACTIVE_TOKENS) as Json)[token] == null) {
            throw HttpsError("permission-denied", "Token $token is invalid for $path")
        }

        firestore.batch {
            oldOwnerPath?.let { update(ref, it, FieldValue.delete()) }
            update(ref, newOwnerPath, value)
        }
    }.asPromise()
}

fun mergeDuplicateTeams(event: Change<DeltaDocumentSnapshot>): Promise<*>? {
    val snapshot = event.after
    val uid = snapshot.id
    console.log("Checking for duplicate teams for $uid.")

    if (!snapshot.exists) return null
    val duplicates = snapshot.data().toMap<Long>().toList()
            .groupBy { it.second }
            .mapValues { it.value.map { it.first } }
            .filter { it.value.isPolynomial }
            .onEach { console.log("Found duplicate: $it") }
            .map { it.value }
    if (duplicates.isEmpty()) return null

    // TODO add migration function that adds stuff on addition
    // TODO migrate existing teams
    // TODO update duplicates on ownership change

    return async {
        duplicates.map {
            inner@ async {
                val teams = it.map { teams.doc(it) }
                        .map { async { it.get().await() } }
                        .awaitAll()
                        .associate { it to teams.doc(it.id).collection(FIRESTORE_SCOUTS).get() }
                        .mapValues { it.value.await().docs }
                        .mapValues { (_, scouts) ->
                            scouts.associate { it to it.ref.collection(FIRESTORE_METRICS).get() }
                                    .mapValues { it.value.await().docs }
                        }
                        .toList()
                        .sortedBy { it.first.data()[FIRESTORE_TIMESTAMP] as Long }

                val (keep) = teams.first()
                val merges = teams.subList(1, teams.size)
                for ((merge, scouts) in merges) {
                    val oldKeepData = keep.data().toMap<Any?>()
                    val newKeepData = oldKeepData.toMutableMap()
                    val mergeData = merge.data().toMap<Any?>()

                    fun mergeValue(name: String) {
                        if (newKeepData[name] == null) newKeepData[name] = mergeData[name]
                    }
                    mergeValue(FIRESTORE_NAME)
                    mergeValue(FIRESTORE_MEDIA)
                    mergeValue(FIRESTORE_WEBSITE)

                    if (newKeepData != oldKeepData) {
                        keep.ref.set(json(*newKeepData.toList().toTypedArray())).await()
                    }

                    firestore.batch {
                        for ((scout, metrics) in scouts) {
                            val ref = keep.ref.collection(FIRESTORE_SCOUTS).doc(scout.id)
                            set(ref, scout.data())
                            for (metric in metrics) {
                                set(ref.collection(FIRESTORE_METRICS).doc(metric.id), metric.data())
                            }
                        }
                    }
                }

                for ((merge) in merges) deleteTeam(merge)
            }
        }.awaitAll()
    }.asPromise()
}
