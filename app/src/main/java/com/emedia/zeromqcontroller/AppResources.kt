package com.emedia.zeromqcontroller

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

// 1. The Contract (Interface) - Forces every language to have these strings
interface AppStrings {
    val appTitle: String
    val connect: String
    val enterIp: String
    val ipAddress: String
    val connectionSettings: String
    val language: String
    val cancel: String
    val close: String
    val assistiveLevel: String
    val levelShort: String
    val responsePrefix: String
    val modeChangeRequest: String
    val from: String
    val to: String
    val accept: String
    val reject: String
    val deviceIp: String
    val listeningPort: String
    val fatigueRequest: String
    val fatigueLevel: String
    // Helper to translate modes
    fun getModeName(mode: ControllerMode): String
}

// 2. English Implementation
object EnStrings : AppStrings {
    override val appTitle = "ZeroMQ Controller"
    override val connect = "Connect"
    override val enterIp = "Enter IP Address"
    override val ipAddress = "IP Address"
    override val connectionSettings = "Connection Settings"
    override val language = "Language"
    override val cancel = "Cancel"
    override val close = "Close"
    override val assistiveLevel = "Assistive Level"
    override val levelShort = "Level"
    override val responsePrefix = "Response: "
    override val modeChangeRequest = "Mode Change Requested"
    override val from = "Current:"
    override val to = "New:"
    override val accept = "ACCEPT"
    override val reject = "REJECT"
    override val deviceIp = "Your Device IP:"
    override val listeningPort = "Listening on Port:"
    override val fatigueRequest = "Fatigue Level Update"
    override val fatigueLevel = "Fatigue"

    override fun getModeName(mode: ControllerMode): String = when(mode) {
        // CHANGED: Clearer name for elderly users
        ControllerMode.Transparent -> "Free Movement"
        ControllerMode.Walking -> "Walking"
        ControllerMode.SitToStance -> "Sit to Stand"
        ControllerMode.StairAscent -> "Stair Up"
        ControllerMode.StairDescent -> "Stair Down"
    }
}

// 3. Dutch (Nederlands) Implementation
object NlStrings : AppStrings {
    override val appTitle = "ZeroMQ Besturing"
    override val connect = "Verbinden"
    override val enterIp = "Voer IP-adres in"
    override val ipAddress = "IP-adres"
    override val connectionSettings = "Verbindingsinstellingen"
    override val language = "Taal"
    override val cancel = "Annuleren"
    override val close = "Sluiten"
    override val assistiveLevel = "Ondersteuningsniveau"
    override val levelShort = "Niveau"
    override val responsePrefix = "Antwoord: "
    override val modeChangeRequest = "Verandering Modus Gevraagd"
    override val from = "Huidig:"
    override val to = "Nieuw:"
    override val accept = "ACCEPTEREN"
    override val reject = "WEIGEREN"
    override val deviceIp = "Uw Apparaat IP:"
    override val listeningPort = "Luisterend op Poort:"
    override val fatigueRequest = "Update Vermoeidheid"
    override val fatigueLevel = "Vermoeidheid"

    override fun getModeName(mode: ControllerMode): String = when(mode) {
        ControllerMode.Transparent -> "Vrije Beweging"
        ControllerMode.Walking -> "Wandelen"
        ControllerMode.SitToStance -> "Zitten en Rechtstaan"
        ControllerMode.StairAscent -> "Trap Op"
        ControllerMode.StairDescent -> "Trap Af"
    }
}

// 4. French (Français) Implementation
object FrStrings : AppStrings {
    override val appTitle = "Contrôleur ZeroMQ"
    override val connect = "Connecter"
    override val enterIp = "Insérer l'adresse IP"
    override val ipAddress = "Adresse IP"
    override val connectionSettings = "Paramètres de connexion"
    override val language = "Langue"
    override val cancel = "Annuler"
    override val close = "Fermer"
    override val assistiveLevel = "Niveau d'assistance"
    override val levelShort = "Niveau"
    override val responsePrefix = "Réponse: "
    override val modeChangeRequest = "Changement de Mode Demandé"
    override val from = "Actuel:"
    override val to = "Nouveau:"
    override val accept = "ACCEPTER"
    override val reject = "REFUSER"
    override val deviceIp = "IP de votre appareil:"
    override val listeningPort = "Écoute sur le port:"
    override val fatigueRequest = "Mise à jour Fatigue"
    override val fatigueLevel = "Fatigue"

    override fun getModeName(mode: ControllerMode): String = when(mode) {
        ControllerMode.Transparent -> "Mouvement Libre"
        ControllerMode.Walking -> "Marcher"
        ControllerMode.SitToStance -> "S'asseoir et Se lever"
        ControllerMode.StairAscent -> "Monter l'escalier"
        ControllerMode.StairDescent -> "Descendre l'escalier"
    }
}

// 5. The Provider for Compose
val LocalAppStrings = staticCompositionLocalOf<AppStrings> { EnStrings }

// 6. Accessor shortcut
object AppRes {
    val strings: AppStrings
        @Composable
        get() = LocalAppStrings.current
}