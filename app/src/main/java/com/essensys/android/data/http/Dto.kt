package com.essensys.android.data.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Contrats lus dans essensys-user-portal-backend et essensys-server-backend (design D5 bis).

@Serializable data class LoginRequest(val email: String, val password: String)

@Serializable data class CloudUser(
    val id: Long? = null,
    val email: String? = null,
    val role: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
)

@Serializable data class CloudLoginResponse(
    val token: String,
    val user: CloudUser? = null,
    @SerialName("password_change_required") val passwordChangeRequired: Boolean = false,
)

@Serializable data class PasswordChangeRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable data class LanUser(
    val id: Long? = null,
    val email: String? = null,
    val role: String? = null,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable data class LanUserResponse(val user: LanUser? = null)

@Serializable data class InjectRequest(val k: Int, val v: String)

@Serializable data class KeyValue(val k: Int, val v: String)

@Serializable data class InjectResponse(
    val status: String? = null,
    @SerialName("dry_run") val dryRun: Boolean = false,
    val guid: String? = null,
    val message: String? = null,
)

@Serializable data class ExchangeResponse(
    val values: List<KeyValue> = emptyList(),
    val stale: Boolean = false,
    val source: String? = null,
)

@Serializable data class InjectBatchRequest(val params: List<KeyValue>)

// Synchronisation du planning de chauffage, LAN seulement (essensys-server-backend handlers.go).
@Serializable data class HeatingSyncRequest(val startIndex: Int, val byteCount: Int)

@Serializable data class HeatingSyncStatus(
    val active: Boolean = false,
    val received: Int = 0,
    val total: Int = 0,
    val chunksTotal: Int = 0,
    val chunksCompleted: Int = 0,
)

@Serializable data class HeatingSyncStarted(val status: String? = null, val sync: HeatingSyncStatus? = null)

// Scénarios : cloud /api/portal/scenarios*, LAN /api/scenarios* (même format, handlers/scenarios.go).
@Serializable data class ScenarioSlot(
    @SerialName("slot_number") val slot: Int,
    val label: String = "",
    @SerialName("base_index") val baseIndex: Int = 0,
    @SerialName("end_index") val endIndex: Int = 0,
    val editable: Boolean = false,
    @SerialName("last_launched") val lastLaunched: Int? = null,
    val params: List<KeyValue> = emptyList(),
)

@Serializable data class ScenarioList(val slots: List<ScenarioSlot> = emptyList())

@Serializable data class BitmaskBit(val bit: Int, val value: Int, val label: String)

@Serializable data class BitmaskField(val index: Int, val name: String = "", val bits: List<BitmaskBit> = emptyList())

@Serializable data class BitmaskMeta(val fields: List<BitmaskField> = emptyList())

@Serializable data class ScenarioSaveRequest(val params: Map<String, String>)

@Serializable data class LinkRequest(
    val id: Long? = null,
    @SerialName("machine_serial") val machineSerial: String? = null,
    val message: String? = null,
    val status: String? = null,
)

@Serializable data class LinkStatusResponse(
    val status: String? = null,
    @SerialName("link_request") val linkRequest: LinkRequest? = null,
    @SerialName("portal_access") val portalAccess: Boolean = false,
)

@Serializable data class LinkRequestBody(
    @SerialName("machine_serial") val machineSerial: String,
    val message: String,
)

@Serializable data class GatewayInfo(val id: String? = null, val hostname: String? = null, val online: Boolean = false)

@Serializable data class PortalSession(
    val user: CloudUser? = null,
    @SerialName("portal_access") val portalAccess: Boolean = false,
    val gateway: GatewayInfo? = null,
)

@Serializable data class LastAction(
    val guid: String? = null,
    val actionInfo: String? = null,
    val isDone: Boolean = true,
    val timestamp: String? = null,
)

@Serializable data class HistoryLatest(val lastAction: LastAction? = null)

/** Alarme (cloud `POST /api/portal/web/actions`) : le code n'est jamais stocké ni journalisé par l'app. */
@Serializable data class AlarmRequest(val alarme: String, val codealarme: String) {
    override fun toString() = "AlarmRequest(alarme=$alarme, codealarme=••••)"
}
