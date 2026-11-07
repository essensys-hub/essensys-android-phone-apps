package com.essensys.android.data.control

import com.essensys.android.data.auth.AuthRepository
import com.essensys.android.data.http.AlarmRequest
import com.essensys.android.data.http.ApiCaller
import com.essensys.android.data.http.ApiError
import com.essensys.android.data.http.ApiResult
import com.essensys.android.data.http.EssensysJson
import com.essensys.android.data.http.InjectResponse
import com.essensys.android.data.http.LightNameDto
import com.essensys.android.data.http.LightNamesResponse
import com.essensys.android.data.http.LightTimerRequest
import com.essensys.android.data.http.LightTimersResponse
import com.essensys.android.data.session.ConnectionMode
import com.essensys.android.data.session.SessionStore
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement

/** Modes actuels lus dans la table d'échange ; null = valeur non transmise par l'armoire. */
data class ComfortModes(val waterHeater: IndexTable.WaterHeaterMode?, val sprinklerMinutes: Int?) {
    /** Arrosage : 0 = arrêt, 255 = automatique, 1–254 = forcé N minutes (`Arrose_Mode`). */
    val sprinklerLabel: String? get() = when (val v = sprinklerMinutes) {
        null -> null
        0 -> "Arrêt"
        255 -> "Automatique"
        in 1..254 -> "Forcé $v minutes"
        else -> null
    }
}

/** Chauffe-eau, arrosage (specs comfort-controls), prises de sécurité (spec security-outlets) et alarme (spec alarm-control). */
class ComfortRepository(
    private val api: ApiCaller,
    private val store: SessionStore,
    private val auth: AuthRepository,
    private val control: ControlRepository,
) {
    suspend fun readModes(): ApiResult<ComfortModes> =
        when (val result = control.readExchange(listOf(IndexTable.WATER_HEATER_INDEX, IndexTable.SPRINKLER_INDEX))) {
            is ApiResult.Err -> result
            is ApiResult.Ok -> {
                val heater = result.value[IndexTable.WATER_HEATER_INDEX]?.trim()?.toIntOrNull()
                ApiResult.Ok(
                    ComfortModes(
                        waterHeater = IndexTable.WaterHeaterMode.entries.firstOrNull { it.value == heater },
                        sprinklerMinutes = result.value[IndexTable.SPRINKLER_INDEX]?.trim()?.toIntOrNull()?.takeIf { it in 0..255 },
                    ),
                )
            }
        }

    suspend fun setWaterHeater(mode: IndexTable.WaterHeaterMode) = control.inject(mode.command())

    suspend fun setSprinkler(mode: IndexTable.SprinklerMode) = control.inject(mode.command())

    /** Prises de sécurité (spec security-outlets) : état lu à l'indice 440, inconnu s'il est absent. */
    suspend fun readSecurityOutlets(): ApiResult<IndexTable.SecurityOutletsState> =
        when (val result = control.readExchange(listOf(IndexTable.SECURITY_OUTLETS_STATE_INDEX))) {
            is ApiResult.Err -> result
            is ApiResult.Ok -> ApiResult.Ok(IndexTable.SecurityOutletsState.of(result.value[IndexTable.SECURITY_OUTLETS_STATE_INDEX]))
        }

    /** Coupure ou rétablissement : un lot `[590 = 1, 623 = 1|2]`, en cloud comme en LAN. */
    suspend fun securityOutlets(action: IndexTable.SecurityOutletsAction) = control.injectBatch(action.commands())

    /**
     * Noms des lumières (spec light-names-clients) : cloud `GET /api/portal/lights/names`, LAN `GET /api/lights/names`.
     * Le résultat couvre toujours les 33 lumières : une lumière absente de la réponse garde son nom d'origine, une
     * lumière inconnue est ignorée. En cas d'échec, l'appelant garde [LightNames.fallback].
     */
    suspend fun readLightNames(): ApiResult<Map<String, LightName>> {
        val state = store.state.value
        val path = if (state.mode == ConnectionMode.CLOUD) "/api/portal/lights/names" else "/api/lights/names"
        return when (val result = api.get(state.baseUrl, path)) {
            is ApiResult.Err -> result.also { auth.onApiError(it.error) }
            is ApiResult.Ok -> {
                val response = runCatching { EssensysJson.decodeFromString(LightNamesResponse.serializer(), result.value.body) }
                    .getOrElse { return ApiResult.Err(ApiError.Http(result.value.status, null, null)) }
                val byId = response.lights.associateBy { it.id }
                ApiResult.Ok(LightNames.fallback().mapValues { (id, original) -> byId[id]?.toLightName(original) ?: original })
            }
        }
    }

    /**
     * Renomme une lumière (administrateur) : cloud `PUT /api/portal/admin/lights/names/{id}`, LAN
     * `PUT /api/admin/lights/names/{id}`, corps `{"name":"…"}` ; [name] nul = retour au nom d'origine (`{"name":null}`).
     * Le nom est validé avant l'envoi. Aucun ordre n'est envoyé à l'armoire.
     */
    suspend fun setLightName(id: String, name: String?): ApiResult<LightNameSaved> {
        val clean = if (name == null) null else LightNames.validate(name).getOrElse {
            return ApiResult.Err(ApiError.Http(400, "invalid_name", LightNames.INVALID))
        }
        val state = store.state.value
        val prefix = if (state.mode == ConnectionMode.CLOUD) "/api/portal/admin" else "/api/admin"
        // explicitNulls = false retirerait `"name":null` : le corps est construit à la main.
        val body = buildJsonObject { put("name", clean?.let(::JsonPrimitive) ?: JsonNull) }
        return when (val result = api.send(state.baseUrl, "PUT", "$prefix/lights/names/$id", body)) {
            is ApiResult.Err -> result.also { auth.onApiError(it.error) }
            is ApiResult.Ok -> {
                val dto = runCatching { EssensysJson.decodeFromString(LightNameDto.serializer(), result.value.body) }.getOrNull()
                val original = LightNames.fallback()[id]
                ApiResult.Ok(LightNameSaved(original?.let { dto?.toLightName(it) }, dryRun = dto?.status == "dry_run"))
            }
        }
    }

    /**
     * Temporisations des six lumières (spec light-timers-clients) : cloud `GET /api/portal/lights/timers`, LAN
     * `GET /api/admin/lights/timers`. Une valeur jamais lue (`null`) ou hors 0..255 reste inconnue, jamais 0.
     */
    suspend fun readLightTimers(): ApiResult<Map<LightTimer, Int?>> {
        val state = store.state.value
        val prefix = if (state.mode == ConnectionMode.CLOUD) "/api/portal" else "/api/admin"
        return when (val result = api.get(state.baseUrl, "$prefix/lights/timers")) {
            is ApiResult.Err -> result.also { auth.onApiError(it.error) }
            is ApiResult.Ok -> {
                val response = runCatching { EssensysJson.decodeFromString(LightTimersResponse.serializer(), result.value.body) }
                    .getOrElse { return ApiResult.Err(ApiError.Http(result.value.status, null, null)) }
                val byId = response.timers.associateBy { it.id }
                ApiResult.Ok(LightTimer.entries.associateWith { timer -> byId[timer.id]?.minutes?.takeIf(LightTimer::isValid) })
            }
        }
    }

    /**
     * Règle une temporisation : cloud `PUT /api/portal/admin/lights/timers/{id}`, LAN `PUT /api/admin/lights/timers/{id}`,
     * corps `{"minutes":n}`. La validation a lieu avant l'envoi ; le mode test répond `dry_run` (rien n'est mis en file).
     */
    suspend fun setLightTimer(timer: LightTimer, minutes: Int): ApiResult<InjectOutcome> {
        require(LightTimer.isValid(minutes)) { "temporisation : 0 à ${LightTimer.MAX_MINUTES} minutes" }
        val state = store.state.value
        val prefix = if (state.mode == ConnectionMode.CLOUD) "/api/portal/admin" else "/api/admin"
        val body = EssensysJson.encodeToJsonElement(LightTimerRequest(minutes))
        return when (val result = api.send(state.baseUrl, "PUT", "$prefix/lights/timers/${timer.id}", body)) {
            is ApiResult.Err -> result.also { auth.onApiError(it.error) }
            is ApiResult.Ok -> {
                val response = runCatching { EssensysJson.decodeFromString(InjectResponse.serializer(), result.value.body) }.getOrNull()
                val dryRun = response?.status == "dry_run" || response?.status == "test_ok" || response?.dryRun == true
                ApiResult.Ok(if (dryRun) InjectOutcome.DRY_RUN_OK else InjectOutcome.SENT)
            }
        }
    }

    /**
     * Mise en service ou arrêt de l'alarme, en cloud seulement. Le code n'est ni conservé ni renvoyé :
     * un message d'erreur serveur qui le contiendrait est masqué, et la réponse dry-run (qui le répète
     * dans `validated_params`) n'est réduite qu'à son statut.
     */
    suspend fun alarm(on: Boolean, code: String): ApiResult<InjectOutcome> {
        require(code.length == 4 && code.all { it.isDigit() }) { "code d'alarme : 4 chiffres" }
        val state = store.state.value
        if (state.mode != ConnectionMode.CLOUD) return ApiResult.Err(ApiError.CloudOnly)
        val body = EssensysJson.encodeToJsonElement(AlarmRequest(if (on) "on" else "off", code))
        return when (val result = api.send(state.baseUrl, "POST", "/api/portal/web/actions", body)) {
            is ApiResult.Err -> {
                auth.onApiError(result.error)
                ApiResult.Err(redact(result.error, code))
            }
            is ApiResult.Ok -> {
                val response = runCatching { EssensysJson.decodeFromString(InjectResponse.serializer(), result.value.body) }.getOrNull()
                ApiResult.Ok(if (response?.status == "test_ok" || response?.dryRun == true) InjectOutcome.DRY_RUN_OK else InjectOutcome.SENT)
            }
        }
    }

    private fun redact(error: ApiError, code: String): ApiError = when (error) {
        is ApiError.Http -> error.copy(text = error.text?.replace(code, "••••"), code = error.code?.replace(code, "••••"))
        is ApiError.Forbidden -> error.copy(text = error.text?.replace(code, "••••"))
        is ApiError.Unauthorized -> error.copy(text = error.text?.replace(code, "••••"))
        is ApiError.Network -> error.copy(cause = error.cause?.replace(code, "••••"))
        else -> error
    }
}

private fun LightNameDto.toLightName(original: LightName) =
    LightName(id, defaultName?.takeIf { it.isNotBlank() } ?: original.defaultName, name, version, updatedAt)
