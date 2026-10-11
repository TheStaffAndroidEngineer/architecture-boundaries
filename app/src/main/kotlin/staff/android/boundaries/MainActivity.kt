package staff.android.boundaries

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import staff.android.boundaries.cartui.CartOwner
import staff.android.boundaries.cartui.CartScreen
import staff.android.boundaries.savedui.SavedOwner
import staff.android.boundaries.savedui.SavedScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cart = (application as ShopApplication).cart
        val owners =
            ViewModelProvider(
                this,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(
                        modelClass: Class<T>
                    ): T =
                        when (modelClass) {
                            SavedOwner::class.java ->
                                SavedOwner(cart) as T
                            CartOwner::class.java ->
                                CartOwner(cart) as T
                            else ->
                                error("Unsupported owner: $modelClass")
                        }
                },
            )
        val savedOwner = owners[SavedOwner::class.java]
        val cartOwner = owners[CartOwner::class.java]
        setContent {
            CommonGoodsTheme {
                Surface(Modifier.fillMaxSize()) {
                    var screen by rememberSaveable {
                        mutableStateOf("saved")
                    }
                    Column(
                        Modifier.fillMaxSize()
                            .safeDrawingPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(16.dp),
                    ) {
                        CommonGoodsHeader()

                        FlowRow(
                            horizontalArrangement =
                                Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { screen = "saved" }
                            ) {
                                Text("Open Saved items")
                            }
                            OutlinedButton(
                                onClick = { screen = "cart" }
                            ) {
                                Text("Open Cart")
                            }
                        }
                        HorizontalDivider()
                        if (screen == "saved") SavedScreen(savedOwner)
                        else CartScreen(cartOwner)
                        LabControls {
                            Text(
                                "Cart limit: 4 per product. " +
                                    "In-memory architecture lab; " +
                                    "a new process starts Tea at 3."
                            )
                        }
                    }
                }
            }
        }
    }
}
