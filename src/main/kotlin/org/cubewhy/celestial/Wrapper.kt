/*
 * Celestial Launcher <me@lunarclient.top>
 * License under GPLv3
 * Do NOT remove this note if you want to copy this file.
 */

package org.cubewhy.celestial

import com.google.gson.JsonParser
import org.apache.commons.io.FileUtils
import org.cubewhy.celestial.event.EventManager
import org.cubewhy.celestial.event.EventTarget
import org.cubewhy.celestial.event.impl.AuthEvent
import org.cubewhy.celestial.event.impl.GameStartEvent
import org.cubewhy.celestial.event.impl.GameTerminateEvent
import org.cubewhy.celestial.game.GameProperties
import org.cubewhy.celestial.game.LaunchCommand
import org.cubewhy.celestial.game.LaunchCommandJson
import org.cubewhy.celestial.game.addon.JavaAgent
import org.cubewhy.celestial.gui.elements.unzipNatives
import org.cubewhy.celestial.utils.GitUtils
import org.cubewhy.celestial.utils.OSEnum
import org.cubewhy.celestial.utils.currentJavaExec
import org.slf4j.LoggerFactory
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.charset.StandardCharsets
import javax.swing.JOptionPane
import kotlin.system.exitProcess

private var log = LoggerFactory.getLogger(Wrapper::class.java)

object Wrapper {
    @EventTarget
    fun onAuth(e: AuthEvent) {
        log.info("Request for login")
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(e.authURL.toString()), null)
        val link = JOptionPane.showInputDialog(
            null,
            f.getString("gui.launcher.auth.message"),
            f.getString("gui.launcher.auth.title"),
            JOptionPane.QUESTION_MESSAGE
        )
        e.put(link)
    }
}

fun main() {
    EventManager.register(Wrapper) // handle login requests
    log.info("Powered by Celestial")
    log.info("https://lunarclient.top")
    log.info("Celestial v${GitUtils.buildVersion} build by ${GitUtils.buildUser}")
    log.info("Git remote: ${GitUtils.remote} (${GitUtils.branch})")
    val jsonPath = System.getProperty("celestial.json")
    val commandFile = jsonPath?.toFile() ?: launchJson
    log.info("Launching via $commandFile")
    // parse command file
    val launchCommandJson = JSON.decodeFromString<LaunchCommandJson>(commandFile.readText(StandardCharsets.UTF_8))
    // to launch command
    val command = launchCommandJson.complete()
    val code = launch(command).waitFor()
    log.info("Game terminated (code=$code)")
    exitProcess(0)
}

fun generateScripts(): String {
    val sb = StringBuilder()
    val classpath = System.getProperty("java.class.path")
    if (OSEnum.current == OSEnum.Windows) {
        sb
            .append("@echo off\n")
            .append("@rem This script was generated by Celestial Launcher v${GitUtils.buildVersion}\n")
            .append("@rem View the real commandline in ${launchJson.name}\n")
    } else {
        sb.append("# This script was generated by Celestial Launcher v${GitUtils.buildVersion}\n")
            .append("# View the real commandline in ${launchJson.name}\n")
    }
    val command = mutableListOf<String>()
    command.add(currentJavaExec.path)
    command.add("-cp")
    command.add(classpath)
    command.add("-Dcelestial.json=${launchJson.path}")
    command.add(Wrapper::class.java.canonicalName + "Kt")
    command.map {
        sb.append(if (it.contains(" ")) "\"${it}\"" else it).append(" ")
    }
    return sb.toString()
}

private fun LaunchCommandJson.complete(): LaunchCommand {
    val javaAgents = JavaAgent.findEnabled()
    if (config.addon.weave.state) {
        log.info("Weave is enabled!")
        javaAgents.add(JavaAgent(config.addon.weave.installationDir))
    }
    if (config.addon.lunarcn.state) {
        log.info("LunarCN is enabled!")
        javaAgents.add(JavaAgent(config.addon.lunarcn.installationDir))
    }
    if (config.addon.lcqt.state) {
        log.info("LunarQT is enabled!")
        javaAgents.add(JavaAgent(config.addon.lcqt.installationDir))
    }
    log.info("Found ${javaAgents.count()} javaagents")
    return LaunchCommand(
        installation = this.installation.toFile(),
        jre = this.jre.toFile(),
        wrapper = this.wrapper,
        mainClass = this.mainClass,
        natives = this.natives.map { File(it) },
        vmArgs = this.vmArgs,
        programArgs = this.programArgs,
        javaAgents = javaAgents,
        classpath = this.classpath.map { it.toFile() },
        ichorpath = this.ichorpath.map { it.toFile() },
        ipcPort = this.ipcPort,
        gameVersion = this.gameVersion,
        gameProperties = GameProperties(
            config.game.resize.width,
            config.game.resize.height,
            File(config.game.gameDir)
        )
    )
}

/**
 * Patching network disabling for LunarClient
 */
fun completeSession() {
    if (!sessionFile.exists()) {
        log.info("Completing session.json to fix the network error for LunarClient")
        var json: ByteArray?
        "/game/session.json".getInputStream().use { stream ->
            json = stream?.readAllBytes()
        }
        FileUtils.writeStringToFile(
            sessionFile, JsonParser.parseString(
                String(
                    json!!, StandardCharsets.UTF_8
                )
            ).toString(), StandardCharsets.UTF_8
        )
    }
}

fun launch(cmd: LaunchCommand): Process {
    log.info("Patching network disable...")
    completeSession()
    log.info("Unzipping natives")
    try {
        cmd.natives.forEach {
            it.unzipNatives(cmd.installation)
        }
    } catch (e: Exception) {
        log.error("Failed to unzip natives. Does the game running?")
        log.error(e.stackTraceToString())
    }
    log.info("Starting auth server...")
    val server = cmd.startAuthServer()
    log.info("Generating command...")
    // wait 1s for the auth server start
    Thread.sleep(1000)
    val commandList = cmd.generateCommand(server.port)
    log.debug(commandList.joinToString(" "))
    log.info("Executing command...")
    val pb = ProcessBuilder(commandList)
        .inheritIO()
        .directory(cmd.installation)
    val process = pb.start()
    val pid = process.pid()
    Thread.sleep(3000) // wait 3s
    log.info("Game is running! (PID: $pid)")
    GameStartEvent(pid).call()
    process.onExit().thenAccept {
        GameTerminateEvent(it.exitValue()).call()
        server.stop()
    }
    return process
}

fun launchPrevious(): Process {
    val launchCommandJson = JSON.decodeFromString<LaunchCommandJson>(launchJson.readText(StandardCharsets.UTF_8))
    return launch(launchCommandJson.complete())
}