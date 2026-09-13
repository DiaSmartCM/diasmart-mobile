package com.diabeto.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emet Unit toutes les [intervalMs]. Sert a re-evaluer une condition
 * dependante du temps (ex: "vu il y a moins de 45s") sans attendre un
 * nouvel evenement Firestore, sans lecture supplementaire.
 */
fun tickerFlow(intervalMs: Long): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(intervalMs)
    }
}
