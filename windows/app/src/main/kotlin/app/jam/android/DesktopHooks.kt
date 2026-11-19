package app.jam.android

/** Discord.kt's presence, also to the Discord app on the PC while not signed in (DiscordIpc). */
internal fun desktopIpc(a: RichActivity?, signedIn: Boolean) = app.jam.desktop.DiscordIpc.update(a, signedIn)
