package staff.android.boundaries.singlemodule

import android.app.Application
import staff.android.boundaries.api.CartItem
import staff.android.boundaries.impl.createCart

class ShopApplication : Application() {
    val cart by lazy {
        createCart(
            products = setOf("tea"),
            maximumPerProduct = 4,
            initial = listOf(CartItem("tea", 3)),
        )
    }
}
