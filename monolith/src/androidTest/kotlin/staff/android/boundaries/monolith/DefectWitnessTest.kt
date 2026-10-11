package staff.android.boundaries.monolith

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Passing tests that witness the deliberately broken starting checkpoint. */
@RunWith(AndroidJUnit4::class)
class DefectWitnessTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun resetQuantity() {
        compose.runOnIdle { (compose.activity.application as ShopApplication).table.writeQuantity(3) }
    }
    private fun tap(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()

    @Test fun cartControllerRejectsFive() {
        tap("Open Cart")
        compose.onNode(hasSetTextAction()).performTextReplacement("5")
        tap("Apply quantity")
        compose.onNodeWithText("Tea in Cart: 3").assertExists()
        compose.onNodeWithText("Not changed. Cart remains 3.").assertExists()
    }

    @Test fun savedControllerBypassesTheLimit() {
        tap("Add 2 Tea to Cart")
        compose.onNodeWithText("Tea in Cart: 5").assertExists()
        compose.onNodeWithText("Added 2. Cart now 5.").assertExists()
        tap("Open Cart")
        compose.onNodeWithText("Tea in Cart: 5").assertExists()
    }
}
