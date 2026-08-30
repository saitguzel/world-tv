package com.worldtv.core.common

/** Minimal result wrapper for operations the UI needs to render a failure for. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val message: String, val cause: Throwable? = null) : Outcome<Nothing>
    data object Loading : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
    Outcome.Loading -> Outcome.Loading
}

fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Success)?.value
