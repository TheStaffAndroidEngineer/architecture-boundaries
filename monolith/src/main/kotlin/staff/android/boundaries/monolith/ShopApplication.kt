package staff.android.boundaries.monolith

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow

class CartTable {
    val teaQuantity = MutableStateFlow(3)

    fun writeQuantity(value: Int) {
        teaQuantity.value = value
    }
}

class CartController(private val table: CartTable) {
    fun setTea(quantity: Int): String =
        if (quantity in 0..4) {
            table.writeQuantity(quantity)
            "Cart accepted $quantity."
        } else "Not changed. Cart remains ${table.teaQuantity.value}."
}

class SavedController(private val table: CartTable) {
    fun addTea(amount: Int): String {
        // Deliberate defect: this path bypasses CartController's rule.
        val desired = table.teaQuantity.value + amount
        table.writeQuantity(desired)
        return "Added $amount. Cart now $desired."
    }
}

class ShopApplication : Application() {
    val table = CartTable()
    val cart = CartController(table)
    val saved = SavedController(table)
}
