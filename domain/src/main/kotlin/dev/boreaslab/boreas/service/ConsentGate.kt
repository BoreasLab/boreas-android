package dev.boreaslab.boreas.service

/** The three answers Android can give to a VPN consent request. A closed set. */
public enum class ConsentOutcome { Granted, Denied, Unavailable }

/**
 * Asking the user for permission to create a VPN interface.
 *
 * Behind an interface so the session controller stays a pure state machine that
 * unit tests drive without an Activity, a Looper, or a device. The Android
 * implementation lives in the app module, on the other side of this boundary.
 */
public interface ConsentGate {
    public suspend fun request(): ConsentOutcome
}
