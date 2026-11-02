package com.pixelmunchers.game

/** A level is a small pixel picture. Each letter is a color, '.' is empty. */
data class Level(
    val name: String,
    val mix: Float,      // how much the tile order is shuffled (higher = harder)
    val hidden: Float,   // chance a tile behind the front row is a "?" tile
    val art: List<String>
)

object Levels {

    private val palette: Map<Char, Int> = mapOf(
        'R' to 0xFFE53935.toInt(), // red
        'O' to 0xFFFB8C00.toInt(), // orange
        'Y' to 0xFFFDD835.toInt(), // yellow
        'G' to 0xFF43A047.toInt(), // green
        'L' to 0xFF9CCC65.toInt(), // light green
        'B' to 0xFF1E88E5.toInt(), // blue
        'S' to 0xFF81D4FA.toInt(), // sky
        'V' to 0xFF8E24AA.toInt(), // violet
        'P' to 0xFFF48FB1.toInt(), // pink
        'N' to 0xFF8D5524.toInt(), // brown
        'C' to 0xFFEBC98F.toInt(), // cream
        'A' to 0xFF9E9E9E.toInt(), // gray
        'W' to 0xFFF7F7F2.toInt(), // white
        'K' to 0xFF3A2E39.toInt()  // near-black
    )

    fun color(ch: Char): Int = palette[ch] ?: 0xFF888888.toInt()

    /** Hand-drawn pictures. The first four are the tutorial levels. */
    val handmade: List<Level> = listOf(
        Level("Mushroom", 0.5f, 0f, listOf(
            "....RRRRRR....",
            "..RRWWRRRRRR..",
            ".RRWWWRRRWWRR.",
            ".RRWWRRRRWWWR.",
            "RRRRRRRRRRWWRR",
            "RRWWRRRRRRRRRR",
            "RWWWWRRRWWRRRR",
            "RRWWRRRRWWWRRR",
            ".RRRRRRRRRRRR.",
            "....CCCCCC....",
            "....CCKCKC....",
            "....CCCCCC....",
            "....CCCCCC....",
            ".....CCCC....."
        )),
        Level("Cactus", 0.8f, 0f, listOf(
            "......GG......",
            ".....GGGG.....",
            ".GG..GGGG.....",
            ".GG..GGYG..GG.",
            ".GGG.GGGG..GG.",
            "..GGGGGGG.GGG.",
            "....GGGGGGGG..",
            ".....GLGG.....",
            ".....GGLG.....",
            ".....GLGG.....",
            "..OOOOOOOOOO..",
            "..ONNNNNNNNO..",
            "...OOOOOOOO...",
            "...OOPPPPOO...",
            "....OOOOOO....",
            "....OOOOOO...."
        )),
        Level("Clownfish", 1.0f, 0.1f, listOf(
            "......OOOO......",
            "....OOWOOOOO....",
            "..OOOOWOOWOOO..N",
            ".OOKOWWOOWOOO.NN",
            "OOOOOWWOOWWOONNN",
            "OOOOOWWOOWWOONNN",
            ".OOOOWWOOWOOO.NN",
            "..OOOOWOOWOOO..N",
            "....OOWOOOOO....",
            "......OOOO......"
        )),
        Level("Balloon", 1.1f, 0.12f, listOf(
            "....BBBBBB....",
            "..BBBBBBBBBB..",
            ".BBWWBBBBBBBB.",
            ".BWWBBBBBBBBB.",
            "BBWBBBBPPBBBBB",
            "BBBBBBPPPPBBBB",
            "BBBBBBPPPPBBBB",
            "BBBBBBBPPBBBBB",
            ".BBBBBBBBBBBB.",
            ".BBBBBBBBBBBB.",
            "..BBBBBBBBBB..",
            "....BBBBBB....",
            "......BB......",
            "......YY......",
            "......NN......",
            ".....NN.......",
            "......NN......"
        )),
        Level("Strawberry", 1f, 0f, listOf(
            "....GGGG....",
            "..GGLGGLGG..",
            ".RRRGGGGRRR.",
            "RRYRRRRRRYRR",
            "RRRRRYRRRRRR",
            "RYRRRRRRYRRR",
            "RRRRYRRRRRYR",
            ".RRRRRRYRRR.",
            ".RYRRRRRRRR.",
            "..RRRYRRRR..",
            "..RRRRRRYR..",
            "...RRYRRR...",
            "....RRRR....",
            ".....RR....."
        )),
        Level("Ice cream", 1f, 0f, listOf(
            "....PPPP....",
            "..PPPPPPPP..",
            ".PPWPPPPPPP.",
            ".PPPPPPRPPP.",
            "PPPPPPPPPPPP",
            "PPRPPPPPPWPP",
            ".PPPPPPPPPP.",
            "CCCCCCCCCCCC",
            ".CNCCNCCNCC.",
            ".CCNCCNCCNC.",
            "..CCNCCNCC..",
            "..NCCNCCNC..",
            "...CCNCCN...",
            "....CNCC....",
            "....NCCN....",
            ".....CC....."
        )),
        Level("House", 1f, 0f, listOf(
            "......RR......",
            ".....RRRR.....",
            "....RRRRRR....",
            "...RRRRRRRR...",
            "..RRRRRRRRRR..",
            ".RRRRRRRRRRRR.",
            "RRRRRRRRRRRRRR",
            ".CCCCCCCCCCCC.",
            ".CSSCCCCCCSSC.",
            ".CSSCCNNCCSSC.",
            ".CCCCCNNCCCCC.",
            ".CCCCCNYCCCCC.",
            ".CCCCCNNCCCCC.",
            "GGGGGGGGGGGGGG"
        )),
        Level("Sun", 1f, 0f, listOf(
            "......Y......",
            ".Y....Y....Y.",
            "..Y..OOO..Y..",
            "....OYYYO....",
            "...OYYYYYO...",
            "..OYYKYKYYO..",
            "YYOYYYYYYYOYY",
            "..OYYRRRYYO..",
            "...OYYYYYO...",
            "....OYYYO....",
            "..Y..OOO..Y..",
            ".Y....Y....Y.",
            "......Y......"
        )),
        Level("Frog", 1f, 0f, listOf(
            "..GGG....GGG..",
            ".GWWKG..GKWWG.",
            ".GWKKGGGGKKWG.",
            ".GGGGGGGGGGGG.",
            "GGGGGGGGGGGGGG",
            "GGLLLLLLLLLLGG",
            "GGLRRRRRRRRLGG",
            "GGLLRRRRRRLLGG",
            ".GGLLLLLLLLGG.",
            "..GGGGGGGGGG..",
            "...GG....GG..."
        )),
        Level("Cherries", 1f, 0f, listOf(
            ".......NN...",
            "......N.N...",
            ".....N...N..",
            "....N....N..",
            "...N......N.",
            "..N.......N.",
            ".RRR.....RRR",
            "RRWRR...RRWR",
            "RRRRR...RRRR",
            "RRRRR...RRRR",
            ".RRR.....RR."
        )),
        Level("Rocket", 1f, 0f, listOf(
            ".....R.....",
            "....RRR....",
            "...RRRRR...",
            "...WWWWW...",
            "...WSSSW...",
            "...WSSSW...",
            "...WWWWW...",
            "...WWWWW...",
            "..RWWWWWR..",
            ".RRWWRWWRR.",
            ".RRWWRWWRR.",
            "RR.WWWWW.RR",
            "R..NNNNN..R",
            "....OYO....",
            "....YOY....",
            ".....O....."
        )),
        Level("Watermelon", 1f, 0f, listOf(
            "RRRRRRRRRRRRRRRR",
            "RRKRRRRKRRRRKRRR",
            ".RRRRKRRRRKRRRR.",
            ".RRRRRRRRRRRRRR.",
            "..RRKRRRRRRKRR..",
            "...WWWWWWWWWW...",
            "....LLLLLLLL....",
            ".....GGGGGG....."
        )),
        Level("Cat", 1f, 0f, listOf(
            ".O..........O.",
            ".OO........OO.",
            ".OPO......OPO.",
            ".OOOOOOOOOOOO.",
            "OOOOOOOOOOOOOO",
            "OOKKOOOOOOKKOO",
            "OOKWOOOOOOKWOO",
            "OOOOOOPPOOOOOO",
            "WOOOOOKKOOOOOW",
            "OOOOOKOOKOOOOO",
            ".OOOOOOOOOOOO.",
            "..OOOOOOOOOO.."
        )),
        Level("Tree", 1f, 0f, listOf(
            "......G......",
            ".....GGG.....",
            "....GGLGG....",
            "...GGGGGGG...",
            "....GLGGG....",
            "...GGGGLGG...",
            "..GGLGGGGGG..",
            "...GGGGGLG...",
            "..GGGGLGGGG..",
            ".GGLGGGGGGLG.",
            "GGGGGGGLGGGGG",
            ".....NNN.....",
            ".....NNN.....",
            ".....NNN.....",
            "...NNNNNNN..."
        ))
    )
}
