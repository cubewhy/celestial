/*
 * Celestial Launcher <me@lunarclient.top>
 * License under GPLv3
 * Do NOT remove this note if you want to copy this file.
 */
package org.cubewhy.celestial.gui.dialogs

import cn.hutool.core.util.NumberUtil
import com.google.gson.JsonObject
import org.cubewhy.celestial.Celestial.f
import org.cubewhy.celestial.Celestial.proxy
import org.cubewhy.celestial.gui.GuiLauncher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.GridLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

class MirrorDialog : JDialog() {
    private var input: JTextArea? = null

    init {
        this.title = f.getString("gui.mirror.title")
        this.setSize(600, 600)
        this.layout = BorderLayout()
        this.modalityType = ModalityType.APPLICATION_MODAL
        this.isLocationByPlatform = true
        this.initGui()
    }

    private fun initGui() {
        this.input = JTextArea(header)
        this.add(
            JScrollPane(
                this.input,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            )
        )

        val btnCheckSyntax: JButton = JButton(f.getString("gui.mirror.syntax"))

        btnCheckSyntax.addActionListener {
            log.info("Check syntax")
            val status = this.checkSyntax()
            if (status) {
                JOptionPane.showMessageDialog(
                    this,
                    f.getString("gui.mirror.syntax.pass"),
                    "Syntax check",
                    JOptionPane.INFORMATION_MESSAGE
                )
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    f.getString("gui.mirror.syntax.incorrect"),
                    "Syntax check",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }

        val buttons = JPanel()
        buttons.layout = GridLayout(1, 1)
        buttons.add(btnCheckSyntax)

        this.add(buttons, BorderLayout.SOUTH)

        this.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                // save config
                if (!checkSyntax()) {
                    dispose()
                    return
                }
                val json = asJson()
                proxy.applyMirrors(json)
                GuiLauncher.statusBar.text = f.getString("giu.mirror.success")
                dispose() // close window
            }
        })

        loadFromJson() // add texts to this.input
    }

    private val header: String
        get() {
            val lines: Array<String> =
                f.getString("gui.mirror.header").split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            val sb = StringBuilder()
            for (line in lines) {
                sb.append("# ").append(line).append("\n")
            }
            return sb.toString()
        }

    private fun checkSyntax(): Boolean {
        for (s in input!!.text.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (s.startsWith("#")) {
                continue
            }
            val addresses = s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (addresses.size != 2) {
                return false
            }
            // check port is number
            for (address in addresses) {
                val split = address.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (split.size != 2) {
                    return false // wtf this
                }
                val port = split[1]
                if (!NumberUtil.isNumber(port)) {
                    return false
                }
            }
        }
        return true
    }

    private fun loadFromJson() {
        val mirrors: JsonObject = proxy.getValue("mirror").asJsonObject
        for ((source, value) in mirrors.entrySet()) {
            val mirror = value.asString
            input!!.append(String.format("%s %s\n", source, mirror))
        }
    }

    private fun asJson(): JsonObject {
        val json = JsonObject()
        for (s in input!!.text.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (s.startsWith("#")) {
                continue
            }
            val split = s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val source = split[0]
            val mirror = split[1]
            json.addProperty(source, mirror)
        }
        return json
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MirrorDialog::class.java)
    }
}
