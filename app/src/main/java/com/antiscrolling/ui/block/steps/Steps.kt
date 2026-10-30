package com.antiscrolling.ui.block.steps

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiscrolling.BuildConfig
import com.antiscrolling.domain.INTENTS
import com.antiscrolling.domain.Task
import com.antiscrolling.domain.TaskKind
import com.antiscrolling.ui.Caption
import com.antiscrolling.ui.ChoiceGrid
import com.antiscrolling.ui.Neon
import com.antiscrolling.ui.NeonButton
import com.antiscrolling.ui.NeonRing
import com.antiscrolling.ui.durationLabel
import com.antiscrolling.ui.glow
import kotlinx.coroutines.delay
import kotlin.random.Random

// En debug, 1 min pour passer la recette vite.
private val DURATION_CHOICES = (if (BuildConfig.DEBUG) listOf(1) else emptyList()) + listOf(5, 10, 15, 30)

/** [color] : l'anneau passe du bleu au magenta au fil des ouvertures du jour. */
@Composable
fun DelayStep(delaySec: Int, continueLabel: String, color: Color, onDone: () -> Unit) {
    var remaining by rememberSaveable { mutableIntStateOf(delaySec) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }
    NeonRing(color, if (delaySec == 0) 0f else remaining.toFloat() / delaySec, 150.dp) {
        Text("$remaining", color = Color.White, fontFamily = Neon.Mono, fontSize = 56.sp, fontWeight = FontWeight.Bold, style = TextStyle(shadow = glow(color)))
        Caption("secondes")
    }
    Text(
        if (remaining > 0) "Une pause de $delaySec s avant d'ouvrir" else "Vous voulez vraiment l'ouvrir ?",
        color = Neon.Soft,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
    )
    NeonButton(
        if (remaining > 0) "Continuer dans $remaining s" else continueLabel,
        onDone,
        Modifier.fillMaxWidth(),
        color = Neon.Yellow,
        filled = true,
        enabled = remaining == 0,
    )
}

@Composable
fun IntentStep(label: String, onPick: (String) -> Unit) {
    Question("Pourquoi ouvrir $label maintenant ?")
    ChoiceGrid(INTENTS, null, 2, { it }, color = Neon.Magenta, mono = false, role = Role.Button, onPick = onPick)
}

@Composable
fun TaskStep(kind: TaskKind, continueLabel: String, onDone: () -> Unit) {
    // Les nombres du calcul survivent à la rotation : sinon on pourrait tourner l'écran pour en avoir de plus simples.
    val numbers = rememberSaveable { intArrayOf(Random.nextInt(10, 100), Random.nextInt(10, 100)) }
    val task = when (kind) {
        TaskKind.COPY -> Task.Copy(Task.COPY_SENTENCE)
        TaskKind.MATH -> Task.Math(numbers[0], numbers[1])
        TaskKind.NONE -> return // jamais : l'étape n'existe que si une tâche est choisie
    }
    var answer by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() } // ouvre le clavier sans avoir à toucher le champ
    Question(
        when (task) {
            is Task.Copy -> "Recopiez : « ${task.sentence} »"
            is Task.Math -> "Combien font ${task.a} + ${task.b} ?"
        },
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
    NeonButton(continueLabel, onDone, Modifier.fillMaxWidth(), color = Neon.Yellow, filled = true, enabled = task.check(answer))
}

@Composable
fun DurationStep(onPick: (Int) -> Unit) {
    Question("Combien de temps ?")
    ChoiceGrid(DURATION_CHOICES, null, 3, ::durationLabel, role = Role.Button, onPick = onPick)
}

@Composable
private fun Question(text: String) {
    Text(text, color = Neon.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}
