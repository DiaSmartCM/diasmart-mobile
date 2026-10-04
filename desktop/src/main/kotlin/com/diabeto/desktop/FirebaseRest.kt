package com.diabeto.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Acces a Firebase (Auth + Firestore) par les API REST officielles.
 * Le SDK Firebase Android ne tourne pas sur PC ; les API REST appliquent
 * exactement les memes regles de securite Firestore que l'app mobile.
 * La cle est la cle web publique du projet (deja dans le site).
 */
class FirebaseRest(
    private val apiKey: String = "AIzaSyCMx0rYw9rua20M_SPJW6LtXZ8xIbTcHvo",
    projectId: String = "project-d-r1997t"
) {
    class ErreurFirebase(message: String, val code: Int = 0) : Exception(message)

    data class Session(val uid: String, val email: String, val idToken: String, val refreshToken: String, val expireA: Long)

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val base = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"

    @Volatile var session: Session? = null
        private set

    suspend fun connexion(email: String, motDePasse: String): Session {
        val corps = buildJsonObject {
            put("email", email.trim())
            put("password", motDePasse)
            put("returnSecureToken", true)
        }
        val r = appel("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey", "POST", corps.toString(), jeton = null)
        val o = r.jsonObject
        val s = Session(
            uid = o.texte("localId"),
            email = o.texte("email"),
            idToken = o.texte("idToken"),
            refreshToken = o.texte("refreshToken"),
            expireA = System.currentTimeMillis() + (o.texte("expiresIn").toLongOrNull() ?: 3600L) * 1000
        )
        session = s
        return s
    }

    fun deconnexion() { session = null }

    /** Le jeton d'acces dure 1 h : on le renouvelle 5 min avant la fin. */
    private suspend fun jetonValide(): String {
        val s = session ?: throw ErreurFirebase("Non connecte", 401)
        if (System.currentTimeMillis() < s.expireA - 5 * 60_000) return s.idToken
        val corps = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(s.refreshToken, Charsets.UTF_8)
        val o = appel("https://securetoken.googleapis.com/v1/token?key=$apiKey", "POST", corps, jeton = null,
            typeContenu = "application/x-www-form-urlencoded").jsonObject
        val nouvelle = s.copy(
            idToken = o.texte("id_token"),
            refreshToken = o.texte("refresh_token"),
            expireA = System.currentTimeMillis() + (o.texte("expires_in").toLongOrNull() ?: 3600L) * 1000
        )
        session = nouvelle
        return nouvelle.idToken
    }

    /** Un document, ou null s'il n'existe pas. Renvoie ses champs. */
    suspend fun document(chemin: String): Map<String, Any?>? = try {
        champs(appel("$base/$chemin", "GET", null, jetonValide()).jsonObject)
    } catch (e: ErreurFirebase) {
        if (e.code == 404) null else throw e
    }

    /** Tous les documents d'une collection (jusqu'a 300). */
    suspend fun collection(chemin: String): List<Map<String, Any?>> {
        val o = appel("$base/$chemin?pageSize=300", "GET", null, jetonValide()).jsonObject
        return (o["documents"] as? JsonArray).orEmpty().map { champs(it.jsonObject) }
    }

    /** Les [limite] derniers documents d'une sous-collection, tries par [champ] decroissant. */
    suspend fun derniers(parent: String, collection: String, champ: String, limite: Int): List<Map<String, Any?>> {
        val requete = buildJsonObject {
            put("structuredQuery", buildJsonObject {
                put("from", buildJsonArray { add(buildJsonObject { put("collectionId", collection) }) })
                put("orderBy", buildJsonArray {
                    add(buildJsonObject {
                        put("field", buildJsonObject { put("fieldPath", champ) })
                        put("direction", "DESCENDING")
                    })
                })
                put("limit", limite)
            })
        }
        val r = appel("$base/$parent:runQuery", "POST", requete.toString(), jetonValide())
        return r.jsonArray.mapNotNull { (it.jsonObject["document"] as? JsonObject)?.let(::champs) }
    }

    private suspend fun appel(
        url: String, methode: String, corps: String?, jeton: String?,
        typeContenu: String = "application/json"
    ): JsonElement = withContext(Dispatchers.IO) {
        val b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
        if (jeton != null) b.header("Authorization", "Bearer $jeton")
        if (corps != null) b.header("Content-Type", typeContenu).method(methode, HttpRequest.BodyPublishers.ofString(corps))
        else b.method(methode, HttpRequest.BodyPublishers.noBody())
        val rep = try {
            http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        } catch (e: java.io.IOException) {
            throw ErreurFirebase("Pas de connexion Internet. Verifiez le reseau puis reessayez.")
        }
        if (rep.statusCode() !in 200..299) throw ErreurFirebase(messageErreur(rep.statusCode(), rep.body()), rep.statusCode())
        json.parseToJsonElement(rep.body())
    }

    private fun messageErreur(code: Int, corps: String): String {
        val brut = runCatching {
            json.parseToJsonElement(corps).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull().orEmpty()
        return when {
            brut.startsWith("INVALID_LOGIN_CREDENTIALS") || brut.startsWith("INVALID_PASSWORD") ||
                brut.startsWith("EMAIL_NOT_FOUND") || brut.startsWith("INVALID_EMAIL") ->
                "Email ou mot de passe incorrect. Les comptes crees avec Google n'ont pas de mot de passe : " +
                    "la version PC demande pour l'instant un compte email + mot de passe."
            brut.startsWith("USER_DISABLED") -> "Ce compte est desactive."
            brut.startsWith("TOO_MANY_ATTEMPTS") -> "Trop d'essais. Reessayez dans quelques minutes."
            code == 403 -> "Acces refuse par les regles de securite."
            code == 404 -> "Introuvable."
            else -> "Erreur du serveur ($code). Reessayez."
        }
    }

    companion object {
        private fun JsonObject.texte(cle: String) = this[cle]?.jsonPrimitive?.content.orEmpty()

        /** Convertit les champs Firestore REST ({"stringValue": ...}) en valeurs Kotlin. */
        fun champs(doc: JsonObject): Map<String, Any?> {
            val f = doc["fields"] as? JsonObject ?: return emptyMap()
            return f.mapValues { (_, v) -> valeur(v.jsonObject) }
        }

        private fun valeur(v: JsonObject): Any? = when {
            "stringValue" in v -> v["stringValue"]!!.jsonPrimitive.content
            "integerValue" in v -> v["integerValue"]!!.jsonPrimitive.content.toLongOrNull()
            "doubleValue" in v -> v["doubleValue"]!!.jsonPrimitive.doubleOrNull
            "booleanValue" in v -> v["booleanValue"]!!.jsonPrimitive.booleanOrNull
            "timestampValue" in v -> v["timestampValue"]!!.jsonPrimitive.content
            "mapValue" in v -> (v["mapValue"]!!.jsonObject["fields"] as? JsonObject)
                ?.mapValues { (_, x) -> valeur(x.jsonObject) } ?: emptyMap<String, Any?>()
            "arrayValue" in v -> (v["arrayValue"]!!.jsonObject["values"] as? JsonArray)
                ?.map { valeur(it.jsonObject) } ?: emptyList<Any?>()
            else -> null
        }
    }
}
