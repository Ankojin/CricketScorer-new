package `in`.nrkmart.cricscore

import android.app.Application

class CricketScorerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TournamentRepository.init(this)
        GlobalPlayerRepository.init(this)
        GullyRulesRepository.init(this)
        CloudSyncManager.init(this)
    }
}
