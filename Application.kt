package hotkitchen

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import hotkitchen.plugins.DatabaseFactory
import hotkitchen.plugins.configureRouting
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module(testing: Boolean = false) {
    /*install(Authentication) {
        basic("myAuth") {
            realm = "Access to the '/page' path"
            validate { credentials ->
                if (credentials.name == "Admin" && credentials.password == "2425") {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
        basic("myAuth2") {
            realm = "Access to the '/page' path"
            validate { credentials ->
                if (credentials.name == "Admin2" && credentials.password == "1213") {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }*/
    authentication {
        jwt("auth-jwt") {
            val jwtAudience = environment.config.property("jwt.audience").getString()
            realm = environment.config.property("jwt.realm").getString()
            verifier(
                JWT
                    .require(Algorithm.HMAC256("secret"))
                    .withAudience(jwtAudience)
                    .withIssuer(environment.config.property("jwt.issuer").getString())
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) JWTPrincipal(credential.payload) else null
            }
        }
    }
    DatabaseFactory.init()
    configureRouting()
}