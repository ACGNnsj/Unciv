package com.unciv.app.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.glutils.HdpiMode
import com.unciv.json.json
import com.unciv.logic.files.SETTINGS_FILE_NAME
import com.unciv.logic.files.UncivFiles
import com.unciv.models.metadata.ScreenSize
import com.unciv.models.metadata.WindowState
import com.unciv.ui.components.Fonts
import com.unciv.utils.Display
import com.unciv.utils.Log
import org.lwjgl.system.Library
import org.lwjgl.system.ThreadLocalUtil
import java.awt.GraphicsEnvironment
import kotlin.math.max

internal object DesktopLauncher {

    @JvmStatic
    fun main(arg: Array<String>) {
        // Solve the problem 'java.home property not set'
        System.setProperty("java.home",System.getenv("JAVA_HOME"))
        // To avoid segfault
        Library.initialize()
        ThreadLocalUtil.setupEnvData()

        // Setup Desktop logging
        Log.backend = DesktopLogBackend()

        // Setup Desktop display
        Display.platform = DesktopDisplay()

        // Setup Desktop font
        Fonts.fontImplementation = DesktopFont()

        // Setup Desktop saver-loader
        UncivFiles.saverLoader = DesktopSaverLoader()
        UncivFiles.preferExternalStorage = false

        // Solves a rendering problem in specific GPUs and drivers.
        // For more info see https://github.com/yairm210/Unciv/pull/3202 and https://github.com/LWJGL/lwjgl/issues/119
        System.setProperty("org.lwjgl.opengl.Display.allowSoftwareOpenGL", "true")
        // This setting (default 64) limits clipboard transfers. Value in kB!
        // 386 is an almost-arbitrary choice from the saves I had at the moment and their GZipped size.
        // There must be a reason for lwjgl3 being so stingy, which for me meant to stay conservative.
        System.setProperty("org.lwjgl.system.stackSize", "384")

        val isRunFromJAR = DesktopLauncher.javaClass.`package`.specificationVersion != null
        ImagePacker.packImages(isRunFromJAR)

        val config = Lwjgl3ApplicationConfiguration()
        config.setWindowIcon("ExtraImages/Icon.png")
        config.setTitle("Unciv")
        config.setHdpiMode(HdpiMode.Logical)
        config.setWindowSizeLimits(120, 80, -1, -1)

        // We don't need the initial Audio created in Lwjgl3Application, HardenGdxAudio will replace it anyway.
        // Note that means config.setAudioConfig() would be ignored too, those would need to go into the HardenedGdxAudio constructor.
        config.disableAudio(true)

        val settings = UncivFiles.getSettingsForPlatformLaunchers()
        if (settings.isFreshlyCreated) {
            settings.screenSize = ScreenSize.Large // By default we guess that Desktops have larger screens
            // LibGDX not yet configured, use regular java class
            val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val maximumWindowBounds = graphicsEnvironment.maximumWindowBounds
            settings.windowState = WindowState(
                width = maximumWindowBounds.width,
                height = maximumWindowBounds.height
            )
            FileHandle(SETTINGS_FILE_NAME).writeString(json().toJson(settings), false) // so when we later open the game we get fullscreen
        }
        // Kludge! This is a workaround - the matching call in DesktopDisplay doesn't "take" quite permanently,
        // the window might revert to the "config" values when the user moves the window - worse if they
        // minimize/restore. And the config default is 640x480 unless we set something here.
        config.setWindowedMode(max(settings.windowState.width, 100), max(settings.windowState.height, 100))

        if (!isRunFromJAR) {
            UniqueDocsWriter().write()
            UiElementDocsWriter().write()
        }

        val game = DesktopGame(config)
        Lwjgl3Application(game, config)
    }
}
