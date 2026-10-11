package staff.android.boundaries.monolith

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = application as ShopApplication
        setContent {
            CommonGoodsTheme {
                Surface(Modifier.fillMaxSize()) {
                    var screen by rememberSaveable { mutableStateOf("saved") }
                    var message by rememberSaveable { mutableStateOf("") }
                    var draft by rememberSaveable { mutableStateOf("3") }
                    val quantity by graph.table.teaQuantity.collectAsStateWithLifecycle()
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CommonGoodsHeader()

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { screen = "saved"; message = "" }) { Text("Open Saved items") }
                            OutlinedButton(onClick = { screen = "cart"; message = "" }) { Text("Open Cart") }
                        }
                        HorizontalDivider()
                        Text(if (screen == "saved") "Saved items" else "Cart", style = MaterialTheme.typography.headlineMedium)
                        ProductPhoto("tea")
                        Text("Tea in Cart: $quantity")
                        if (screen == "saved") {
                            Text("Tea is saved for later. Saving does not add it to Cart.")
                            Button(onClick = { message = graph.saved.addTea(2) }) { Text("Add 2 Tea to Cart") }
                            OutlinedButton(onClick = { message = graph.saved.addTea(1) }) { Text("Add 1 Tea to Cart") }
                        } else {
                            OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("Tea quantity") }, singleLine = true)
                            Button(onClick = { message = graph.cart.setTea(draft.toInt()) }, enabled = draft.toIntOrNull() != null) { Text("Apply quantity") }
                        }
                        if (message.isNotBlank()) ShopMessage(message)
                        LabControls { Text("Deliberately broken. Cart limit: 4 per product. New process starts Tea at 3.") }
                    }
                }
            }
        }
    }
}
