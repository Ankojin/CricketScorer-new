package `in`.nrkmart.cricscore

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CricketScorerApp : Application() {
    private val accountScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        TournamentRepository.init(this)
        GlobalPlayerRepository.init(this)
        GullyRulesRepository.init(this)
        CloudSyncManager.init(this)
        switchLocalProfile(CloudSyncManager.session.value?.userId)
        accountScope.launch {
            CloudSyncManager.session.collect { session ->
                switchLocalProfile(session?.userId)
            }
        }
        VoiceCommentaryManager.init(this)
    }

    private fun switchLocalProfile(userId: String?) {
        TournamentRepository.switchProfile(userId)
        GlobalPlayerRepository.switchProfile(userId)
    }
}
