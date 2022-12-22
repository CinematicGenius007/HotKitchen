package hotkitchen.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table

@Serializable
data class Order(
    val orderId: Int,
    val userEmail: String,
    val mealsIds: List<Int>,
    val price: Float,
    val address: String,
    val status: String,
) {
    override fun toString(): String = "Order($orderId, $userEmail, $mealsIds, $price, $address, $status)"
}

object Orders : Table() {
    val orderId = integer("orderId").autoIncrement()
    val userEmail = varchar("userEmail", 100)
    val mealsIds = varchar("mealsIds", 200)
    val price = float("price")
    val address = varchar("address", 200)
    val status = varchar("status", 100)

    override val primaryKey: PrimaryKey = PrimaryKey(orderId)
    fun mealsIdsToString(mealsIds: IntArray): String = mealsIds.joinToString("+")
    fun toMealsIds(mealsIds: String): List<Int> = mealsIds.split("+").map { it.toInt() }.toList()
}

@Serializable
data class Category(
    val categoryId: Int,
    val title: String,
    val description: String
) {
    override fun toString(): String = "Category($categoryId, $title, $description)"
}

object Categories : Table() {
    val categoryId = integer("categoryId")
    val title = varchar("title", 100)
    val description = varchar("description", 200)

    override val primaryKey: PrimaryKey = PrimaryKey(categoryId)
}

@Serializable
data class Meal (
    val mealId: Int,
    val title: String,
    val price: Float,
    val imageUrl: String,
    val categoryIds: Array<Int>
) {
    override fun toString(): String = """Meal($mealId, $title, $price, $imageUrl, ${categoryIds.joinToString("+")})"""

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Meal

        if (mealId != other.mealId) return false
        if (title != other.title) return false
        if (price != other.price) return false
        if (imageUrl != other.imageUrl) return false
        if (!categoryIds.contentEquals(other.categoryIds)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = mealId
        result = 31 * result + title.hashCode()
        result = 31 * result + price.hashCode()
        result = 31 * result + imageUrl.hashCode()
        result = 31 * result + categoryIds.contentHashCode()
        return result
    }
}

object Meals : Table() {
    val mealId = integer("mealId")
    val title = varchar("title", 100)
    val price = float("price")
    val imageUrl = varchar("imageUrl", 200)
    val categoryIds = varchar("categoryIds", 100)

    fun getCategoryIdString(categories: Array<Int>): String = categories.joinToString("+")
    fun toCategoryId(categories: String): Array<Int> = categories.split("+").map { it.toInt() }.toTypedArray()
}