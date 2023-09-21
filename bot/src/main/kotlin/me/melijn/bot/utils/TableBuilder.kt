package me.melijn.bot.utils

import me.melijn.bot.model.Cell
import me.melijn.bot.model.enums.Alignment
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

class TableBuilder {

    private val ANSI_REGEX = Regex("\\x1B\\[\\d\\d?;\\d\\d?m")

    private val headerRow = mutableListOf<Cell>()
    private val valueRows = mutableMapOf<Int, List<Cell>>()
    private val footerRow = mutableListOf<Cell>()
    private val columnWidth = mutableMapOf<Int, Int>()
    private val extraSplit = mutableMapOf<Int, String>()

    var defaultSeperator = " | "
    var seperatorOverrides = mutableMapOf<Int, String>()

    var codeBlockLanguage = "markdown"

    var headerSeperator = "="
    var footerTopSeperator = "~"
    var footerBottomSeperator = ""
    var rowPrefix = ""
    var rowSuffix = ""

    fun addSplit(character: String = footerTopSeperator) {
        extraSplit[valueRows.size] = character
    }

    fun setColumns(vararg headerValues: String): TableBuilder {
        val cells = headerValues.map { Cell(it) }.toList()

        headerRow.addAll(cells)
        findWidest(cells)
        return this
    }

    fun setColumns(headerCells: List<Cell>): TableBuilder {
        headerRow.addAll(headerCells)
        findWidest(headerCells)
        return this
    }
    fun setColumns(vararg headerCells: Cell): TableBuilder = setColumns(headerCells.toList())

    fun addRow(vararg rowElements: String): TableBuilder {
        val cellList = rowElements.map { Cell(it) }
        valueRows[valueRows.size] = cellList
        findWidest(cellList.toList())
        return this
    }
    fun addRow(rowCells: List<Cell>): TableBuilder {
        valueRows[valueRows.size] = rowCells.toList()
        findWidest(rowCells)
        return this
    }

    fun addRow(vararg rowCells: Cell): TableBuilder {
        valueRows[valueRows.size] = rowCells.toList()
        findWidest(*rowCells)
        return this
    }

    fun setFooterRow(vararg footerElements: String): TableBuilder {
        val cells = footerElements.map { Cell(it) }.toList()

        footerRow.addAll(cells)
        findWidest(cells)
        return this
    }

    fun setFooterRow(vararg footerElements: Cell): TableBuilder {
        footerRow.addAll(footerElements)
        findWidest(*footerElements)
        return this
    }

    private fun findWidest(vararg rowElements: Cell) {
        findWidest(rowElements.toList())
    }

    private fun findWidest(rowElements: List<Cell>) {
        for ((temp, c) in rowElements.withIndex()) {
            val s = toDisplayString(c.value)

            if (columnWidth.getOrDefault(temp, 0) < s.codePoints().count()) {
                columnWidth[temp] = s.codePoints().count().toInt()
            }
        }
    }

    private fun toDisplayString(s: String) = when (codeBlockLanguage) {
        "ansi" -> s.replace(ANSI_REGEX, "")
        else -> s
    }

    fun build(split: Boolean): List<String> {
        require(!valueRows.values.stream().anyMatch { array -> array.size > headerRow.size }) {
            "A value row cannot have more values then the header (you can make empty header slots)"
        }

        require(footerRow.size <= headerRow.size) {
            "A footer row cannot have more values then the header (you can make empty header slots)"
        }

        var maxRowWidth = columnWidth.size * 3 - 2
        for (i in columnWidth.values) {
            maxRowWidth += i
        }

        var sb = StringBuilder()
        val toReturn = ArrayList<String>()
        addCodeblockStart(sb)
        addRow(sb, headerRow)
        addHeaderSplicer(sb)

        //main
        for ((index, element) in valueRows) {
            if (extraSplit.containsKey(index)) {
                extraSplit[index]?.let { addSplicer(sb, it, headerRow) }
            }

            if (split && sb.length + maxRowWidth > 1997 - (if (footerRow.size > 0) maxRowWidth * 3 else maxRowWidth)) {
                toReturn.add("$sb```")
                sb = StringBuilder()
                sb.append("```$codeBlockLanguage\n")
            }

            addRow(sb, element)
        }

        if (footerRow.size > 0) {
            if (footerTopSeperator.isNotEmpty()) {
                addTopFooterSplicer(sb)
            }
            addRow(sb, footerRow)
        }
        if (footerBottomSeperator.isNotEmpty()) {
            addBottom(sb)
        }

        toReturn.add("$sb```")

        // less gc
        headerRow.clear()
        valueRows.clear()
        columnWidth.clear()
        footerRow.clear()
        return toReturn
    }

    // CodeBlockStart
    private fun addCodeblockStart(sb: StringBuilder) {
        sb.append("```$codeBlockLanguage\n")
    }

    private fun addRow(sb: StringBuilder, list: List<Cell>) {
        sb.append(rowPrefix)
        for ((i, cell) in list.withIndex()) {
            when (cell.alignment) {
                Alignment.LEFT -> {
                    sb
                        .append(cell.value)

                    if (i != list.size - 1 || rowSuffix.isNotEmpty()) {
                        sb.append(getSpaces(i, cell.value))
                    }
                }

                Alignment.RIGHT -> {
                    sb
                        .append(getSpaces(i, cell.value))
                        .append(cell.value)
                }

                Alignment.CENTER -> {
                    sb
                        .append(getLeftSpaces(i, cell.value))
                        .append(cell.value)

                    if (i != list.size - 1 || rowSuffix.isNotEmpty()) {
                        sb.append(getRightSpaces(i, cell.value))
                    }
                }
            }
            if (i != list.size - 1) {
                sb.append(seperatorOverrides.getOrDefault(i, defaultSeperator))
            }
        }
        sb
            .append(rowSuffix)
            .append("\n")
    }

    private fun addHeaderSplicer(sb: StringBuilder) {
        addSplicer(sb, headerSeperator, headerRow)
    }

    private fun addTopFooterSplicer(sb: StringBuilder) {
        addSplicer(sb, footerTopSeperator, footerRow)
    }

    private fun addBottom(sb: StringBuilder) {
        addSplicer(sb, footerBottomSeperator, footerRow)
    }

    private fun addSplicer(sb: StringBuilder, separator: String, row: List<Cell>) {
        for (i in row.indices) {
            val separatorLength = if (i != row.size - 1) {
                seperatorOverrides.getOrDefault(i, defaultSeperator).length + if (i == 0) {
                    rowPrefix.length
                } else {
                    0
                }
            } else {
                rowSuffix.length
            }
            val length = columnWidth[i]?.plus(separatorLength) ?: throw IllegalArgumentException("error")
            sb.append(separator.repeat(length))
        }

        sb.append("\n")
    }

    private fun getSpaces(widthIndex: Int, value: String): String {
        return columnWidth[widthIndex]?.minus(toDisplayString(value).length)?.let {
            " ".repeat(min(50, it))
        } ?: ""
    }

    private fun getLeftSpaces(widthIndex: Int, value: String): String {
        return columnWidth[widthIndex]?.minus(toDisplayString(value).length)?.let {
            " ".repeat(min(50, floor(it / 2.0).toInt()))
        } ?: ""
    }

    private fun getRightSpaces(widthIndex: Int, value: String): String {
        return columnWidth[widthIndex]?.minus(toDisplayString(value).length)?.let {
            " ".repeat(min(50, ceil(it / 2.0).toInt()))
        } ?: ""
    }
}

class RowBuilder(val cells: MutableList<Cell>)

fun RowBuilder.cell(value: String, position: Alignment = Alignment.RIGHT) {
    cells.add(Cell(value, position))
}
fun RowBuilder.leftCell(value: String) = cell(value, Alignment.LEFT)
fun RowBuilder.centerCell(value: String) = cell(value, Alignment.CENTER)
fun RowBuilder.rightCell(value: String) = cell(value, Alignment.RIGHT)

fun TableBuilder.row(rowBuilderFunc: RowBuilder.() -> Unit) {
    val rowBuilder = RowBuilder(mutableListOf())
    rowBuilder.apply(rowBuilderFunc)
    this.addRow(rowBuilder.cells)
}

fun TableBuilder.header(headerBuilderFunc: RowBuilder.() -> Unit) {
    val rowBuilder = RowBuilder(mutableListOf())
    rowBuilder.apply(headerBuilderFunc)
    this.setColumns(rowBuilder.cells)
}

/** Sets the [index]'th symbol between columns to [seperator].
 *
 * ```
 * E.g. seperator(1, "-")
 * Makes rows look like:  x | y - z | u
 * Instead of the normal: x | y | z | u
 * ```
 */
fun TableBuilder.seperator(index: Int, seperator: String) {
    this.seperatorOverrides[index] = seperator
}

fun tableBuilder(applied: TableBuilder.() -> Unit): TableBuilder {
    val tableBuilder = TableBuilder()
    applied.invoke(tableBuilder)
    return tableBuilder
}