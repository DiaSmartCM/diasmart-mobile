package com.diabeto.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    private val projectId: String = "project-d-r1997t"
) {
    class ErreurFirebase(message: String, val code: Int = 0, val identifiantsFaux: Boolean = false) : Exception(message)

    data class Session(val uid: String, val email: String, val idToken: String, val refreshToken: String, val expireA: Long)

    /** Date/heure Firestore (timestampValue), ex. "2026-10-04T12:00:00Z". */
    data class Horodatage(val iso: String)

    /** Une ecriture d'un lot atomique : document entier (set) ou suppression. */
    sealed class Ecriture {
        data class Poser(val chemin: String, val champs: Map<String, Any?>) : Ecriture()
        data class Supprimer(val chemin: String) : Ecriture()
        /** Change seulement ces champs d'un document existant. */
        data class Changer(val chemin: String, val champs: Map<String, Any?>) : Ecriture()
    }

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val racine = "projects/$projectId/databases/(default)/documents"
    private val base = "https://firestore.googleapis.com/v1/$racine"

    @Volatile var session: Session? = null
        private set

    // ── Comptes ─────────────────────────────────────────────────────────

    suspend fun connexion(email: String, motDePasse: String): Session =
        ouvrirSession("accounts:signInWithPassword", email, motDePasse)

    suspend fun inscription(email: String, motDePasse: String): Session =
        ouvrirSession("accounts:signUp", email, motDePasse)

    private suspend fun ouvrirSession(methode: String, email: String, motDePasse: String): Session {
        val corps = buildJsonObject {
            put("email", email.trim())
            put("password", motDePasse)
            put("returnSecureToken", true)
        }
        val o = appel("https://identitytoolkit.googleapis.com/v1/$methode?key=$apiKey", "POST", corps.toString(), jeton = null).jsonObject
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

    /** Vrai si l'email du compte a ete confirme (code a 6 chiffres). */
    suspend fun emailVerifie(): Boolean {
        val corps = buildJsonObject { put("idToken", jetonValide()) }
        val o = appel("https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=$apiKey", "POST", corps.toString(), jeton = null).jsonObject
        return (o["users"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("emailVerified")?.jsonPrimitive?.booleanOrNull == true
    }

    /** Envoie l'email "Mot de passe oublie" de Firebase. */
    suspend fun motDePasseOublie(email: String) {
        val corps = buildJsonObject {
            put("requestType", "PASSWORD_RESET")
            put("email", email.trim())
        }
        appel("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$apiKey", "POST", corps.toString(), jeton = null)
    }

    fun deconnexion() { session = null }

    /**
     * Reprend une session gardee sur ce PC a partir du jeton de reconnexion.
     * Erreur code 400 : jeton refuse (mot de passe du compte change, compte
     * supprime...) ; code 0 : pas d'Internet.
     */
    suspend fun reprendre(uid: String, email: String, refreshToken: String): Session {
        session = Session(uid, email, idToken = "", refreshToken = refreshToken, expireA = 0L)
        try {
            jetonValide(force = true)
        } catch (e: Exception) {
            session = null
            throw e
        }
        return session!!
    }

    /** Le jeton d'acces dure 1 h : on le renouvelle 5 min avant la fin (ou si [force]). */
    suspend fun jetonValide(force: Boolean = false): String {
        val s = session ?: throw ErreurFirebase("Non connecte", 401)
        if (!force && System.currentTimeMillis() < s.expireA - 5 * 60_000) return s.idToken
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

    // ── Firestore ───────────────────────────────────────────────────────

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

    /**
     * Requete sur une collection : filtres "champ == valeur", tri decroissant
     * optionnel, limite. Renvoie (id du document, champs).
     */
    suspend fun requete(
        parent: String?,
        collection: String,
        egalites: Map<String, Any?> = emptyMap(),
        triDecroissant: String? = null,
        limite: Int = 300,
        triCroissant: String? = null
    ): List<Pair<String, Map<String, Any?>>> {
        val q = buildJsonObject {
            put("structuredQuery", buildJsonObject {
                put("from", buildJsonArray { add(buildJsonObject { put("collectionId", collection) }) })
                if (egalites.isNotEmpty()) {
                    val filtres = egalites.map { (champ, v) ->
                        buildJsonObject {
                            put("fieldFilter", buildJsonObject {
                                put("field", buildJsonObject { put("fieldPath", champ) })
                                put("op", "EQUAL")
                                put("value", encoder(v))
                            })
                        }
                    }
                    put("where", if (filtres.size == 1) filtres[0] else buildJsonObject {
                        put("compositeFilter", buildJsonObject {
                            put("op", "AND")
                            put("filters", JsonArray(filtres))
                        })
                    })
                }
                val tri = triDecroissant ?: triCroissant
                if (tri != null) put("orderBy", buildJsonArray {
                    add(buildJsonObject {
                        put("field", buildJsonObject { put("fieldPath", tri) })
                        put("direction", if (triDecroissant != null) "DESCENDING" else "ASCENDING")
                    })
                })
                put("limit", limite)
            })
        }
        val url = if (parent.isNullOrBlank()) "$base:runQuery" else "$base/$parent:runQuery"
        val r = appel(url, "POST", q.toString(), jetonValide())
        return r.jsonArray.mapNotNull { el ->
            (el.jsonObject["document"] as? JsonObject)?.let { d ->
                d["name"]!!.jsonPrimitive.content.substringAfterLast('/') to champs(d)
            }
        }
    }

    /** Les [limite] derniers documents d'une sous-collection, tries par [champ] decroissant. */
    suspend fun derniers(parent: String, collection: String, champ: String, limite: Int): List<Map<String, Any?>> =
        requete(parent, collection, triDecroissant = champ, limite = limite).map { it.second }

    /** Ecrit plusieurs documents d'un coup : tout passe, ou rien. */
    suspend fun lot(ecritures: List<Ecriture>) {
        val corps = buildJsonObject {
            put("writes", buildJsonArray {
                ecritures.forEach { e ->
                    add(when (e) {
                        is Ecriture.Poser -> buildJsonObject {
                            put("update", buildJsonObject {
                                put("name", "$racine/${e.chemin}")
                                put("fields", JsonObject(e.champs.mapValues { (_, v) -> encoder(v) }))
                            })
                        }
                        is Ecriture.Supprimer -> buildJsonObject { put("delete", "$racine/${e.chemin}") }
                        is Ecriture.Changer -> buildJsonObject {
                            put("update", buildJsonObject {
                                put("name", "$racine/${e.chemin}")
                                put("fields", JsonObject(e.champs.mapValues { (_, v) -> encoder(v) }))
                            })
                            put("updateMask", buildJsonObject {
                                put("fieldPaths", JsonArray(e.champs.keys.map { JsonPrimitive(it) }))
                            })
                            put("currentDocument", buildJsonObject { put("exists", true) })
                        }
                    })
                }
            })
        }
        appel("$base:commit", "POST", corps.toString(), jetonValide())
    }

    // ── HTTP ────────────────────────────────────────────────────────────

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
            throw ErreurFirebase("Pas de connexion Internet. Vérifiez le réseau puis réessayez.")
        }
        if (rep.statusCode() !in 200..299) throw erreur(rep.statusCode(), rep.body())
        json.parseToJsonElement(rep.body())
    }

    private fun erreur(code: Int, corps: String): ErreurFirebase {
        val brut = runCatching {
            json.parseToJsonElement(corps).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull().orEmpty()
        fun e(m: String, faux: Boolean = false) = ErreurFirebase(m, code, faux)
        return when {
            brut.startsWith("INVALID_LOGIN_CREDENTIALS") || brut.startsWith("INVALID_PASSWORD") ||
                brut.startsWith("EMAIL_NOT_FOUND") -> e(
                "Email ou mot de passe incorrect. Les comptes créés avec Google n'ont pas de mot de passe : " +
                    "la version PC demande un compte email + mot de passe.", faux = true)
            brut.startsWith("INVALID_EMAIL") -> e("Adresse email invalide.")
            brut.startsWith("EMAIL_EXISTS") -> e("Un compte existe déjà avec cet email : utilisez « Se connecter ».")
            brut.startsWith("WEAK_PASSWORD") -> e("Mot de passe trop faible : au moins 6 caractères.")
            brut.startsWith("USER_DISABLED") -> e("Ce compte est désactivé.")
            brut.startsWith("TOO_MANY_ATTEMPTS") -> e("Trop d'essais. Réessayez dans quelques minutes.")
            code == 403 -> e("Accès refusé par les règles de sécurité.")
            code == 404 -> e("Introuvable.")
            else -> e("Erreur du serveur ($code). Réessayez.")
        }
    }

    companion object {
        private fun JsonObject.texte(cle: String) = this[cle]?.jsonPrimitive?.content.orEmpty()

        /** Valeur Kotlin -> valeur Firestore REST. */
        fun encoder(v: Any?): JsonObject = when (v) {
            null -> buildJsonObject { put("nullValue", JsonNull) }
            is String -> buildJsonObject { put("stringValue", v) }
            is Boolean -> buildJsonObject { put("booleanValue", v) }
            is Int, is Long -> buildJsonObject { put("integerValue", v.toString()) }
            is Double, is Float -> buildJsonObject { put("doubleValue", JsonPrimitive((v as Number).toDouble())) }
            is Horodatage -> buildJsonObject { put("timestampValue", v.iso) }
            is List<*> -> buildJsonObject {
                put("arrayValue", buildJsonObject { put("values", JsonArray(v.map { encoder(it) })) })
            }
            else -> error("Type non gere : ${v::class.simpleName}")
        }

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
