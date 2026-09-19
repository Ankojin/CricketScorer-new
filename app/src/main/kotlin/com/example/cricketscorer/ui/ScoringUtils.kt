package com.example.cricketscorer.ui

import com.example.cricketscorer.*

data class InningsStats(
    val singlesRuns: Int = 0,
    val doublesRuns: Int = 0,
    val triplesRuns: Int = 0,
    val foursRuns: Int = 0,
    val sixesRuns: Int = 0,
    val otherBatRuns: Int = 0,
    val dots: Int = 0,
    val extrasRuns: Int = 0,
    val wickets: Int = 0,
    val droppedCatches: Int = 0,
    val wideCount: Int = 0,
    val noBallCount: Int = 0,
    val ppRuns: Int = 0,
    val ppWickets: Int = 0,
    val midRuns: Int = 0,
    val midWickets: Int = 0,
    val finRuns: Int = 0,
    val finWickets: Int = 0,
    val boundaryRuns: Int = 0,
    val dotPercent: Int = 0,
    val totalLegalBalls: Int = 0,
    val hasMid: Boolean = false,
    val hasFin: Boolean = false
)

fun calculateInningsStats(balls: List<Ball>): InningsStats {
    var sR = 0; var dR = 0; var tR = 0; var fR = 0; var siR = 0; var oR = 0; var dots = 0; var exR = 0; var w = 0; var dC = 0
    var wC = 0; var nbC = 0
    
    var ppR = 0; var ppW = 0
    var midR = 0; var midW = 0
    var finR = 0; var finW = 0
    var pB = 0; var lB = 0
    
    balls.forEach { ball ->
        if (ball.isAdjustment) return@forEach

        val ballTotal = ball.runs + ball.extraRuns
        val isW = ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT
        
        if (pB < 36) { // Powerplay: 1-6 Overs
            ppR += ballTotal
            if (isW) ppW++
        } else if (pB < 90) { // Middle: 7-15 Overs
            midR += ballTotal
            if (isW) midW++
        } else { // Death: 16-20 Overs
            finR += ballTotal
            if (isW) finW++
        }

        if (ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT) w++
        if (ball.extrasType != ExtrasType.NONE && ball.extrasType != ExtrasType.GRANTED) exR += ball.extraRuns
        
        if (ball.extrasType == ExtrasType.WIDE) wC++
        if (ball.extrasType == ExtrasType.NO_BALL) nbC++
        
        if (ball.isDroppedCatch) dC++
        
        val runs = ball.runs
        if (ball.isPhysicalBall) pB++
        if (ball.isLegalBall) lB++

        when (runs) {
            0 -> if (ball.isLegalBall && ball.extraRuns == 0) dots++
            1 -> sR += 1
            2 -> dR += 2
            3 -> tR += 3
            4 -> fR += 4
            6 -> siR += 6
            else -> if (runs > 0) oR += runs
        }
    }
    
    val dP = if (lB > 0) (dots * 100) / lB else 0
    
    return InningsStats(
        sR, dR, tR, fR, siR, oR, dots, exR, w, dC, wC, nbC,
        ppR, ppW, midR, midW, finR, finW,
        fR + siR, dP, lB,
        pB > 36, pB > 90
    )
}

fun recoverName(id: String?, match: Match, defaultName: String = "Player"): String {
    if (id.isNullOrEmpty()) return defaultName
    
    match.teamA.players.find { it.id == id }?.name?.let { return it }
    match.teamB.players.find { it.id == id }?.name?.let { return it }
    
    if (id.length < 30 || id.contains(" ")) return id
    
    return "Guest Player"
}

fun calculatePartnerships(balls: List<Ball>, match: Match): List<Partnership> {
    val partnerships = mutableListOf<Partnership>()
    if (balls.isEmpty()) return partnerships
    
    var currentB1Id: String? = null
    var currentB2Id: String? = null
    var runs1 = 0; var balls1 = 0
    var runs2 = 0; var balls2 = 0
    var pExtras = 0
    
    balls.forEach { ball ->
        if (currentB1Id == null) {
            currentB1Id = ball.strikerId
            currentB2Id = ball.nonStrikerId
        }
        
        if (ball.strikerId == currentB1Id) {
            runs1 += ball.runs
            balls1 += if (ball.extrasType != ExtrasType.WIDE) 1 else 0
        } else if (ball.strikerId == currentB2Id) {
            runs2 += ball.runs
            balls2 += if (ball.extrasType != ExtrasType.WIDE) 1 else 0
        }
        
        pExtras += ball.extraRuns
        
        if (ball.wicketType != WicketType.NONE && ball.wicketType != WicketType.RETIRED_HURT) {
            val b1Name = recoverName(currentB1Id, match, "Striker")
            val b2Name = recoverName(currentB2Id, match, "Non-Striker")
            partnerships.add(Partnership(
                currentB1Id ?: "", b1Name, runs1, balls1,
                currentB2Id ?: "", b2Name, runs2, balls2,
                runs1 + runs2 + pExtras, balls1 + balls2
            ))
            currentB1Id = null; currentB2Id = null
            runs1 = 0; balls1 = 0; runs2 = 0; balls2 = 0
            pExtras = 0
        }
    }
    
    if (currentB1Id != null) {
        val b1Name = recoverName(currentB1Id, match, "Striker")
        val b2Name = recoverName(currentB2Id, match, "Non-Striker")
        partnerships.add(Partnership(
            currentB1Id ?: "", b1Name, runs1, balls1,
            currentB2Id ?: "", b2Name, runs2, balls2,
            runs1 + runs2 + pExtras, balls1 + balls2
        ))
    }
    
    return partnerships
}
