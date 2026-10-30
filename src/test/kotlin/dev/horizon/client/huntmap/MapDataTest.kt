package dev.horizon.client.huntmap

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapDataTest {
    @Test
    fun readsBaseFinderLinesOldAndNew() {
        val finds = MapData.parseBases(listOf(
            "2026-10-26 16:43:41 | X -29898883 Y 60 Z -970402 | score 15 | 2 ender chests, 6 portal blocks | chunks -1868681,-60651",
            "2026-10-26 17:09:44 | BASE | X 256 Y 70 Z 244 | score 131 | 39 signs, 6 anvils | chunks 16,16 15,15",
            "2026-10-26 17:10:00 | STASH | X -5 Y 12 Z 9 | score 40 | 30 shulkers | chunks -1,0",
            "garbage",
        ))
        assertEquals(3, finds.size)
        assertEquals("base", finds[0].kind)
        assertEquals(-29898883, finds[0].x)
        assertEquals("2 ender chests, 6 portal blocks, score 15", finds[0].detail)
        assertEquals(256, finds[1].x)
        assertEquals("stash", finds[2].kind)
        assertEquals(9, finds[2].z)
    }

    @Test
    fun readsCluesAndStashesForOneWorld() {
        val clues = MapData.parseKinds(listOf("2026-10-26 17:09:44 | Sign | X 235 Y 65 Z 269 | \"STYLE / /skin\"")) { "clue" }
        assertEquals("Sign", clues.single().label)
        assertEquals("\"STYLE / /skin\"", clues.single().detail)
        val stashes = MapData.parseStashes(listOf(
            "t | play.6b6t.org | overworld | X 1 Y 2 Z 3 | 9 shulkers, 0 chests, 0 hoppers | total 9",
            "t | play.6b6t.org | the_nether | X 4 Y 5 Z 6 | 1 shulkers | total 1",
            "t | other.net | overworld | X 7 Y 8 Z 9 | 1 shulkers | total 1",
        ), "play.6b6t.org", "overworld")
        assertEquals(1, stashes.size)
        assertEquals(3, stashes[0].z)
    }

    @Test
    fun readsWorkerMessages() {
        assertEquals(listOf("worker-1", "node-13", "us-east"), MapData.parseWorker("You're now playing on worker-1, hosted on node-13 in us-east."))
        assertNull(MapData.parseWorker("<someone> hello"))
        val c = MapData.parseCrossings(listOf("2026-10-28 12:00:00 | worker-1 | worker-4 | 1000 -250")).single()
        assertEquals("worker-4", c.to)
        assertEquals(-250, c.z)
    }

    @Test
    fun dropsPortalTripsFromCrossings() {
        val crossings = MapData.parseCrossings(listOf(
            "2026-10-28 21:11:37 | worker-1 | worker-2 | -5000 3000",
            "2026-10-29 08:31:45 | worker-6 | worker-0 | 12001 -400",
            "2026-10-29 09:00:00 | worker-1 | worker-4 | 8000 8000",
        ))
        val finds = MapData.parseKinds(listOf(
            "2026-10-28 21:11:37 | portal | X -5001 Y 117 Z 3000 | ",
            "2026-10-29 08:31:45 | portal | X 12000 Y 71 Z -402 | ",
            "2026-10-29 08:40:00 | island | X 8003 Y 0 Z 8001 | ",
        )) { it }
        val left = MapData.withoutPortalTrips(crossings, finds)
        assertEquals(listOf("worker-4"), left.map { it.to })
    }

    @Test
    fun picksTheSeedByServerAndDimension() {
        val seeds = MapData.parseSeeds("6b6t.org=-4172144997902289642 2b2t.org=-4172144997902289642/146008555100680, bad=x")
        assertEquals(2, seeds.size)
        assertEquals(-4172144997902289642L, MapData.seedFor(seeds, "play.6b6t.org", true))
        assertEquals(-4172144997902289642L, MapData.seedFor(seeds, "6b6t.org:25565", false))
        assertEquals(146008555100680L, MapData.seedFor(seeds, "connect.2b2t.org", true))
        assertNull(MapData.seedFor(seeds, "not6b6t.org", false))
    }

    @Test
    fun regionFilesRoundTrip() {
        val dir = Files.createTempDirectory("huntmap-test")
        val r = MapRegion(-3, 7)
        r.setState(5, 6, MapRegion.SEEN)
        r.setState(5, 6, MapRegion.NEW)
        r.setState(5, 6, MapRegion.OLD) // the first verdict stays
        r.diff[40] = MapRegion.CHANGED.toByte()
        for (i in 0 until 5000) r.colors[i * 37 % MapRegion.BLOCKS] = (i and 255).toByte()
        val f = dir.resolve(MapRegion.fileName(r.rx, r.rz))
        MapRegion.save(f, r.states, r.diff, r.colors)
        val back = MapRegion.load(f, -3, 7)
        assertEquals(MapRegion.NEW, back.state(5, 6))
        assertContentEquals(r.diff, back.diff)
        assertContentEquals(r.colors, back.colors)
        assertEquals(-3 to 7, MapRegion.parseName(f.fileName.toString()))
        assertEquals(-3, MapRegion.rx(MapRegion.key(-3, 7)))
        assertEquals(7, MapRegion.rz(MapRegion.key(-3, 7)))
    }

    @Test
    fun overviewAveragesRecordedBlocks() {
        val colors = ByteArray(MapRegion.BLOCKS)
        val palette = IntArray(256) { if (it < 4) 0 else 0xFF000000.toInt() or (it shl 16) }
        // Half of the top-left 8x8 cell recorded with colour 100.
        for (y in 0 until 4) for (x in 0 until 8) colors[y * 512 + x] = 100
        val o = MapRegion.overview(colors, palette)
        assertEquals(100, o[0] shr 16 and 255)
        assertEquals(127, o[0] ushr 24)
        assertEquals(0, o[1])
    }
}
