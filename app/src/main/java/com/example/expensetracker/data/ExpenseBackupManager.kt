package com.example.expensetracker.data

import android.content.Context
import android.net.Uri
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.CodingErrorAction
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Locale
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Il formato JSON riguarda solo le spese, non le preferenze né gli allegati fisici. */
data class ExpenseBackupPreview(
    val expenses: List<ExpenseEntity>,
    val exportedAt: String,
    val recurringRules: List<RecurringRule> = emptyList(),
    val customCategories: List<CustomCategory> = emptyList(),
    val formatVersion: Int = 1
)

/** Export/import locale tramite URI del selettore file di Android (Storage Access Framework). */
object ExpenseBackupManager {
    private const val APPLICATION_ID = "com.example.expensetracker"
    private const val FORMAT_VERSION = 6
    private const val MAX_FILE_BYTES = 20_000_000
    private const val MAX_EXPENSES = 50_000

    suspend fun exportToUri(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val expenses = AppDatabase.getDatabase(context).expenseDao()
            .observeAllExpenses().first()

        val rules = AppDatabase.getDatabase(context).expenseDao().getRecurringRules()
        val categories = AppDatabase.getDatabase(context).expenseDao().getCustomCategories()
        val items = JSONArray()
        expenses.forEach { expense ->
            val entry = JSONObject().apply {
                put("id", expense.id)
                put("amountCents", expense.amountCents)
                put("category", expense.category)
                put("dateEpochDay", expense.dateEpochDay)
                put("merchant", expense.merchant)
                put("paymentMethod", expense.paymentMethod)
                put("note", expense.note)
                put("isRecurring", expense.isRecurring)
                put("receiptUri", expense.receiptUri ?: JSONObject.NULL)
                put("recurringSourceId", expense.recurringSourceId ?: JSONObject.NULL)
                put("recurringOccurrenceIndex", expense.recurringOccurrenceIndex ?: JSONObject.NULL)
            }
            items.put(entry)
        }

        val ruleItems = JSONArray()
        rules.forEach { rule ->
            ruleItems.put(JSONObject().apply {
                put("sourceExpenseId", rule.sourceExpenseId)
                put("intervalCode", rule.intervalCode)
                put("firstDateEpochDay", rule.firstDateEpochDay)
                put("nextIndex", rule.nextIndex)
                put("expiryAfterMonths", rule.expiryAfterMonths ?: JSONObject.NULL)
                put("endDateEpochDay", rule.endDateEpochDay ?: JSONObject.NULL)
            })
        }
        val categoryItems = JSONArray()
        categories.forEach { category ->
            categoryItems.put(JSONObject().apply {
                put("name", category.name)
                put("colorArgb", category.colorArgb)
                put("iconKey", category.iconKey)
                put("isArchived", category.isArchived)
            })
        }
        val payload = JSONObject().apply {
            put("application", APPLICATION_ID)
            put("formatVersion", FORMAT_VERSION)
            put("exportedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            put("expenses", items)
            put("recurringRules", ruleItems)
            put("customCategories", categoryItems)
        }.toString(2)

        // "wt" = scrivi e tronca: non lascia dati di un eventuale file precedente.
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Impossibile aprire il file di destinazione")
        output.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
        expenses.size
    }

    suspend fun inspectUri(context: Context, uri: Uri): ExpenseBackupPreview =
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Impossibile aprire il file selezionato")
            val bytes = input.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count = 0
                while (true) {
                    val n = stream.read(buffer)
                    if (n == -1) break
                    count += n
                    if (count > MAX_FILE_BYTES) {
                        throw IllegalArgumentException("Backup troppo grande (massimo 20 MB)")
                    }
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            val decoded = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
            val root = JSONObject(decoded)

            val backupVersion = root.optInt("formatVersion", -1)
            if (root.optString("application") != APPLICATION_ID ||
                backupVersion !in 1..FORMAT_VERSION
            ) {
                throw IllegalArgumentException("Il file non è un backup ExpenseTracker compatibile")
            }

            val exportedAt = root.getString("exportedAt")
            OffsetDateTime.parse(exportedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val entries = root.getJSONArray("expenses")
            if (entries.length() > MAX_EXPENSES) {
                throw IllegalArgumentException("Il backup contiene troppe spese")
            }
            val ids = HashSet<Long>()
            val expenses = ArrayList<ExpenseEntity>(entries.length())
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                val id = item.strictLong("id")
                val cents = item.strictLong("amountCents")
                val epochDay = item.strictLong("dateEpochDay")
                val category = item.getString("category")
                val merchant = item.getString("merchant")
                val method = item.getString("paymentMethod")
                val note = item.getString("note")
                val recurring = item.get("isRecurring")
                val receipt = if (item.isNull("receiptUri")) null
                else item.getString("receiptUri")

                require(id > 0 && ids.add(id)) { "ID di spesa non valido o ripetuto" }
                require(cents > 0) { "Importo non valido" }
                LocalDate.ofEpochDay(epochDay) // Verifica la data senza alterarla.
                require(category.isNotBlank() && category.length <= 120) {
                    "Categoria non valida"
                }
                require(merchant.length <= 1000 && note.length <= 5000 &&
                        method.isNotBlank() && method.length <= 120 &&
                        (receipt == null || receipt.length <= 4000)
                ) { "Testo troppo lungo o metodo di pagamento non valido" }
                require(recurring is Boolean) { "Valore di ricorrenza non valido" }
                val sourceId = if (backupVersion >= 5 &&
                    !item.isNull("recurringSourceId")) item.strictLong("recurringSourceId") else null
                val occurrenceIndex = if (backupVersion >= 5 &&
                    !item.isNull("recurringOccurrenceIndex"))
                    item.strictLong("recurringOccurrenceIndex") else null
                require((sourceId == null) == (occurrenceIndex == null)) {
                    "Riferimento di ricorrenza incompleto"
                }
                require(sourceId == null || (sourceId > 0 && occurrenceIndex!! >= 1 && !recurring)) {
                    "Riferimento di ricorrenza non valido"
                }

                expenses.add(
                    ExpenseEntity(
                        id = id,
                        amountCents = cents,
                        category = category,
                        dateEpochDay = epochDay,
                        merchant = merchant,
                        paymentMethod = method,
                        note = note,
                        isRecurring = recurring,
                        receiptUri = receipt,
                        recurringSourceId = sourceId,
                        recurringOccurrenceIndex = occurrenceIndex
                    )
                )
            }
            val occurrenceKeys = HashSet<Pair<Long, Long>>()
            for (expense in expenses) {
                if (expense.recurringSourceId != null) {
                    require(occurrenceKeys.add(
                        expense.recurringSourceId to expense.recurringOccurrenceIndex!!
                    )) { "Occorrenza ricorrente duplicata nel backup" }
                }
            }
            val recurringRules = ArrayList<RecurringRule>()
            if (backupVersion >= 2) {
                val scheduleItems = root.getJSONArray("recurringRules")
                require(scheduleItems.length() <= MAX_EXPENSES) {
                    "Il backup contiene troppe regole di ricorrenza"
                }
                val expenseById = expenses.associateBy { it.id }
                val seenSources = HashSet<Long>()
                for (i in 0 until scheduleItems.length()) {
                    val item = scheduleItems.getJSONObject(i)
                    val sourceId = item.strictLong("sourceExpenseId")
                    val code = item.getString("intervalCode")
                    val firstDay = item.strictLong("firstDateEpochDay")
                    val nextIndex = item.strictLong("nextIndex")
                    require(sourceId > 0 && seenSources.add(sourceId)) {
                        "Regola di ricorrenza duplicata"
                    }
                    require(expenseById[sourceId]?.isRecurring == true) {
                        "Spesa originale della ricorrenza mancante o non valida"
                    }
                    RecurrenceInterval.valueOf(code)
                    LocalDate.ofEpochDay(firstDay)
                    require(nextIndex >= 1) { "Indice di ricorrenza non valido" }
                    val expiryMonths = if (backupVersion >= 6 &&
                        !item.isNull("expiryAfterMonths")) {
                        item.strictLong("expiryAfterMonths").also {
                            require(it in 1L..1200L) { "Durata della ricorrenza non valida" }
                        }.toInt()
                    } else null
                    val expiryDay = if (backupVersion >= 6 &&
                        !item.isNull("endDateEpochDay")) {
                        item.strictLong("endDateEpochDay")
                    } else null
                    require(expiryMonths == null || expiryDay == null) {
                        "Sono presenti due scadenze per la stessa ricorrenza"
                    }
                    if (expiryDay != null) {
                        val end = LocalDate.ofEpochDay(expiryDay)
                        val start = LocalDate.ofEpochDay(firstDay)
                        require(!end.isBefore(start) && !end.isAfter(start.plusYears(100))) {
                            "Data finale della ricorrenza non valida"
                        }
                    }
                    recurringRules.add(
                        RecurringRule(sourceId, code, firstDay, nextIndex,
                            expiryMonths, expiryDay)
                    )
                }
            }
            val customCategories = ArrayList<CustomCategory>()
            if (backupVersion >= 3) {
                val categoryItems = root.getJSONArray("customCategories")
                require(categoryItems.length() <= 500) {
                    "Il backup contiene troppe categorie personalizzate"
                }
                val usedNames = HashSet<String>()
                val baseNames = setOf(
                    "cibo", "trasporti", "casa", "shopping", "intrattenimento", "salute"
                )
                for (i in 0 until categoryItems.length()) {
                    val item = categoryItems.getJSONObject(i)
                    val name = item.getString("name")
                    val argb = item.strictLong("colorArgb")
                    val iconKey = item.getString("iconKey")
                    require(name.isNotBlank() && name == name.trim() &&
                            name.length <= 24 && usedNames.add(name.lowercase(Locale.ROOT)) &&
                            name.lowercase(Locale.ROOT) !in baseNames
                    ) { "Nome categoria personalizzata non valido o duplicato" }
                    require(argb in 0L..0xFFFFFFFFL) { "Colore categoria non valido" }
                    require(iconKey.length in 1..40 && iconKey.matches(Regex("[a-z0-9_]+"))) {
                        "Icona categoria non valida"
                    }
                    val isArchived = if (backupVersion >= 4) {
                        val flag = item.get("isArchived")
                        require(flag is Boolean) { "Stato categoria non valido" }
                        flag
                    } else false
                    customCategories.add(CustomCategory(name, argb, iconKey, isArchived))
                }
            }
            ExpenseBackupPreview(
                expenses, exportedAt, recurringRules, customCategories, backupVersion
            )
        }

    /**
     * Ripristino con sostituzione: operazione ATOMICA.
     * In caso di errore la transazione viene annullata e le vecchie spese restano.
     */
    suspend fun replaceAll(context: Context, preview: ExpenseBackupPreview) =
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(context)
            val dao = database.expenseDao()
            database.withWriteTransaction {
                dao.deleteAllRecurringRules()
                dao.deleteAll()
                dao.deleteAllCustomCategories()
                dao.insertAllCustomCategories(preview.customCategories)
                dao.insertAll(preview.expenses)
                dao.insertAllRecurringRules(preview.recurringRules)
            }
            RecurringExpenseManager.ensurePeriodicCheck(context)
            RecurringExpenseManager.materializeDue(context)
        }

    private fun JSONObject.strictLong(name: String): Long {
        val value = get(name)
        require(value is Number) { "Numero non valido: $name" }
        return value.toString().toLongOrNull()
            ?: throw IllegalArgumentException("Numero intero non valido: $name")
    }
}
