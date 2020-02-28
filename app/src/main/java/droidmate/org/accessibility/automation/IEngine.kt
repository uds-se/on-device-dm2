package droidmate.org.accessibility.automation

interface IEngine {
    companion object {
        internal const val debug = false
        internal const val debugFetch = false
        internal val TAG = IEngine::class.java.simpleName
    }
}