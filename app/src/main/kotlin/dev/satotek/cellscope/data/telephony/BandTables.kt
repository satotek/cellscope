package dev.satotek.cellscope.data.telephony

/**
 * ARFCN → band / frequency conversion (3GPP TS 36.101 §5.7.3, TS 38.101-1/-2 §5.4.2, TS 25.101 §5.4.4).
 * Android ≥30 already reports bands for LTE/NR, but the modem frequently leaves
 * them empty for neighbours, so we derive both band and centre frequency ourselves.
 * Tables are the complete 3GPP sets, not a regional subset.
 */
object BandTables {

    /** [band] is the best guess; [candidates] lists every band whose raster contains the ARFCN (overlaps like n77/n78). */
    data class Freq(val band: Int?, val dlMhz: Double?, val ulMhz: Double?, val candidates: List<Int> = listOfNotNull(band))

    private val NONE = Freq(null, null, null, emptyList())

    // ---- LTE (TS 36.101 Table 5.7.3-1) ----------------------------------------------------------------
    /** band, NOffs-DL, last DL EARFCN, FDL_low (MHz), UL offset (MHz; 0 = TDD, null = SDL). */
    private data class LteBand(val band: Int, val nOffs: Int, val nHigh: Int, val fLow: Double, val ulOffset: Double?)

    private val lte = listOf(
        LteBand(1, 0, 599, 2110.0, -190.0),
        LteBand(2, 600, 1199, 1930.0, -80.0),
        LteBand(3, 1200, 1949, 1805.0, -95.0),
        LteBand(4, 1950, 2399, 2110.0, -400.0),
        LteBand(5, 2400, 2649, 869.0, -45.0),
        LteBand(6, 2650, 2749, 875.0, -45.0),
        LteBand(7, 2750, 3449, 2620.0, -120.0),
        LteBand(8, 3450, 3799, 925.0, -45.0),
        LteBand(9, 3800, 4149, 1844.9, -95.0),
        LteBand(10, 4150, 4749, 2110.0, -400.0),
        LteBand(11, 4750, 4949, 1475.9, -48.0),
        LteBand(12, 5010, 5179, 729.0, -30.0),
        LteBand(13, 5180, 5279, 746.0, 31.0),
        LteBand(14, 5280, 5379, 758.0, 30.0),
        LteBand(17, 5730, 5849, 734.0, -30.0),
        LteBand(18, 5850, 5999, 860.0, -45.0),
        LteBand(19, 6000, 6149, 875.0, -45.0),
        LteBand(20, 6150, 6449, 791.0, 41.0),
        LteBand(21, 6450, 6599, 1495.9, -48.0),
        LteBand(22, 6600, 7399, 3510.0, -100.0),
        LteBand(23, 7500, 7699, 2180.0, -180.0),
        LteBand(24, 7700, 8039, 1525.0, 101.5),
        LteBand(25, 8040, 8689, 1930.0, -80.0),
        LteBand(26, 8690, 9039, 859.0, -45.0),
        LteBand(27, 9040, 9209, 852.0, -45.0),
        LteBand(28, 9210, 9659, 758.0, -55.0),
        LteBand(29, 9660, 9769, 717.0, null),
        LteBand(30, 9770, 9869, 2350.0, -45.0),
        LteBand(31, 9870, 9919, 462.5, -10.0),
        LteBand(32, 9920, 10359, 1452.0, null),
        LteBand(33, 36000, 36199, 1900.0, 0.0),
        LteBand(34, 36200, 36349, 2010.0, 0.0),
        LteBand(35, 36350, 36949, 1850.0, 0.0),
        LteBand(36, 36950, 37549, 1930.0, 0.0),
        LteBand(37, 37550, 37749, 1910.0, 0.0),
        LteBand(38, 37750, 38249, 2570.0, 0.0),
        LteBand(39, 38250, 38649, 1880.0, 0.0),
        LteBand(40, 38650, 39649, 2300.0, 0.0),
        LteBand(41, 39650, 41589, 2496.0, 0.0),
        LteBand(42, 41590, 43589, 3400.0, 0.0),
        LteBand(43, 43590, 45589, 3600.0, 0.0),
        LteBand(44, 45590, 46589, 703.0, 0.0),
        LteBand(45, 46590, 46789, 1447.0, 0.0),
        LteBand(46, 46790, 54539, 5150.0, 0.0),
        LteBand(47, 54540, 55239, 5855.0, 0.0),
        LteBand(48, 55240, 56739, 3550.0, 0.0),
        LteBand(49, 56740, 58239, 3550.0, 0.0),
        LteBand(50, 58240, 59089, 1432.0, 0.0),
        LteBand(51, 59090, 59139, 1427.0, 0.0),
        LteBand(52, 59140, 60139, 3300.0, 0.0),
        LteBand(53, 60140, 60254, 2483.5, 0.0),
        LteBand(65, 65536, 66435, 2110.0, -190.0),
        LteBand(66, 66436, 67335, 2110.0, -400.0),
        LteBand(67, 67336, 67535, 738.0, null),
        LteBand(68, 67536, 67835, 753.0, -55.0),
        LteBand(69, 67836, 68335, 2570.0, null),
        LteBand(70, 68336, 68585, 1995.0, -300.0),
        LteBand(71, 68586, 68935, 617.0, 46.0),
        LteBand(72, 68936, 69085, 461.0, -10.0),
        LteBand(73, 69086, 69235, 460.0, -10.0),
        LteBand(74, 69236, 69735, 1475.0, -48.0),
        LteBand(75, 69736, 70335, 1432.0, null),
        LteBand(76, 70336, 70365, 1427.0, null),
        LteBand(85, 70366, 70545, 728.0, -30.0),
        LteBand(87, 70546, 70595, 420.0, -10.0),
        LteBand(88, 70596, 70645, 422.0, -10.0),
    )

    fun lte(earfcn: Int?): Freq {
        if (earfcn == null || earfcn < 0 || earfcn == Int.MAX_VALUE) return NONE
        val b = lte.firstOrNull { earfcn in it.nOffs..it.nHigh } ?: return NONE
        val dl = b.fLow + 0.1 * (earfcn - b.nOffs)
        val ul = when (b.ulOffset) { null -> null; 0.0 -> dl; else -> dl + b.ulOffset }
        return Freq(b.band, round1(dl), ul?.let(::round1))
    }

    // ---- NR (TS 38.101-1 Table 5.4.2.3-1, TS 38.101-2 Table 5.4.2.3-1) ---------------------------------
    /** NR global frequency raster (TS 38.101-1 Table 5.4.2.1-1). */
    fun nrArfcnToMhz(arfcn: Int?): Double? {
        if (arfcn == null || arfcn < 0 || arfcn == Int.MAX_VALUE) return null
        return when {
            arfcn < 600_000 -> arfcn * 0.005
            arfcn < 2_016_667 -> 3000.0 + (arfcn - 600_000) * 0.015
            arfcn <= 3_279_165 -> 24_250.08 + (arfcn - 2_016_667) * 0.06
            else -> null
        }
    }

    /** band, DL NR-ARFCN range. */
    private data class NrBand(val band: Int, val low: Int, val high: Int)

    private val nr = listOf(
        NrBand(1, 422_000, 434_000), NrBand(2, 386_000, 398_000), NrBand(3, 361_000, 376_000),
        NrBand(5, 173_800, 178_800), NrBand(7, 524_000, 538_000), NrBand(8, 185_000, 192_000),
        NrBand(12, 145_800, 149_200), NrBand(13, 149_200, 151_200), NrBand(14, 151_600, 153_600),
        NrBand(18, 172_000, 175_000), NrBand(20, 158_200, 164_200), NrBand(24, 305_000, 311_800),
        NrBand(25, 386_000, 399_000), NrBand(26, 171_800, 178_800), NrBand(28, 151_600, 160_600),
        NrBand(29, 143_400, 145_600), NrBand(30, 470_000, 472_000), NrBand(34, 402_000, 405_000),
        NrBand(38, 514_000, 524_000), NrBand(39, 376_000, 384_000), NrBand(40, 460_000, 480_000),
        NrBand(41, 499_200, 537_999), NrBand(46, 743_334, 795_000), NrBand(47, 790_334, 795_000),
        NrBand(48, 636_667, 646_666), NrBand(50, 286_400, 303_400), NrBand(51, 285_400, 286_400),
        NrBand(53, 496_700, 499_000), NrBand(65, 422_000, 440_000), NrBand(66, 422_000, 440_000),
        NrBand(67, 147_600, 151_600), NrBand(70, 399_000, 404_000), NrBand(71, 123_400, 130_400),
        NrBand(74, 295_000, 303_600), NrBand(75, 286_400, 303_400), NrBand(76, 285_400, 286_400),
        NrBand(77, 620_000, 680_000), NrBand(78, 620_000, 653_333), NrBand(79, 693_334, 733_333),
        NrBand(85, 145_600, 149_200), NrBand(90, 499_200, 538_000), NrBand(91, 285_400, 286_400),
        NrBand(92, 286_400, 303_400), NrBand(93, 285_400, 286_400), NrBand(94, 286_400, 303_400),
        NrBand(96, 795_000, 875_000), NrBand(100, 183_880, 185_000), NrBand(101, 380_000, 382_000),
        NrBand(102, 796_334, 828_333), NrBand(104, 828_334, 875_000),
        // FR2
        NrBand(257, 2_054_166, 2_104_165), NrBand(258, 2_016_667, 2_070_832), NrBand(259, 2_270_832, 2_337_499),
        NrBand(260, 2_229_166, 2_279_165), NrBand(261, 2_070_833, 2_084_999), NrBand(262, 2_399_166, 2_415_619),
    )

    /**
     * Every band containing the ARFCN, narrowest first (n78 before n77, n38 before n41/n90, n1 before n65/n66),
     * then by band number for ties (n50/n75/n92/n94 share a raster).
     */
    fun nrBandCandidates(arfcn: Int?): List<Int> {
        if (arfcn == null) return emptyList()
        return nr.filter { arfcn in it.low..it.high }.sortedWith(compareBy({ it.high - it.low }, { it.band })).map { it.band }
    }

    fun nr(arfcn: Int?): Freq {
        val mhz = nrArfcnToMhz(arfcn) ?: return NONE
        val c = nrBandCandidates(arfcn)
        return Freq(c.firstOrNull(), round1(mhz), null, c)
    }

    // ---- UMTS (TS 25.101 Table 5.0) ---------------------------------------------------------------------
    /** band, DL UARFCN range, FDL = 0.2·N + offset (MHz), UL offset from DL (MHz). */
    private data class UmtsBand(val band: Int, val low: Int, val high: Int, val offset: Double, val duplex: Double)

    private val umts = listOf(
        UmtsBand(1, 10562, 10838, 0.0, -190.0),
        UmtsBand(2, 9662, 9938, 0.0, -80.0),
        UmtsBand(3, 1162, 1513, 1575.0, -95.0),
        UmtsBand(4, 1537, 1738, 1805.0, -400.0),
        UmtsBand(5, 4357, 4458, 0.0, -45.0),
        UmtsBand(6, 4387, 4413, 0.0, -45.0),
        UmtsBand(7, 2237, 2563, 2175.0, -120.0),
        UmtsBand(8, 2937, 3088, 340.0, -45.0),
        UmtsBand(9, 9237, 9387, 0.0, -95.0),
        UmtsBand(10, 3112, 3388, 1490.0, -400.0),
        UmtsBand(11, 3712, 3787, 736.0, -48.0),
        UmtsBand(12, 3842, 3903, -37.0, -30.0),
        UmtsBand(13, 4017, 4043, -55.0, 31.0),
        UmtsBand(14, 4117, 4143, -63.0, 30.0),
        UmtsBand(19, 712, 763, 735.0, -45.0),
        UmtsBand(20, 4512, 4638, -109.0, 41.0),
        UmtsBand(21, 862, 912, 1326.0, -48.0),
        UmtsBand(22, 4662, 5038, 2580.0, -100.0),
        UmtsBand(25, 5112, 5413, 910.0, -80.0),
    )

    fun wcdma(uarfcn: Int?): Freq {
        if (uarfcn == null || uarfcn < 0 || uarfcn == Int.MAX_VALUE) return NONE
        // Band VI sits inside band V's raster; prefer the narrower one like NR.
        val b = umts.filter { uarfcn in it.low..it.high }.minByOrNull { it.high - it.low } ?: return Freq(null, round1(uarfcn * 0.2), null, emptyList())
        val dl = uarfcn * 0.2 + b.offset
        return Freq(b.band, round1(dl), round1(dl + b.duplex))
    }

    /** NR frequency range label as Android names it. */
    fun nrRangeLabel(mhz: Double?): String = when {
        mhz == null -> "—"
        mhz < 1000 -> "LOW (FR1)"
        mhz < 6000 -> if (mhz < 3000) "MID (FR1)" else "HIGH (FR1)"
        else -> "MMWAVE (FR2)"
    }

    private fun round1(v: Double) = Math.round(v * 10.0) / 10.0
}
