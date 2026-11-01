package com.aliuppal.pockethamster.wellness

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reads today's step count from Health Connect. Only step totals are read; nothing is written. */
object StepsSync {
    val PERMISSIONS = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    fun available(context: Context) =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    suspend fun hasPermission(context: Context): Boolean = available(context) &&
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    /** Syncs into [Wellness] when allowed. Returns false if Health Connect isn't set up. */
    suspend fun sync(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val result = HealthConnectClient.getOrCreate(context).aggregate(
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, Instant.now()))
        )
        Wellness.updateSteps(result[StepsRecord.COUNT_TOTAL] ?: 0L)
        return true
    }
}
