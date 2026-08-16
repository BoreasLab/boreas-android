package dev.boreaslab.boreas.service

/** Answers to a VPN consent request. */
public enum class ConsentOutcome { Granted, Denied, Unavailable }

/** Abstracts Android consent so the controller remains unit-testable. */
public interface ConsentGate {
    public suspend fun request(): ConsentOutcome
}
