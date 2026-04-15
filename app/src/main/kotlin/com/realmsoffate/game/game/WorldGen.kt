package com.realmsoffate.game.game

import com.realmsoffate.game.data.Lake
import com.realmsoffate.game.data.MapLocation
import com.realmsoffate.game.data.MapRoad
import com.realmsoffate.game.data.RiverPoint
import com.realmsoffate.game.data.Terrain
import com.realmsoffate.game.data.WorldMap
import kotlin.math.hypot

// LCG seeded random, matches the JS generator's determinism flavor.
class SeededRandom(seed: Long) {
    private var s = if (seed == 0L) 1L else seed
    fun next(): Double {
        s = (s * 16807) % 2147483647
        return (s - 1) / 2147483646.0
    }
}

private data class LocTemplate(val type: String, val icon: String, val names: List<String>)

private val locTemplates = listOf(
    LocTemplate("town", "\uD83C\uDFD8\uFE0F", listOf(
        "Thornwick", "Blackfen", "Oakhaven", "Greystone", "Ravenhollow", "Silvermoor", "Ironbrook", "Ashenford", "Duskvale", "Redfern", "Mosswater", "Kingsreach",
        "Willowmere", "Briarcliff", "Dunmoor", "Foxhollow", "Hearthwick", "Millhaven", "Copperfield", "Ashenmere", "Pinecross", "Stonebridge", "Highthorn", "Wychfield",
        "Barrowton", "Deepwell", "Goldcrest", "Larchford", "Mudhollow", "Rookhurst", "Salthaven", "Thistledown", "Vinterton", "Westmarch", "Elmbury", "Falstead"
    )),
    LocTemplate("city", "\uD83C\uDFDB\uFE0F", listOf(
        "Silverhold", "Highport", "Stormgate", "Goldharbor", "Crownspire", "Emberwatch",
        "Vaultshore", "Ironspire", "Sunharbor", "Ashenmount", "Thronefall", "Starreach", "Blackhaven", "Frostholm", "Whitecliff", "Dawnkeep",
        "Shadowport", "Flamecrest", "Tidemark", "Crystalgate", "Copperport", "Northhold", "Grandveil", "Silkshore", "Forgepeak", "Kingsridge",
        "Nightwall", "Thornhall", "Serpentine", "Wardenmere"
    )),
    LocTemplate("dungeon", "\uD83D\uDD73\uFE0F", listOf(
        "The Blackroot Depths", "Shadowmire Crypt", "Sunken Halls", "Crimson Keep", "The Hollow Pit", "Tomb of Kings",
        "The Wailing Caverns", "Bonemarrow Pit", "Ashvault", "The Drowning Halls", "Ironfang Labyrinth", "Sepulcher of Chains",
        "The Rotwood Cellar", "Voidmaw Chasm", "Scorchdeep", "The Blighted Crypt", "Grimstone Mine", "The Sundered Vault",
        "Plaguewell", "Duskspore Caves", "The Maw of Thirst", "The Forgotten Ossuary", "Nethercoil", "Windless Depths",
        "The Carrion Pit", "Bloodstone Sanctum", "The Husk", "Thornroot Burrow", "The Shackled Deep", "Cinderhollow"
    )),
    LocTemplate("forest", "\uD83C\uDF32", listOf(
        "Whispering Woods", "Thornvale", "Moonlit Forest", "Emerald Glade", "Duskwood", "Wildroot",
        "Briartangle", "Ashen Canopy", "Silverleaf Wood", "The Rotwood", "Misthollow", "Fernwatch",
        "Nightbriar", "Tangle Reach", "Mossdeep", "Spore Hollow", "The Verdant Maze", "Ironbark Forest",
        "Crowwood", "Dewshroud", "Gloomfern", "Wolfswood", "The Petrified Grove", "Whitebirch",
        "Thornmarsh", "Shadowfen", "Oldgrowth", "Lichenveil", "Rootdeep", "Hemlockwood"
    )),
    LocTemplate("mountain", "\u26F0\uFE0F", listOf(
        "Stormpeak", "Grimspire", "Dragon's Tooth", "Frostfang", "Ashridge",
        "Ironjaw Peak", "Thunderhelm", "The Shattered Horn", "Bonecrag", "Cindercone", "Gloomspire", "Whitecap",
        "Razorback Ridge", "The Weeping Summit", "Stonefather", "Windshear", "Hollowpeak", "Dreadhorn",
        "Cloudpiercer", "The Giant's Tooth", "Obsidian Crest", "Frostmantle", "Serpent's Spine", "Ashfall Summit",
        "The Iron Crown", "Bleakridge", "Skullcap", "Thundertop", "Gorecrag", "Splinterpeak"
    )),
    LocTemplate("ruins", "\uD83D\uDDFF", listOf(
        "Eldarian Ruins", "Fallen Spire", "Shattered Temple", "Sunken Altar",
        "The Crumbled Citadel", "Dusthaven", "Ghostwall", "The Broken Spire", "Voidheart Shrine", "Ashfall Temple",
        "The Hollow Court", "Moonwane Priory", "Blightstone", "The Forsaken Altar", "Dawnfall", "Ironwreck",
        "The Shattered Crown", "Rotstone Abbey", "Grimwatch", "The Bone Nave", "Tideswept Fort", "Ember Basilica",
        "The Sinking Throne", "Deadlight Tower", "Riftmark", "Bleached Hall", "The Mournfield", "Cindertemple",
        "Wraithstone", "Starveil"
    )),
    LocTemplate("castle", "\uD83C\uDFF0", listOf(
        "Stonewatch Keep", "Grimhold", "Redthorn Castle",
        "Blackthorn Citadel", "Ironveil Fortress", "Stormhold", "The Crimson Bastion", "Dreadfort", "Wyvernrest",
        "Shadowmere Keep", "Frosthammer Hall", "The Bone Fortress", "Ashcrown Castle", "Thornwall", "Nightspire",
        "The Gilded Keep", "Wraithguard", "Oathstone", "Grimwatch Castle", "Hellgate Fortress", "The Sunken Bastion",
        "Wolfsgate", "Siegeholm", "The Pale Fortress", "Blackiron Keep"
    )),
    LocTemplate("camp", "\u26FA", listOf(
        "Hunter's Rest", "Outrider Camp", "Bandit Roost",
        "Wayward Camp", "Trapper's Hollow", "Driftwood Rest", "The Burned Clearing", "Thornbrake Camp", "Ridgeline Watch",
        "Windbreak", "The Old Bivouac", "Smokepine Rest", "Frostfire Camp", "Mudflat Stop", "The Vagrant's Eye",
        "Deadfall Camp", "Creekside Rest", "Wolftooth Camp", "The Broken Wagon", "Ashpit", "Sentinel's Rest",
        "Dew Hollow", "The Lean-To", "Moorwatch", "Heather Camp"
    ))
)

object WorldGen {
    fun generate(seed: Long = (System.currentTimeMillis() % 99999)): WorldMap {
        val r = SeededRandom(seed)
        val mw = 600; val mh = 500
        val locs = mutableListOf<MapLocation>()
        val used = mutableSetOf<String>()
        val count = 11 + (r.next() * 4).toInt()
        val cols = 4
        for (i in 0 until count) {
            val gx = i % cols
            val gy = i / cols
            val x = 80 + gx * 140 + ((r.next() - 0.5) * 70)
            val y = 70 + gy * 110 + ((r.next() - 0.5) * 50)
            var tmpl = locTemplates[(r.next() * locTemplates.size).toInt()]
            var name = tmpl.names[(r.next() * tmpl.names.size).toInt()]
            while (name in used) {
                tmpl = locTemplates[(r.next() * locTemplates.size).toInt()]
                name = tmpl.names[(r.next() * tmpl.names.size).toInt()]
            }
            used += name
            val type = if (i == 0) "town" else tmpl.type
            val icon = if (i == 0) "\uD83C\uDFD8\uFE0F" else tmpl.icon
            locs += MapLocation(
                id = i, name = name, type = type, icon = icon,
                x = x.toInt(), y = y.toInt(), discovered = i == 0
            )
        }
        val roads = mutableListOf<MapRoad>()
        for (i in locs.indices) {
            val base = locs[i]
            val dists = locs.mapIndexedNotNull { j, l ->
                if (j == i) null else (j to hypot(l.x - base.x.toDouble(), l.y - base.y.toDouble()))
            }.sortedBy { it.second }
            val cc = 2 + if (r.next() > 0.5) 1 else 0
            for (c in 0 until minOf(cc, dists.size)) {
                val to = dists[c].first
                if (roads.none { (it.from == i && it.to == to) || (it.from == to && it.to == i) }) {
                    roads += MapRoad(from = i, to = to, dist = maxOf(1, (dists[c].second / 15).toInt()))
                }
            }
        }
        val terrain = mutableListOf<Terrain>()
        repeat(8 + (r.next() * 6).toInt()) {
            terrain += Terrain("mountain", (r.next() * mw).toFloat(), (r.next() * mh).toFloat(),
                (15 + r.next() * 25).toFloat(), (r.next() * 0.5 - 0.25).toFloat())
        }
        repeat(20 + (r.next() * 15).toInt()) {
            terrain += Terrain("tree", (r.next() * mw).toFloat(), (r.next() * mh).toFloat(), (6 + r.next() * 10).toFloat())
        }
        val rivers = mutableListOf<List<RiverPoint>>()
        repeat(1 + (r.next() * 2).toInt()) {
            val pts = mutableListOf<RiverPoint>()
            var rx = (r.next() * mw * 0.3).toFloat()
            var ry = (r.next() * 50).toFloat()
            repeat(8) {
                pts += RiverPoint(rx, ry)
                rx += (30 + r.next() * 80).toFloat()
                ry += (30 + r.next() * 60).toFloat()
            }
            rivers += pts
        }
        val lakes = mutableListOf<Lake>()
        repeat(1 + (r.next() * 2).toInt()) {
            lakes += Lake(
                x = (100 + r.next() * (mw - 200)).toFloat(),
                y = (80 + r.next() * (mh - 160)).toFloat(),
                rx = (25 + r.next() * 40).toFloat(),
                ry = (15 + r.next() * 25).toFloat(),
                rot = (r.next() * 60).toFloat()
            )
        }
        return WorldMap(locs, roads, 0, terrain, rivers, lakes, mw, mh)
    }

    fun connected(wm: WorldMap, locId: Int): List<Pair<MapLocation, Int>> =
        wm.roads.filter { it.from == locId || it.to == locId }.map { r ->
            val oid = if (r.from == locId) r.to else r.from
            wm.locations[oid] to r.dist
        }
}
