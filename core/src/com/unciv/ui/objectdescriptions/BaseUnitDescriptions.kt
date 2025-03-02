package com.unciv.ui.objectdescriptions

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.city.City
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.UnitMovementType
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.extensions.getConsumesAmountString
import com.unciv.ui.components.extensions.toPercent
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import kotlin.math.pow

object BaseUnitDescriptions {

    fun getShortDescription(baseUnit: BaseUnit): String {
        val infoList = mutableListOf<String>()
        if (baseUnit.strength != 0) infoList += "${baseUnit.strength}${Fonts.strength}"
        if (baseUnit.rangedStrength != 0) infoList += "${baseUnit.rangedStrength}${Fonts.rangedStrength}"
        if (baseUnit.movement != 2) infoList += "${baseUnit.movement}${Fonts.movement}"
        for (promotion in baseUnit.promotions)
            infoList += promotion.tr()
        if (baseUnit.replacementTextForUniques != "") infoList += baseUnit.replacementTextForUniques
        else for (unique in baseUnit.uniqueObjects) if (!unique.hasFlag(UniqueFlag.HiddenToUsers))
            infoList += unique.text.tr()
        return infoList.joinToString()
    }


    /** Generate description as multi-line string for CityScreen addSelectedConstructionTable
     * @param city Supplies civInfo to show available resources after resource requirements */
    fun getDescription(baseUnit: BaseUnit, city: City): String {
        val lines = mutableListOf<String>()
        val availableResources = city.civ.getCivResourcesByName()
        for ((resourceName, amount) in baseUnit.getResourceRequirementsPerTurn()) {
            val available = availableResources[resourceName] ?: 0
            val resource = baseUnit.ruleset.tileResources[resourceName] ?: continue
            val consumesString = resourceName.getConsumesAmountString(amount, resource.isStockpiled())
            lines += "$consumesString ({[$available] available})".tr()
        }
        var strengthLine = ""
        if (baseUnit.strength != 0) {
            strengthLine += "${baseUnit.strength}${Fonts.strength}, "
            if (baseUnit.rangedStrength != 0)
                strengthLine += "${baseUnit.rangedStrength}${Fonts.rangedStrength}, ${baseUnit.range}${Fonts.range}, "
        }
        lines += "$strengthLine${baseUnit.movement}${Fonts.movement}"

        if (baseUnit.replacementTextForUniques != "") lines += baseUnit.replacementTextForUniques
        else for (unique in baseUnit.uniqueObjects.filterNot {
            it.type == UniqueType.Unbuildable
                    || it.type == UniqueType.ConsumesResources  // already shown from getResourceRequirements
                    || it.type?.flags?.contains(UniqueFlag.HiddenToUsers) == true
        })
            lines += unique.text.tr()

        if (baseUnit.promotions.isNotEmpty()) {
            val prefix = "Free promotion${if (baseUnit.promotions.size == 1) "" else "s"}:".tr() + " "
            lines += baseUnit.promotions.joinToString(", ", prefix) { it.tr() }
        }

        return lines.joinToString("\n")
    }

    fun getCivilopediaTextLines(baseUnit: BaseUnit, ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        // Don't call baseUnit.getType() here - coming from the main menu baseUnit isn't fully initialized
        val unitTypeLink = ruleset.unitTypes[baseUnit.unitType]?.makeLink() ?: ""
        textList += FormattedLine("{Unit type}: ${baseUnit.unitType.tr()}", unitTypeLink)

        val stats = ArrayList<String>()
        if (baseUnit.strength != 0) stats += "${baseUnit.strength}${Fonts.strength}"
        if (baseUnit.rangedStrength != 0) {
            stats += "${baseUnit.rangedStrength}${Fonts.rangedStrength}"
            stats += "${baseUnit.range}${Fonts.range}"
        }
        if (baseUnit.movement != 0 && ruleset.unitTypes[baseUnit.unitType]?.isAirUnit() != true)
            stats += "${baseUnit.movement}${Fonts.movement}"
        if (stats.isNotEmpty())
            textList += FormattedLine(stats.joinToString(", "))

        if (baseUnit.cost > 0) {
            stats.clear()
            stats += "${baseUnit.cost}${Fonts.production}"
            if (baseUnit.canBePurchasedWithStat(null, Stat.Gold)) {
                // We need what INonPerpetualConstruction.getBaseGoldCost calculates but without any game- or civ-specific modifiers
                val buyCost = (30.0 * baseUnit.cost.toFloat().pow(0.75f) * baseUnit.hurryCostModifier.toPercent()).toInt() / 10 * 10
                stats += "$buyCost${Fonts.gold}"
            }
            textList += FormattedLine(stats.joinToString("/", "{Cost}: "))
        }

        if (baseUnit.replacementTextForUniques.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine(baseUnit.replacementTextForUniques)
        } else if (baseUnit.uniques.isNotEmpty()) {
            textList += FormattedLine()
            for (unique in baseUnit.uniqueObjects.sortedBy { it.text }) {
                if (unique.hasFlag(UniqueFlag.HiddenToUsers)) continue
                if (unique.type == UniqueType.ConsumesResources) continue  // already shown from getResourceRequirements
                textList += FormattedLine(unique)
            }
        }

        val resourceRequirements = baseUnit.getResourceRequirementsPerTurn()
        if (resourceRequirements.isNotEmpty()) {
            textList += FormattedLine()
            for ((resourceName, amount) in resourceRequirements) {
                val resource = ruleset.tileResources[resourceName] ?: continue
                textList += FormattedLine(
                    resourceName.getConsumesAmountString(amount, resource.isStockpiled()),
                    link = "Resource/$resource", color = "#F42"
                )
            }
        }

        if (baseUnit.uniqueTo != null) {
            textList += FormattedLine()
            textList += FormattedLine("Unique to [${baseUnit.uniqueTo}]", link = "Nation/${baseUnit.uniqueTo}")
            if (baseUnit.replaces != null)
                textList += FormattedLine(
                    "Replaces [${baseUnit.replaces}]",
                    link = "Unit/${baseUnit.replaces}",
                    indent = 1
                )
        }

        if (baseUnit.requiredTech != null || baseUnit.upgradesTo != null || baseUnit.obsoleteTech != null) textList += FormattedLine()
        if (baseUnit.requiredTech != null) textList += FormattedLine(
            "Required tech: [${baseUnit.requiredTech}]",
            link = "Technology/${baseUnit.requiredTech}"
        )

        val canUpgradeFrom = ruleset.units
            .filterValues {
                (it.upgradesTo == baseUnit.name || it.upgradesTo != null && it.upgradesTo == baseUnit.replaces)
                        && (it.uniqueTo == baseUnit.uniqueTo || it.uniqueTo == null)
            }.keys
        if (canUpgradeFrom.isNotEmpty()) {
            if (canUpgradeFrom.size == 1)
                textList += FormattedLine(
                    "Can upgrade from [${canUpgradeFrom.first()}]",
                    link = "Unit/${canUpgradeFrom.first()}"
                )
            else {
                textList += FormattedLine()
                textList += FormattedLine("Can upgrade from:")
                for (unitName in canUpgradeFrom.sorted())
                    textList += FormattedLine(unitName, indent = 2, link = "Unit/$unitName")
                textList += FormattedLine()
            }
        }

        if (baseUnit.upgradesTo != null) textList += FormattedLine(
            "Upgrades to [${baseUnit.upgradesTo}]",
            link = "Unit/${baseUnit.upgradesTo}"
        )
        if (baseUnit.obsoleteTech != null) textList += FormattedLine(
            "Obsolete with [${baseUnit.obsoleteTech}]",
            link = "Technology/${baseUnit.obsoleteTech}"
        )

        if (baseUnit.promotions.isNotEmpty()) {
            textList += FormattedLine()
            baseUnit.promotions.withIndex().forEach {
                textList += FormattedLine(
                    when {
                        baseUnit.promotions.size == 1 -> "{Free promotion:} "
                        it.index == 0 -> "{Free promotions:} "
                        else -> ""
                    } + "{${it.value.tr()}}" +   // tr() not redundant as promotion names now can use []
                            (if (baseUnit.promotions.size == 1 || it.index == baseUnit.promotions.size - 1) "" else ","),
                    link = "Promotions/${it.value}",
                    indent = if (it.index == 0) 0 else 1
                )
            }
        }

        val seeAlso = ArrayList<FormattedLine>()
        for ((other, unit) in ruleset.units) {
            if (unit.replaces == baseUnit.name || baseUnit.uniques.contains("[${baseUnit.name}]")) {
                seeAlso += FormattedLine(other, link = "Unit/$other", indent = 1)
            }
        }
        if (seeAlso.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{See also}:")
            textList += seeAlso
        }

        return textList
    }

    @Suppress("RemoveExplicitTypeArguments")  // for faster IDE - inferring sequence types can be slow
    fun UnitType.getUnitTypeCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        fun getDomainLines() = sequence<FormattedLine> {
            yield(FormattedLine("{Unit types}:", header = 4))
            val myMovementType = getMovementType()
            for (unitType in ruleset.unitTypes.values) {
                if (unitType.getMovementType() != myMovementType) continue
                if (!unitType.isUsed(ruleset)) continue
                yield(FormattedLine(unitType.name, unitType.makeLink()))
            }
        }
        fun getUnitTypeLines() = sequence<FormattedLine> {
            getMovementType()?.let {
                val color = when (it) {
                    UnitMovementType.Land -> "#ffc080"
                    UnitMovementType.Water -> "#80d0ff"
                    UnitMovementType.Air -> "#e0e0ff"
                }
                yield(FormattedLine("Domain: [${it.name}]", "UnitType/Domain: [${it.name}]", color = color))
                yield(FormattedLine(separator = true))
            }
            yield(FormattedLine("Units:", header = 4))
            for (unit in ruleset.units.values) {
                if (unit.unitType != name) continue
                yield(FormattedLine(unit.name, unit.makeLink()))
            }
            if (uniqueObjects.isNotEmpty()) {
                yield(FormattedLine(separator = true))
                for (unique in uniqueObjects) {
                    if (unique.hasFlag(UniqueFlag.HiddenToUsers)) continue
                    yield(FormattedLine(unique))
                }
            }
        }
        return (if (name.startsWith("Domain: ")) getDomainLines() else getUnitTypeLines()).toList()
    }

    /**
     * Lists differences e.g. for help on an upgrade, or how a nation-unique compares to its replacement.
     *
     * Cost is **not** included.
     * Result lines are **not** translated.
     *
     * @param originalUnit The "older" unit
     * @param betterUnit The "newer" unit
     * @return Sequence of Pairs - first is the actual text, second is an optional link for Civilopedia use
     */
    fun getDifferences(ruleset: Ruleset, originalUnit: BaseUnit, betterUnit: BaseUnit):
            Sequence<Pair<String, String?>> = sequence {
        if (betterUnit.strength != originalUnit.strength)
            yield("${Fonts.strength} {[${betterUnit.strength}] vs [${originalUnit.strength}]}" to null)

        if (betterUnit.rangedStrength > 0 && originalUnit.rangedStrength == 0)
            yield("[Gained] ${Fonts.rangedStrength} [${betterUnit.rangedStrength}] ${Fonts.range} [${betterUnit.range}]" to null)
        else if (betterUnit.rangedStrength == 0 && originalUnit.rangedStrength > 0)
            yield("[Lost] ${Fonts.rangedStrength} [${originalUnit.rangedStrength}] ${Fonts.range} [${originalUnit.range}]" to null)
        else {
            if (betterUnit.rangedStrength != originalUnit.rangedStrength)
                yield("${Fonts.rangedStrength} " + "[${betterUnit.rangedStrength}] vs [${originalUnit.rangedStrength}]" to null)
            if (betterUnit.range != originalUnit.range)
                yield("${Fonts.range} {[${betterUnit.range}] vs [${originalUnit.range}]}" to null)
        }

        if (betterUnit.movement != originalUnit.movement)
            yield("${Fonts.movement} {[${betterUnit.movement}] vs [${originalUnit.movement}]}" to null)

        for (resource in originalUnit.getResourceRequirementsPerTurn().keys)
            if (!betterUnit.getResourceRequirementsPerTurn().containsKey(resource)) {
                yield("[$resource] not required" to "Resource/$resource")
            }
        // We return the unique text directly, so Nation.getUniqueUnitsText will not use the
        // auto-linking FormattedLine(Unique) - two reasons in favor:
        // would look a little chaotic as unit uniques unlike most uniques are a HashSet and thus do not preserve order
        // No .copy() factory on FormattedLine and no (Unique, all other val's) constructor either
        if (betterUnit.replacementTextForUniques.isNotEmpty()) {
            yield(betterUnit.replacementTextForUniques to null)
        } else {
            val newAbilityPredicate: (Unique)->Boolean = { it.text in originalUnit.uniques || it.hasFlag(UniqueFlag.HiddenToUsers) }
            for (unique in betterUnit.uniqueObjects.filterNot(newAbilityPredicate))
                yield(unique.text to null)
        }

        val lostAbilityPredicate: (Unique)->Boolean = { it.text in betterUnit.uniques || it.hasFlag(UniqueFlag.HiddenToUsers) }
        for (unique in originalUnit.uniqueObjects.filterNot(lostAbilityPredicate)) {
            yield("Lost ability (vs [${originalUnit.name}]): [${unique.text}]" to null)
        }
        for (promotion in betterUnit.promotions.filter { it !in originalUnit.promotions }) {
            // Needs tr for **individual** translations (no bracket nesting), default separator would have extra blank
            val effects = ruleset.unitPromotions[promotion]!!.uniques
                .joinToString(",") { it.tr() }
            yield("{$promotion} ($effects)" to "Promotion/$promotion")
        }
    }

    /** Prepares a Widget with [information about the differences][getDifferences] between units.
     *  Used by UnitUpgradeMenu (but formerly also for a tooltip).
     */
    fun getUpgradeInfoTable(title: String, unitUpgrading: BaseUnit, unitToUpgradeTo: BaseUnit): Table {
        val ruleset = unitToUpgradeTo.ruleset
        val info = sequenceOf(FormattedLine(title, color = "#FDA", icon = unitToUpgradeTo.makeLink(), header = 5)) +
            getDifferences(ruleset, unitUpgrading, unitToUpgradeTo)
                .map { FormattedLine(it.first, icon = it.second ?: "") }
        val infoTable = MarkupRenderer.render(info.asIterable(), 400f)
        infoTable.background = BaseScreen.skinStrings.getUiBackground("General/Tooltip", BaseScreen.skinStrings.roundedEdgeRectangleShape, Color.DARK_GRAY)
        return infoTable
    }
}
