package com.example.cricketscorer

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object NearbyManager {
    private const val SERVICE_ID = "com.example.cricketscorer.SYNC.v1.20" // Bumped to 1.20
    private val STRATEGY_TYPE = Strategy.P2P_STAR
    
    private val _connectedEndpoints = MutableStateFlow<Set<String>>(emptySet())
    val connectedEndpoints = _connectedEndpoints.asStateFlow()

    private val gson = Gson()
    private var matchUpdateCallback: ((Match) -> Unit)? = null
    private var tournamentUpdateCallback: ((String) -> Unit)? = null
    private var onConnectedCallback: (() -> Unit)? = null
    private val endpointNames = mutableMapOf<String, String>()

    fun setMatchUpdateCallback(callback: (Match) -> Unit) {
        matchUpdateCallback = callback
    }

    fun setTournamentUpdateCallback(callback: (String) -> Unit) {
        tournamentUpdateCallback = callback
    }

    fun setOnConnectedCallback(callback: () -> Unit) {
        onConnectedCallback = callback
    }

    fun startSync(context: Context, name: String) {
        stopAll(context)
        startBroadcasting(context, name)
        startDiscovering(context)
    }

    fun startBroadcasting(context: Context, matchName: String) {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY_TYPE).build()
        val appContext = context.applicationContext
        Toast.makeText(appContext, "Broadcasting Live Score...", Toast.LENGTH_SHORT).show()
        Nearby.getConnectionsClient(appContext)
            .startAdvertising(matchName, SERVICE_ID, createConnectionLifecycleCallback(appContext), options)
            .addOnSuccessListener { Log.d("Nearby", "Advertising started as $matchName") }
            .addOnFailureListener { Log.e("Nearby", "Advertising failed", it) }
    }

    fun startDiscovering(context: Context) {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY_TYPE).build()
        val appContext = context.applicationContext
        Toast.makeText(appContext, "Searching for Nearby Scorer...", Toast.LENGTH_SHORT).show()
        Nearby.getConnectionsClient(appContext)
            .startDiscovery(SERVICE_ID, createEndpointDiscoveryCallback(appContext), options)
            .addOnSuccessListener { Log.d("Nearby", "Discovery started") }
            .addOnFailureListener { Log.e("Nearby", "Discovery failed", it) }
    }

    fun stopAll(context: Context) {
        Nearby.getConnectionsClient(context.applicationContext).apply {
            stopAdvertising()
            stopDiscovery()
            stopAllEndpoints()
        }
        _connectedEndpoints.value = emptySet()
        endpointNames.clear()
    }

    fun broadcastTournament(context: Context, tournament: Tournament) {
        try {
            val json = gson.toJson(tournament)
            val payloadObj = NearbyPayload("FULL_SYNC", json)
            sendWrapper(context, payloadObj)
        } catch (e: Exception) {
            Log.e("Nearby", "Error broadcasting tournament", e)
        }
    }

    fun broadcastMatch(context: Context, match: Match) {
        try {
            val json = gson.toJson(match)
            val payloadObj = NearbyPayload("MATCH_UPDATE", json)
            sendWrapper(context, payloadObj)
        } catch (e: Exception) {
            Log.e("Nearby", "Error broadcasting match", e)
        }
    }

    private fun sendWrapper(context: Context, payloadObj: NearbyPayload) {
        val endpoints = _connectedEndpoints.value
        if (endpoints.isEmpty()) return
        
        try {
            val wrapperJson = gson.toJson(payloadObj)
            val bytes = wrapperJson.toByteArray(Charsets.UTF_8)
            val payload = Payload.fromBytes(bytes)
            Nearby.getConnectionsClient(context.applicationContext)
                .sendPayload(endpoints.toList(), payload)
                .addOnFailureListener { e -> Log.e("Nearby", "Failed to send payload", e) }
        } catch (e: Exception) {
            Log.e("Nearby", "Error sending payload wrapper", e)
        }
    }

    private fun createConnectionLifecycleCallback(context: Context) = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d("Nearby", "Connection initiated with $endpointId (${info.endpointName})")
            endpointNames[endpointId] = info.endpointName
            Nearby.getConnectionsClient(context.applicationContext).acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val name = endpointNames[endpointId] ?: endpointId
            if (result.status.isSuccess) {
                Log.d("Nearby", "Connected successfully to $endpointId")
                Toast.makeText(context.applicationContext, "CONNECTED to $name 🟢", Toast.LENGTH_SHORT).show()
                _connectedEndpoints.value += endpointId
                onConnectedCallback?.invoke()
            } else {
                Log.e("Nearby", "Connection failed to $endpointId: ${result.status.statusMessage}")
                Toast.makeText(context.applicationContext, "Connection failed: ${result.status.statusMessage}", Toast.LENGTH_LONG).show()
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("Nearby", "Disconnected from $endpointId")
            _connectedEndpoints.value -= endpointId
            endpointNames.remove(endpointId)
        }
    }

    private fun createEndpointDiscoveryCallback(context: Context) = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d("Nearby", "Found scorer: ${info.endpointName}, requesting connection...")
            Toast.makeText(context.applicationContext, "Found Scorer: ${info.endpointName}. Connecting...", Toast.LENGTH_SHORT).show()
            Nearby.getConnectionsClient(context.applicationContext).requestConnection(
                android.os.Build.MODEL, // Use device name as identification
                endpointId,
                createConnectionLifecycleCallback(context)
            ).addOnFailureListener { Log.e("Nearby", "Failed to request connection to $endpointId", it) }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d("Nearby", "Lost endpoint $endpointId")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val wrapperJson = String(payload.asBytes()!!)
                try {
                    val wrapper = gson.fromJson(wrapperJson, NearbyPayload::class.java)
                    when (wrapper.type) {
                        "MATCH_UPDATE" -> {
                            val match = gson.fromJson(wrapper.json, Match::class.java)
                            matchUpdateCallback?.invoke(match)
                        }
                        "FULL_SYNC" -> {
                            tournamentUpdateCallback?.invoke(wrapper.json)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Nearby", "Failed to parse payload wrapper from $endpointId", e)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }
}

data class NearbyPayload(val type: String, val json: String)
