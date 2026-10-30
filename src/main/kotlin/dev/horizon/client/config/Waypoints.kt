package dev.horizon.client.config

import dev.horizon.client.HorizonClient
import kotlinx.serialization.Serializable
import java.nio.file.Files

/** The original's waypoint (`zs`): dimension 0 overworld, 1 nether, 2 anything else. */
@Serializable
data class WayPoint(
    var id: String,
    var x: Int,
    var y: Int,
    var z: Int,
    var server: String = "localhost",
    var dimension: Int = 0,
    var visible: Boolean = true,
)

@Serializable
private data class WayPointsFile(val wayPoints: List<WayPoint>)

/** The original's waypoints (`lo`), saved in `waypoints.hzn`. */
object Waypoints {
    val list = ArrayList<WayPoint>()

    private val file get() = Storage.root.resolve("waypoints.hzn")

    /** The original's `lo.f`: the dimension index of a dimension path. */
    fun dimension(path: String) = when (path) {
        "overworld" -> 0
        "the_nether" -> 1
        else -> 2
    }

    fun load() {
        list.clear()
        try {
            if (Files.exists(file) && Files.size(file) > 0) list.addAll(Storage.json.decodeFromString(WayPointsFile.serializer(), Files.readString(file)).wayPoints)
        } catch (e: Exception) {
            HorizonClient.LOG.warn("Waypoints are not loaded from file for some reasons.")
        }
    }

    fun save() = Storage.write(file, WayPointsFile.serializer(), WayPointsFile(list.toList()))
}
