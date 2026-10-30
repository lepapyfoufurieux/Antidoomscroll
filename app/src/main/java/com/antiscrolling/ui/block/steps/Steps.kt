package com.antiscrolling.ui.block.steps

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.antiscrolling.BuildConfig
import com.antiscrolling.domain.INTENTS
import com.antiscrolling.domain.Task
import com.antiscrolling.domain.TaskKind
import com.antiscrolling.ui.durationLabel
import kotlinx.coroutines.delay

// En debug, 1 min pour passer la recette vite.
private val DURATION_CHOICES = (if (BuildConfig.DEBUG) listOf(1) else emptyList()) + listOf(5, 10, 15, 30)

@Composable
fun DelayStep(delaySec: Int, continueLabel: String, onDone: () -> Unit) {
    var remaining by rememberSaveable { mutableIntStateOf(delaySec) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }
    Text(
        if (remaining > 0) "Une pause avant d'ouvrir : $remaining s" else "Vous voulez vraiment l'ouvrir ?",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Button(onClick = onDone, enabled = remaining == 0, modifier = Modifier.fillMaxWidth()) { Text(continueLabel) }
}

@Composable
fun IntentStep(label: String, onPick: (String) -> Unit) {
    Text("Pourquoi ouvrir $label maintenant ?", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    INTENTS.forEach { reason ->
        FilledTonalButton(onClick = { onPick(reason) }, modifier = Modifier.fillMaxWidth()) { Text(reason) }
    }
}

@Composable
fun TaskStep(kind: TaskKind, continueLabel: String, onDone: () -> Unit) {
    // Les nombres du calcul survivent à la rotation : sinon on pourrait tourner l'écran pour en avoir de plus simples.
    val numbers = rememberSaveable { (Task.create(TaskKind.MATH, Math::random) as Task.Math).let { intArrayOf(it.a, it.b) } }
    val task = when (kind) {
        TaskKind.COPY -> Task.Copy(Task.COPY_SENTENCE)
        TaskKind.MATH -> Task.Math(numbers[0], numbers[1])
        TaskKind.NONE -> return // jamais : l'étape n'existe que si une tâche est choisie
    }
    var answer by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() } // ouvre le clavier sans avoir à toucher le champ
    Text(
        when (task) {
            is Task.Copy -> "Recopiez : « ${task.sentence} »"
            is Task.Math -> "Combien font ${task.a} + ${task.b} ?"
        },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    OutlinedTextField(
        value = answer,
        onValueChange = { answer = it },
        singleLine = task is Task.Math,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (task is Task.Math) KeyboardType.Number else KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (task.check(answer)) onDone() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    Button(onClick = onDone, enabled = task.check(answer), modifier = Modifier.fillMaxWidth()) { Text(continueLabel) }
}

@Composable
fun DurationStep(onPick: (Int) -> Unit) {
    Text("Pour combien de temps ?", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    DURATION_CHOICES.forEach { minutes ->
        FilledTonalButton(onClick = { onPick(minutes) }, modifier = Modifier.fillMaxWidth()) { Text(durationLabel(minutes)) }
    }
}
