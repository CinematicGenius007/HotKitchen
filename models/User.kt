package hotkitchen.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table

@Serializable
data class User(var email: String, var userType: String = "something", var password: String) {
    override fun toString(): String = "{$email : $userType : $password}"
}

@Serializable
data class UserProfile(
    var name: String,
    var userType: String,
    var phone: String,
    var email: String,
    var address: String,
    ) {
    override fun toString(): String = "UserProfile: $name, $userType, $phone, $email, $address"
}

object Users : Table() {
    val email = varchar("email", 100)
    val userType = varchar("userType", 50)
    val password = varchar("password", 100)

    override val primaryKey = PrimaryKey(email)
}

object UserProfiles : IntIdTable() {
    val name = varchar("name", 100)
    val userType = varchar("userType", 100)
    val phone = varchar("phone", 14)
    val email = varchar("email", 100)
    val address = varchar("address", 150)
}