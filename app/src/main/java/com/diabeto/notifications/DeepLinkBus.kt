package com.diabeto.notifications

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bus singleton qui transporte les deep-links issus du tap utilisateur sur une
 * notification systeme vers la navigation Compose.
 *
 * `MainActivity` lit les extras de l'intent (`navigate_to`, `conversation_id`...)
 * dans `onCreate` et `onNewIntent`, et appelle [post]. La couche [DiabetoNavigation]
 * collecte le flow et appelle `navController.navigate(...)` une fois que
 * l'utilisateur est passé le splash + l'authentification.
 *
 * `replay = 1` permet de ne pas perdre l'evenement s'il arrive avant que le
 * collector soit pret (cas typique : app froide → MainActivity → onCreate
 * post() avant que DiabetoNavigation ait monte sa LaunchedEffect).
 */
data class DeepLinkEvent(
    val target: String,           // "messagerie" | "community" | "mes_avis" | ...
    val conversationId: String? = null
)

object DeepLinkBus {
    private val _events = MutableSharedFlow<DeepLinkEvent>(
        replay = 1,
        extraBufferCapacity = 4
    )
    val events: SharedFlow<DeepLinkEvent> = _events.asSharedFlow()

    fun post(event: DeepLinkEvent) {
        _events.tryEmit(event)
    }

    /** Vide le buffer apres consommation pour eviter de re-naviguer sur recomposition. */
    fun clearReplayCache() {
        _events.resetReplayCache()
    }
}
