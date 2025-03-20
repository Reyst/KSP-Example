package com.example.ksp.research

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ksp.research.ui.theme.KSPResearchTheme

class MainActivity : ComponentActivity() {

    private val fields = listOf("Name", "ID", "ToTaL", "test", "tt1")
    private val data = Info(
        s = "Test name",
        i = 2048,
        f = 1500F,
        cf = mapOf("test" to "CustomField")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KSPResearchTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        data = data,
                        fields = fields,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}


@Composable
fun Greeting(data: Info, fields: List<String>, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        fields
            .map { it to data.getMetaFieldValue(it) }
            .filter { (_, value) -> value != null }
            .forEach { (name, value) ->
                Text(text = "$name: $value")
            }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    KSPResearchTheme {
        Greeting(
            data = Info("Test name", 1024, 299.78F),
            fields = listOf("Name", "ID", "ToTaL"),
        )
    }
}

