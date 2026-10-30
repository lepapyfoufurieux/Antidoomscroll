package com.antiscrolling.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antiscrolling.ui.Neon
import com.antiscrolling.ui.NeonButton
import com.antiscrolling.ui.ScreenHeader

/** Écran d'information exigé par Google Play avant d'envoyer vers Paramètres › Accessibilité. */
@Composable
fun ConsentScreen(onAccept: () -> Unit) {
    OnboardingPage(
        title = "Avant de commencer",
        paragraphs = listOf(
            "Antiscrolling utilise le service d'accessibilité d'Android pour savoir quelle appli vous ouvrez, " +
                "et afficher un écran de pause devant les applis que vous avez choisies.",
            "Ce qui est lu : uniquement le nom de l'appli au premier plan. Le contenu de l'écran, ce que vous tapez " +
                "et vos messages ne sont jamais lus.",
            "Rien ne quitte le téléphone : l'appli n'a pas accès à Internet et ne partage aucune donnée.",
        ),
        button = "J'accepte",
        onClick = onAccept,
    )
}

@Composable
fun AccessibilityScreen(onOpenSettings: () -> Unit) {
    OnboardingPage(
        title = "Activer le service",
        paragraphs = listOf(
            "Dans Paramètres › Accessibilité, ouvrez Antiscrolling et activez le service.",
            "Si l'option est grisée : Paramètres › Applis › Antiscrolling › ⋮ › Autoriser les paramètres restreints.",
            "Si ce message réapparaît, Android a arrêté le service : réactivez-le.",
        ),
        button = "Ouvrir les paramètres",
        onClick = onOpenSettings,
    )
}

@Composable
fun UsageAccessScreen(onOpenSettings: () -> Unit) {
    OnboardingPage(
        title = "Autoriser l'accès à l'utilisation",
        paragraphs = listOf(
            "Pour afficher le temps passé dans chaque appli, Antiscrolling lit les statistiques d'utilisation " +
                "qu'Android tient déjà.",
            "Dans la liste, ouvrez Antiscrolling et activez « Autoriser l'accès à l'utilisation ».",
            "Ces données restent sur le téléphone.",
        ),
        button = "Ouvrir les paramètres",
        onClick = onOpenSettings,
    )
}

@Composable
private fun OnboardingPage(title: String, paragraphs: List<String>, button: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader("Antidoomscroll", title)
        paragraphs.forEach { Text(it, color = Neon.Soft, fontSize = 16.sp, lineHeight = 23.sp) }
        NeonButton(button, onClick, Modifier.fillMaxWidth(), color = Neon.Yellow, filled = true)
    }
}
