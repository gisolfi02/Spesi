package com.example.expensetracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.expensetracker.data.CustomCategory

/** Un solo catalogo grafico per Home, Nuova spesa, Storico e Dettaglio. */
data class ExpenseCategoryStyle(
    val name: String,
    val color: Color,
    val icon: ImageVector
)

data class ExpenseCategoryIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector
)

data class ExpenseCategoryColorOption(
    val label: String,
    val argb: Long
) {
    val color: Color get() = Color(argb.toInt())
}

object ExpenseCategoryCatalog {
    val defaults = listOf(
        ExpenseCategoryStyle("Cibo", Color(0xFFFF6969), Icons.Filled.Restaurant),
        ExpenseCategoryStyle("Trasporti", Color(0xFF3495FF), Icons.Filled.DirectionsBus),
        ExpenseCategoryStyle("Casa", Color(0xFF42C96A), Icons.Filled.Home),
        ExpenseCategoryStyle("Shopping", Color(0xFFFFB51B), Icons.Filled.ShoppingBag),
        ExpenseCategoryStyle("Intrattenimento", Color(0xFF9860EE), Icons.Filled.Movie),
        ExpenseCategoryStyle("Salute", Color(0xFF20C2CC), Icons.Filled.FavoriteBorder)
    )

    val iconOptions = listOf(
        ExpenseCategoryIconOption("pets", "Animali", Icons.Filled.Pets),
        ExpenseCategoryIconOption("fitness", "Sport", Icons.Filled.FitnessCenter),
        ExpenseCategoryIconOption("school", "Studio", Icons.Filled.School),
        ExpenseCategoryIconOption("flight", "Viaggi", Icons.Filled.Flight),
        ExpenseCategoryIconOption("coffee", "Caffè", Icons.Filled.LocalCafe),
        ExpenseCategoryIconOption("games", "Giochi", Icons.Filled.SportsEsports),
        ExpenseCategoryIconOption("work", "Lavoro", Icons.Filled.Work),
        ExpenseCategoryIconOption("car", "Auto", Icons.Filled.DirectionsCar),
        ExpenseCategoryIconOption("gift", "Regali", Icons.Filled.CardGiftcard),
        ExpenseCategoryIconOption("music", "Musica", Icons.Filled.MusicNote),
        ExpenseCategoryIconOption("beauty", "Cura", Icons.Filled.Spa),
        ExpenseCategoryIconOption("other", "Altro", Icons.Filled.Category)
    )

    val colorOptions = listOf(
        ExpenseCategoryColorOption("Viola", 0xFF8655EBL),
        ExpenseCategoryColorOption("Rosa", 0xFFED65A8L),
        ExpenseCategoryColorOption("Corallo", 0xFFFF7567L),
        ExpenseCategoryColorOption("Arancione", 0xFFF29A38L),
        ExpenseCategoryColorOption("Giallo", 0xFFE8B322L),
        ExpenseCategoryColorOption("Verde", 0xFF38B979L),
        ExpenseCategoryColorOption("Turchese", 0xFF20B8C5L),
        ExpenseCategoryColorOption("Blu", 0xFF438CE8L),
        ExpenseCategoryColorOption("Indaco", 0xFF606DCFL),
        ExpenseCategoryColorOption("Grigio", 0xFF738096L)
    )

    fun iconFor(key: String): ImageVector =
        iconOptions.firstOrNull { it.key == key }?.icon ?: Icons.Filled.Category

    fun style(name: String, customs: List<CustomCategory>): ExpenseCategoryStyle {
        defaults.firstOrNull { it.name == name }?.let { return it }
        customs.firstOrNull { it.name == name }?.let {
            return ExpenseCategoryStyle(it.name, Color(it.colorArgb.toInt()), iconFor(it.iconKey))
        }
        // Conserva visibili anche categorie appartenenti a vecchi backup.
        return ExpenseCategoryStyle(name, Color(0xFF7138EB), Icons.Filled.Receipt)
    }

    fun all(customs: List<CustomCategory>): List<ExpenseCategoryStyle> =
        defaults + customs
            .filter { custom -> defaults.none { it.name.equals(custom.name, ignoreCase = true) } }
            .sortedBy { it.name.lowercase() }
            .map { style(it.name, customs) }

    /** Categorie selezionabili per NUOVE spese: quelle archiviate sono nascoste. */
    fun selectable(customs: List<CustomCategory>): List<ExpenseCategoryStyle> =
        all(customs.filterNot { it.isArchived })

    fun activeStyles(
        totals: Map<String, Long>,
        customs: List<CustomCategory>
    ): List<ExpenseCategoryStyle> {
        val names = totals.filterValues { it > 0L }.keys
        val known = all(customs)
        return known.filter { it.name in names } +
                names.filter { name -> known.none { it.name == name } }
                    .sorted().map { style(it, customs) }
    }
}
