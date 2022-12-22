package hotkitchen.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import hotkitchen.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.random.Random

object DatabaseFactory {
    fun init() {
        val database = Database.connect(hikari())
        transaction(database) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(Users)
            SchemaUtils.create(UserProfiles)
            SchemaUtils.create(Categories)
            SchemaUtils.create(Meals)
            SchemaUtils.create(Orders)
        }
    }

    fun postOrder(email: String, mid: IntArray): Order? {
        val order: Order?
        var success = true
        val userAddress = getUserProfile(email)?.address
        if (userAddress == null) success = false
        var totalPrice = 0.0f
        transaction {
            mid.forEach {
                val currentMeal = getMeal(it)
                if (currentMeal == null) success = false
                else totalPrice += currentMeal.price
            }
        }

        if (!success) return null

        order = Order(
            Random.nextInt(129387123),
            email,
            mid.toList(),
            totalPrice,
            userAddress ?: "",
            "COOK"
        )

        transaction {
            Orders.insert {
                it[orderId] = order.orderId
                it[userEmail] = order.userEmail
                it[mealsIds] = mealsIdsToString(mid)
                it[price] = order.price
                it[address] = order.address
                it[status] = order.status
            }
        }
        return order
    }

    fun updateOrderStatus(orderId: Int): Boolean {
        var status = true
        transaction {
            val order = Orders.select { Orders.orderId eq orderId }.firstOrNull()
            if (order == null) status = false
        }

        if (!status) return false

        transaction {
            Orders.update({ Orders.orderId eq orderId }) {
                it[Orders.status] = "COMPLETE"
            }
        }

        return status
    }

    fun getAllOrders(): List<Order>? {
        var orders: List<Order>? = emptyList()
        transaction {
            orders = Orders.selectAll().map {
                Order(
                    it[Orders.orderId],
                    it[Orders.userEmail],
                    Orders.toMealsIds(it[Orders.mealsIds]),
                    it[Orders.price],
                    it[Orders.address],
                    it[Orders.status]
                )
            }
        }
        return orders
    }

    fun getAllIncompleteOrders(): List<Order>? {
        var orders: List<Order>? = emptyList()
        transaction {
            orders = Orders.select { Orders.status eq "COOK" }.map {
                Order(
                    it[Orders.orderId],
                    it[Orders.userEmail],
                    Orders.toMealsIds(it[Orders.mealsIds]),
                    it[Orders.price],
                    it[Orders.address],
                    it[Orders.status]
                )
            }
        }
        return orders
    }

    fun addUser(user: User): Boolean {
        var added = true
        transaction {
            val alice = Users.select { Users.email eq user.email }.firstOrNull()
            println(alice.toString())
            if (alice != null) added = false
            else {
                Users.insert {
                    it[email] = user.email
                    it[userType] = user.userType
                    it[password] = user.password
                }
            }
        }
        return added
    }

    fun hasUser(user: User): Boolean {
        var isThere = false
        transaction {
            val currentUser = Users.select { Users.email eq user.email and (Users.password eq user.password) }.firstOrNull()
            if (currentUser != null) isThere = true
        }
        return isThere
    }

    fun getUser(email: String): User? {
        var currentUser: User? = null
        transaction {
            val user = Users.select { Users.email eq email }.firstOrNull()
            if (user != null) {
                currentUser = User(user[Users.email], user[Users.userType], user[Users.password])
            }
            commit()
        }
        return currentUser
    }

    fun getUserProfile(email: String): UserProfile? {
        var currentUserProfile: UserProfile? = null
        transaction {
            val userProfile = UserProfiles.select {
                UserProfiles.email eq email
            }.firstOrNull()

            if (userProfile != null) {
                currentUserProfile = UserProfile(
                    userProfile[UserProfiles.name],
                    userProfile[UserProfiles.userType],
                    userProfile[UserProfiles.phone],
                    userProfile[UserProfiles.email],
                    userProfile[UserProfiles.address]
                )
            }
        }

        return currentUserProfile
    }

    fun putUserProfile(userProfile: UserProfile) {
        transaction {
            if (getUserProfile(userProfile.email) == null) {
                UserProfiles.insert {
                    it[name] = userProfile.name
                    it[userType] = userProfile.userType
                    it[phone] = userProfile.phone
                    it[email] = userProfile.email
                    it[address] = userProfile.address
                }
            } else {
                UserProfiles.update({ UserProfiles.email eq userProfile.email }) {
                    it[name] = userProfile.name
                    it[userType] = userProfile.userType
                    it[phone] = userProfile.phone
                    it[address] = userProfile.address
                }
            }
        }
    }

    fun deleteUserProfile(email: String) {
        transaction {
            UserProfiles.deleteWhere { UserProfiles.email eq email }
            Users.deleteWhere { Users.email eq email }
        }
    }

    fun getCategory(id: Int) : Category? {
        var category: Category? = null
        transaction {
            val current = Categories.select { Categories.categoryId eq id }.firstOrNull()
            if (current != null) {
                category = Category(
                    current[Categories.categoryId],
                    current[Categories.title],
                    current[Categories.description]
                )
            }
        }
        return category
    }

    fun getAllCategories(): List<Category> {
        var categories: List<Category> = emptyList()
        transaction {
            categories = Categories.selectAll().map {
                Category(
                    it[Categories.categoryId],
                    it[Categories.title],
                    it[Categories.description]
                )
            }
        }
        return categories
    }

    fun postCategories(category: Category) {
        transaction {
            Categories.insert {
                it[categoryId] = category.categoryId
                it[title] = category.title
                it[description] = category.description
            }
        }
    }

    fun getMeal(id: Int): Meal? {
        var meal: Meal? = null
        transaction {
            val current = Meals.select { Meals.mealId eq id }.firstOrNull()
            if (current != null) {
                meal = Meal(
                    current[Meals.mealId],
                    current[Meals.title],
                    current[Meals.price],
                    current[Meals.imageUrl],
                    Meals.toCategoryId(current[Meals.categoryIds])
                )
            }
        }
        return meal
    }

    fun getAllMeals(): List<Meal> {
        var meals: List<Meal> = emptyList()
        transaction {
            meals = Meals.selectAll().map {
                Meal(
                    it[Meals.mealId],
                    it[Meals.title],
                    it[Meals.price],
                    it[Meals.imageUrl],
                    Meals.toCategoryId(it[Meals.categoryIds])
                )
            }
        }
        return meals
    }

    fun postMeal(meal: Meal) {
        transaction {
            Meals.insert {
                it[mealId] = meal.mealId
                it[title] = meal.title
                it[price] = meal.price
                it[imageUrl] = meal.imageUrl
                it[categoryIds] = getCategoryIdString(meal.categoryIds)
            }
        }
    }

    private fun hikari(): HikariDataSource {
        val config = HikariConfig()
        config.driverClassName = "org.postgresql.Driver"
        config.jdbcUrl = "jdbc:postgresql://localhost:5432/hotkitchen"
        config.username = "postgres"
        config.password = "trust"
        config.maximumPoolSize = 3
        config.isAutoCommit = true
        config.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        config.validate()
        return HikariDataSource(config)
    }
}