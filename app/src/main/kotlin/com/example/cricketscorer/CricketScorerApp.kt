package com.example.cricketscorer

import android.app.Application

class CricketScorerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TournamentRepository.init(this)
    }
}
