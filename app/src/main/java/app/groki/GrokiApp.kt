package app.groki

import android.app.Application
import app.groki.data.Graph
import app.groki.sync.Notifier
import app.groki.sync.SyncWorker

class GrokiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifier.createChannels(this)
        SyncWorker.schedule(this)
    }
}
