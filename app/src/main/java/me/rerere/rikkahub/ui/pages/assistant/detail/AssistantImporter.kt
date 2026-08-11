package me.rerere.rikkahub.ui.pages.assistant.detail

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.dokar.sonner.ToastType
import com.dokar.sonner.ToasterState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import kotlin.uuid.Uuid
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.rikkahub.R
import org.koin.compose.koinInject

@Composable
fun AssistantImporter(
    modifier: Modifier = Modifier,
    onUpdate: (Assistant) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        SillyTavernImporter(onImport = onUpdate)
    }
}

@Composable
private fun SillyTavernImporter(
    onImport: (Assistant) -> Unit
) {
    val context = LocalContext.current
    val filesManager: FilesManager = koinInject()
    val preferencesStore: SettingsStore = koinInject()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var isLoading by remember { mutableStateOf(false) }

    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isLoading = true
            scope.launch {
                try {
                    runCatching {
                        importAssistantFromUri(
                            context = context,
                            uri = uri,
                            onImport = onImport,
                            toaster = toaster,
                            filesManager = filesManager,
                            preferencesStore = preferencesStore,
                        )
                    }.onFailure { exception ->
                        exception.printStackTrace()
                        toaster.show(exception.message ?: context.getString(R.string.assistant_importer_import_failed))
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val pngPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isLoading = true
            scope.launch {
                try {
                    runCatching {
                        importAssistantFromUri(
                            context = context,
                            uri = uri,
                            onImport = onImport,
                            toaster = toaster,
                            filesManager = filesManager,
                            preferencesStore = preferencesStore,
                        )
                    }.onFailure { exception ->
                        exception.printStackTrace()
                        toaster.show(exception.message ?: context.getString(R.string.assistant_importer_import_failed))
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                pngPickerLauncher.launch(arrayOf("image/png"))
            },
            enabled = !isLoading
        ) {
            AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
            Text(text = if (isLoading) stringResource(R.string.assistant_importer_importing) else stringResource(R.string.assistant_importer_import_tavern_png))
        }

        OutlinedButton(
            onClick = {
                jsonPickerLauncher.launch(arrayOf("application/json"))
            },
            enabled = !isLoading
        ) {
            AutoAIIcon(name = "tavern", modifier = Modifier.padding(end = 8.dp))
            Text(text = if (isLoading) stringResource(R.string.assistant_importer_importing) else stringResource(R.string.assistant_importer_import_tavern_json))
        }
    }
}

// region Parsing Strategy

private interface TavernCardParser {
    val specName: String
    fun parse(context: Context, json: JsonObject, background: String?): Assistant
}

private class CharaCardV2Parser : TavernCardParser {
    override val specName: String = "chara_card_v2"

    override fun parse(context: Context, json: JsonObject, background: String?): Assistant {
        val data = json["data"]?.jsonObject ?: error(context.getString(R.string.assistant_importer_missing_data_field))
        val name = data["name"]?.jsonPrimitiveOrNull?.contentOrNull
            ?: error(context.getString(R.string.assistant_importer_missing_name_field))
        val firstMessage = data["first_mes"]?.jsonPrimitiveOrNull?.contentOrNull
        val system = data["system_prompt"]?.jsonPrimitiveOrNull?.contentOrNull
        val description = data["description"]?.jsonPrimitiveOrNull?.contentOrNull
        val personality = data["personality"]?.jsonPrimitiveOrNull?.contentOrNull
        val scenario = data["scenario"]?.jsonPrimitiveOrNull?.contentOrNull

        val prompt = buildString {
            appendLine("You are roleplaying as $name.")
            appendLine()
            if (!system.isNullOrBlank()) {
                appendLine(system)
                appendLine()
            }
            appendLine("## Description of the character")
            appendLine(description ?: "Empty")
            appendLine()
            appendLine("## Personality of the character")
            appendLine(personality ?: "Empty")
            appendLine()
            appendLine("## Scenario")
            append(scenario ?: "Empty")
        }

        return Assistant(
            name = name,
            presetMessages = if (firstMessage != null) listOf(UIMessage.assistant(firstMessage)) else emptyList(),
            systemPrompt = prompt,
            background = background
        )
    }
}

private class CharaCardV3Parser : TavernCardParser {
    override val specName: String = "chara_card_v3"

    override fun parse(context: Context, json: JsonObject, background: String?): Assistant {
        val data = json["data"]?.jsonObject ?: error(context.getString(R.string.assistant_importer_missing_data_field))
        val name = data["name"]?.jsonPrimitiveOrNull?.contentOrNull ?: error(context.getString(R.string.assistant_importer_missing_name_field))
        val description = data["description"]?.jsonPrimitiveOrNull?.contentOrNull
        val firstMessage = data["first_mes"]?.jsonPrimitiveOrNull?.contentOrNull
        val system = data["system_prompt"]?.jsonPrimitiveOrNull?.contentOrNull
        val personality = data["personality"]?.jsonPrimitiveOrNull?.contentOrNull
        val scenario = data["scenario"]?.jsonPrimitiveOrNull?.contentOrNull

        val prompt = buildString {
            appendLine("You are roleplaying as $name.")
            appendLine()
            if (!system.isNullOrBlank()) {
                appendLine(system)
                appendLine()
            }
            appendLine("## Description of the character")
            appendLine(description ?: "Empty")
            appendLine()
            appendLine("## Personality of the character")
            appendLine(personality ?: "Empty")
            appendLine()
            appendLine("## Scenario")
            append(scenario ?: "Empty")
        }

        return Assistant(
            name = name,
            presetMessages = if (firstMessage != null) listOf(UIMessage.assistant(firstMessage)) else emptyList(),
            systemPrompt = prompt,
            background = background
        )
    }
}

private val TAVERN_PARSERS: Map<String, TavernCardParser> = listOf(
    CharaCardV2Parser(),
    CharaCardV3Parser()
).associateBy { it.specName }

private fun parseAssistantFromJson(
    context: Context,
    json: JsonObject,
    background: String?,
): Assistant {
    val spec = json["spec"]?.jsonPrimitive?.contentOrNull
        ?: error(context.getString(R.string.assistant_importer_missing_spec_field))
    val parser = TAVERN_PARSERS[spec] ?: error(context.getString(R.string.assistant_importer_unsupported_spec, spec))
    return parser.parse(context = context, json = json, background = background)
}

// endregion

/* 解析酒馆卡 character_book → Lorebook（V2 数组 / V3 {entries} 对象） */
private fun kotlinx.serialization.json.JsonObject.primStr(name: String): String? = this[name]?.jsonPrimitiveOrNull?.contentOrNull
private fun kotlinx.serialization.json.JsonObject.primBool(name: String): Boolean = this[name]?.jsonPrimitiveOrNull?.contentOrNull?.toBoolean() ?: false
private fun kotlinx.serialization.json.JsonObject.primInt(name: String): Int = this[name]?.jsonPrimitiveOrNull?.contentOrNull?.toIntOrNull() ?: 0

private fun parseCharacterBook(json: kotlinx.serialization.json.JsonObject, cardName: String): Lorebook? {
    val data = json["data"]?.jsonObject ?: return null
    val cb = data["character_book"] ?: data["world_book"] ?: return null
    val entriesJson = when {
        cb is kotlinx.serialization.json.JsonArray -> cb
        else -> cb.jsonObject?.get("entries") as? kotlinx.serialization.json.JsonArray
    } ?: return null
    if (entriesJson.isEmpty()) return null

    val entries = entriesJson.mapNotNull { el ->
        val e = el.jsonObject
        val content = e.primStr("content") ?: e.primStr("text") ?: return@mapNotNull null
        val keys = (e["keys"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull } ?: emptyList()
        val posStr = e.primStr("position")
        val position = when {
            posStr == "before_system_prompt" -> InjectionPosition.BEFORE_SYSTEM_PROMPT
            posStr == "top_of_chat" -> InjectionPosition.TOP_OF_CHAT
            posStr == "bottom_of_chat" -> InjectionPosition.BOTTOM_OF_CHAT
            posStr == "at_depth" -> InjectionPosition.AT_DEPTH
            else -> InjectionPosition.AFTER_SYSTEM_PROMPT
        }
        val ex = e["extensions"]?.jsonObject
        val constant = e["constant"]?.jsonPrimitiveOrNull?.booleanOrNull
            ?: e["constantActive"]?.jsonPrimitiveOrNull?.booleanOrNull
            ?: ex?.get("constant")?.jsonPrimitiveOrNull?.booleanOrNull
            ?: false
        PromptInjection.RegexInjection(
            id = Uuid.random(),
            name = e.primStr("name") ?: "",
            enabled = !(e.primStr("enabled") == "false"),
            priority = e.primInt("priority").takeIf { it != 0 } ?: e.primInt("insertion_order"),
            position = position,
            content = content,
            injectDepth = e.primInt("injectDepth").takeIf { it != 0 } ?: ex?.primInt("depth") ?: 4,
            role = if (e.primStr("role") == "assistant") MessageRole.ASSISTANT else MessageRole.USER,
            keywords = keys,
            useRegex = e.primBool("useRegex") || ex?.primBool("regex") == true,
            caseSensitive = e.primBool("caseSensitive") || ex?.primBool("case_sensitive") == true,
            scanDepth = e.primInt("scanDepth").takeIf { it != 0 } ?: e.primInt("scan_depth").takeIf { it != 0 } ?: 4,
            constantActive = constant
        )
    }
    if (entries.isEmpty()) return null
    return Lorebook(
        name = "$cardName 世界书",
        entries = entries
    )
}

private suspend fun importAssistantFromUri(
    context: Context,
    uri: Uri,
    onImport: (Assistant) -> Unit,
    toaster: ToasterState,
    filesManager: FilesManager,
    preferencesStore: SettingsStore,
) {
    try {
        val mime = withContext(Dispatchers.IO) { filesManager.getFileMimeType(uri) }
        val (jsonString, backgroundStr) = withContext(Dispatchers.IO) {
            when (mime) {
                "image/png" -> {
                    val result = ImageUtils.getTavernCharacterMeta(context, uri)
                    result.map { base64Data ->
                        val json = String(Base64.decode(base64Data, Base64.DEFAULT))
                        val bg = filesManager.createChatFilesByContents(listOf(uri)).first().toString()
                        json to bg
                    }.getOrElse { throw it }
                }

                "application/json" -> {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()
                        .use { it?.readText() }
                        ?: error(context.getString(R.string.assistant_importer_read_json_failed))
                    json to null
                }

                else -> error(context.getString(R.string.assistant_importer_unsupported_file_type, mime ?: "unknown"))
            }
        }
        val json = Json.parseToJsonElement(jsonString).jsonObject
        var assistant = parseAssistantFromJson(context = context, json = json, background = backgroundStr)
        // 自动解析卡内世界书（character_book）→ 创建 Lorebook 并关联到卡
        val lorebook = parseCharacterBook(json, assistant.name)
        if (lorebook != null) {
            preferencesStore.update { s -> s.copy(lorebooks = s.lorebooks + lorebook) }
            assistant = assistant.copy(lorebookIds = assistant.lorebookIds + lorebook.id)
        }
        onImport(assistant)
    } catch (exception: Exception) {
        exception.printStackTrace()
        toaster.show(
            message = exception.message ?: context.getString(R.string.assistant_importer_import_failed),
            type = ToastType.Error
        )
    }
}
