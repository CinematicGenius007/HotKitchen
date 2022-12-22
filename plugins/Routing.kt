package hotkitchen.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import hotkitchen.models.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.util.*

@Serializable
data class Status(val message: String)

fun Application.configureRouting() {
    routing {
        route("/") {
            get {
                call.respondText("<h1>Hi this is Tony!</h1>")
            }
        }

        route("/signup") {
            post {
                val data = call.receiveText()
                val user = Json.decodeFromString<User>(data)
                log.info(user.toString())
                if (!Regex("^\\w+@\\w+\\.\\w+$").matches(user.email)) call.respondText("""{"status":"Invalid email"}""", status = HttpStatusCode.Forbidden)
                else if (
                    !Regex(".{6,}").matches(user.password)
                    || !Regex("[0-9]").containsMatchIn(user.password)
                    || !Regex("[a-zA-Z]").containsMatchIn(user.password)
                ) call.respondText("""{"status":"Invalid password"}""", status = HttpStatusCode.Forbidden)
                else if (DatabaseFactory.addUser(user)) {
                    val token = JWT.create()
                        .withAudience(environment.config.property("jwt.audience").getString())
                        .withIssuer(environment.config.property("jwt.issuer").getString())
                        .withClaim("email", user.email)
                        .withExpiresAt(Date(System.currentTimeMillis() + 60000))
                        .sign(Algorithm.HMAC256("secret"))
                    call.respondText("""{"token":"$token"}""", status = HttpStatusCode.OK)
                }
                else call.respondText("""{"status":"User already exists"}""", status = HttpStatusCode.Forbidden)
            }
        }

        route("/signin") {
            post {
                val data = call.receiveText();
                val user = Json.decodeFromString<User>(data)
                log.info(user.toString())
                if (DatabaseFactory.hasUser(user)) {
                    val token = JWT.create()
                        .withAudience(environment.config.property("jwt.audience").getString())
                        .withIssuer(environment.config.property("jwt.issuer").getString())
                        .withClaim("email", user.email)
                        .withExpiresAt(Date(System.currentTimeMillis() + 600000))
                        .sign(Algorithm.HMAC256("secret"))
                    call.respondText("""{"token":"$token"}""", status = HttpStatusCode.OK)
                }
                else call.respondText("""{"status":"Invalid email or password"}""", status = HttpStatusCode.Forbidden)
            }
        }

        authenticate("auth-jwt") {
            get("/validate") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal!!.payload.getClaim("email").asString()
                val user = DatabaseFactory.getUser(email)
                call.respondText("Hello, ${user?.userType} $email")
            }
        }

        authenticate("auth-jwt") {
            route("/me") {
                get {
                    val principal = call.principal<JWTPrincipal>()
                    val email = principal!!.payload.getClaim("email").asString()

                    val currentUserProfile: UserProfile? = DatabaseFactory.getUserProfile(email)
                    if (currentUserProfile != null) call.respondText(Json.encodeToString(currentUserProfile), status = HttpStatusCode.OK)
                    else call.respond(HttpStatusCode.BadRequest)
                }

                put {
                    val principal = call.principal<JWTPrincipal>()
                    val email = principal!!.payload.getClaim("email").asString()

                    val data = call.receiveText()
                    val userProfile = Json.decodeFromString<UserProfile>(data)

                    if (email != userProfile.email)
                        call.respond(HttpStatusCode.BadRequest)
                    else {
                        DatabaseFactory.putUserProfile(userProfile)
                        call.respond(HttpStatusCode.OK)
                    }
                }

                delete {
                    val principal = call.principal<JWTPrincipal>()
                    val email = principal!!.payload.getClaim("email").asString()

                    if (DatabaseFactory.getUserProfile(email) == null)
                        call.respond(HttpStatusCode.BadRequest)
                    else {
                        DatabaseFactory.deleteUserProfile(email)
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }

        authenticate("auth-jwt") {
            route("/categories") {
                get {
                    val id = call.request.queryParameters["id"]?.toInt()
                    if (id != null) {
                        val category = DatabaseFactory.getCategory(id)
                        if (category == null) call.respond(HttpStatusCode.BadRequest)
                        else call.respondText(Json.encodeToString(category), status = HttpStatusCode.OK)
                    } else {
                        val categories = DatabaseFactory.getAllCategories()
                        call.respondText(Json.encodeToString(categories), status = HttpStatusCode.OK)
                    }
                }

                post {
                    val principal = call.principal<JWTPrincipal>()
                    val email = principal!!.payload.getClaim("email").asString()

                    val user = DatabaseFactory.getUser(email)

                    val data = call.receiveText()
                    val category = Json.decodeFromString<Category>(data)

                    if (user == null) call.respond(HttpStatusCode.BadRequest)
                    else if (user.userType != "staff") call.respondText("""{"status":"Access denied"}""", status = HttpStatusCode.Forbidden)
                    else if (DatabaseFactory.getCategory(category.categoryId) != null) call.respond(HttpStatusCode.BadRequest)
                    else {
                        DatabaseFactory.postCategories(category)
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            route("/meals") {
                get {
                    val id = call.request.queryParameters["id"]?.toInt()
                    if (id != null) {
                        val meal = DatabaseFactory.getMeal(id)
                        if (meal == null) call.respond(HttpStatusCode.BadRequest)
                        else call.respondText(Json.encodeToString(meal), status = HttpStatusCode.OK)
                    } else {
                        val meals = DatabaseFactory.getAllMeals()
                        call.respondText(Json.encodeToString(meals), status = HttpStatusCode.OK)
                    }
                }

                post {
                    val principal = call.principal<JWTPrincipal>()
                    val email = principal!!.payload.getClaim("email").asString()

                    val user = DatabaseFactory.getUser(email)

                    val data = call.receiveText()
                    val meal = Json.decodeFromString<Meal>(data)

                    if (user == null) call.respond(HttpStatusCode.BadRequest)
                    else if (user.userType != "staff") call.respondText("""{"status":"Access denied"}""", status = HttpStatusCode.Forbidden)
                    else if (DatabaseFactory.getMeal(meal.mealId) != null) call.respond(HttpStatusCode.BadRequest)
                    else {
                        DatabaseFactory.postMeal(meal)
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }

        authenticate("auth-jwt") {
            route("/order") {
                post {
                    val principal = call.principal<JWTPrincipal>()
                    val email = principal!!.payload.getClaim("email").asString()

                    val data = call.receiveText()
                    val mealsIds = Json.decodeFromString<IntArray>(data)
                    val order = DatabaseFactory.postOrder(email, mealsIds)
                    println(order.toString())
                    if (order != null) call.respondText(Json.encodeToString(order), status = HttpStatusCode.OK)
                    else call.respond(HttpStatusCode.BadRequest)
                }

                post("/{orderId}/markReady") {
                    val principal = call.principal<JWTPrincipal>()
                    val email = principal!!.payload.getClaim("email").asString()

                    val user = DatabaseFactory.getUser(email)
                    val parameter = call.parameters["orderId"]?.toInt()

                    // call.respondText("""{"status":"Access denied"}""", status = HttpStatusCode.Forbidden)

                    if (parameter == null) call.respond(HttpStatusCode.BadRequest)
                    if (user == null || user.userType != "staff") call.respondText("""{"status":"Access denied"}""", status = HttpStatusCode.Forbidden)
                    else {
                        if (DatabaseFactory.updateOrderStatus(parameter ?: 0)) call.respond(HttpStatusCode.OK)
                        else call.respond(HttpStatusCode.BadRequest)
                    }
                }
            }

            get("/orderHistory") {
                call.respondText(Json.encodeToString(DatabaseFactory.getAllOrders()), status = HttpStatusCode.OK)
            }

            get("/orderIncomplete") {
                call.respondText(Json.encodeToString(DatabaseFactory.getAllIncompleteOrders()), status = HttpStatusCode.OK)
            }
        }
    }
}
