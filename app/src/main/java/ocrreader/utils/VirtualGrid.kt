package ocrreader.utils

import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.vision.text.Text
import com.google.android.gms.vision.text.TextBlock
import java.util.*

class VirtualGrid {
    private val tiles = HashSet<GridTile>()
    private val timeFilter = TimeFilter<String>(CHOICE_COOL_DOWN)
    val size
        get() = tiles.size

    val tilesAsString
        get() = tiles.asIterable().map { i -> i.value }

    fun addTile(tile: Text): Boolean {
        return tiles.add(GridTile(tile))
    }

    fun contains(tile: Text): Boolean {
        return tiles.any { i -> i.value == preProcess(tile.value) }
    }

    fun clear() {
        tiles.clear()
    }

    private fun diff(tiles: List<TextBlock>): Set<Pair<String, Point>> {
        val detectedTiles = tiles
                .filter { tile -> tile.value != null }
                .map { tile -> GridTile(tile) }
                .toHashSet()

        return this.tiles.subtract(detectedTiles).toLabeledTiles()
    }

    fun findChoice(tiles: List<TextBlock>): String? {
        val missingValues = diff(tiles)

        if (missingValues.isEmpty()) {
            return null
        }

        Log.i("FILTER", "Missing Value: ${missingValues.joinToString { i -> i.first }}}")

        // Android Screen
        // (0,0) -- (maxX,0)
        // (0,maxY) -- (maxX,maxY)
        // Note that the representation of Android screen (which the point are recorded WRT them)
        // are different from reality. Take care of the mobile orientation also

        val chosenPoint = missingValues.sortedByDescending { i -> i.second.y }.firstOrNull()
                ?: return null

        val chosenLabel = chosenPoint.first

        // Filter the value
        val coldEnough = timeFilter.isColdEnough(chosenLabel)
        Log.i("FILTER", "Is $chosenLabel cold? $coldEnough")

        if (coldEnough) {
            return chosenLabel
        }

        return null
    }

    companion object {
        const val CHOICE_COOL_DOWN: Long = 1000

        private fun preProcess(s: String): String {
            return s.toLowerCase().replace(" ", "")
        }
    }

    private fun Iterable<GridTile>.toLabeledTiles(): Set<Pair<String, Point>> {
        return this.map { tile -> Pair(tile.value, tile.getCenter()) }.toHashSet()
    }

    /**
     * Instantiating an instance of a [Text] is not possible, this class
     * gives us this ability (just a wrapper class)
     */
    private data class GridTile(private val textItem: Text) : Text {
        private val textBoundingBox: Rect = textItem.boundingBox
        private val textComponents: MutableList<out Text> = textItem.components
        private val textCornerPoints: Array<Point> = textItem.cornerPoints
        private val textValue: String = VirtualGrid.preProcess(textItem.value)

        override fun getBoundingBox(): Rect {
            return textBoundingBox
        }

        override fun getComponents(): MutableList<out Text> {
            return textComponents
        }

        override fun getCornerPoints(): Array<Point> {
            return textCornerPoints
        }

        override fun getValue(): String {
            return textValue
        }

        fun getCenter(): Point {
            return Point(textBoundingBox.centerX(), textBoundingBox.centerY())
        }

        override fun hashCode(): Int {
            return textValue.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            return this.hashCode() == other!!.hashCode()
        }
    }
}