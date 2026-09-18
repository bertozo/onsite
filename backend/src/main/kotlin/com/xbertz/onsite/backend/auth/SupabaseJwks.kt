package com.xbertz.onsite.backend.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64

/**
 * Modern Supabase projects sign access tokens with an asymmetric ES256 key rather than a
 * shared HS256 secret, and publish the current public key(s) at a well-known JWKS URL,
 * identified by "kid". This fetches that JWKS once at startup and reconstructs the EC
 * public key so java-jwt can verify tokens without ever seeing a private key.
 */
@Serializable
private data class Jwk(
    val kty: String,
    val crv: String? = null,
    val x: String? = null,
    val y: String? = null,
    val kid: String? = null,
    val use: String? = null,
)

@Serializable
private data class JwksResponse(val keys: List<Jwk>)

fun fetchSupabaseEcPublicKey(projectUrl: String): ECPublicKey {
    val jwksUrl = "$projectUrl/auth/v1/.well-known/jwks.json"
    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder(URI.create(jwksUrl)).GET().build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) {
        "Failed to fetch Supabase JWKS from $jwksUrl (HTTP ${response.statusCode()})"
    }

    val jwks = Json { ignoreUnknownKeys = true }.decodeFromString<JwksResponse>(response.body())
    val key = jwks.keys.firstOrNull { it.kty == "EC" && (it.use == null || it.use == "sig") }
        ?: error("No EC signing key found in Supabase JWKS at $jwksUrl")
    check(key.crv == "P-256") { "Unsupported curve '${key.crv}' in Supabase JWKS key '${key.kid}'" }
    val x = key.x ?: error("Supabase JWKS key '${key.kid}' is missing 'x'")
    val y = key.y ?: error("Supabase JWKS key '${key.kid}' is missing 'y'")

    return buildEcPublicKey(x, y)
}

private fun buildEcPublicKey(xB64Url: String, yB64Url: String): ECPublicKey {
    val decoder = Base64.getUrlDecoder()
    val x = BigInteger(1, decoder.decode(xB64Url))
    val y = BigInteger(1, decoder.decode(yB64Url))

    val params = AlgorithmParameters.getInstance("EC").apply {
        init(ECGenParameterSpec("secp256r1"))
    }
    val ecParameterSpec = params.getParameterSpec(ECParameterSpec::class.java)
    val pubKeySpec = ECPublicKeySpec(ECPoint(x, y), ecParameterSpec)
    return KeyFactory.getInstance("EC").generatePublic(pubKeySpec) as ECPublicKey
}
