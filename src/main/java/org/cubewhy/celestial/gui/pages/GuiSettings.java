package org.cubewhy.celestial.gui.pages;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.extern.slf4j.Slf4j;
import org.cubewhy.celestial.gui.layouts.VerticalFlowLayout;
import org.cubewhy.celestial.utils.GuiUtils;
import org.cubewhy.celestial.utils.SystemUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.cubewhy.celestial.Celestial.config;
import static org.cubewhy.celestial.Celestial.f;
import static org.cubewhy.celestial.gui.GuiLauncher.statusBar;

@Slf4j
public class GuiSettings extends JScrollPane {
    private static final JPanel panel = new JPanel();
    private final Set<String> claimed = new HashSet<>();

    public GuiSettings() {
        super(panel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        this.setBorder(new TitledBorder(null, f.getString("gui.settings.title"), TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, Color.orange));
        panel.setLayout(new VerticalFlowLayout(VerticalFlowLayout.LEFT));
        this.getVerticalScrollBar().setUnitIncrement(30);
        this.initGui();
    }

    private void initGui() {
        // config
        // jre
        JPanel panelVM = new JPanel();
        panelVM.setLayout(new VerticalFlowLayout(VerticalFlowLayout.LEFT));
        panelVM.setBorder(new TitledBorder(null, f.getString("gui.settings.jvm"), TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, Color.orange));

        String customJre = config.getValue("jre").getAsString();
        JButton btnSelectPath = new JButton((customJre.isEmpty()) ? SystemUtils.getCurrentJavaExec().getPath() : customJre);
        JButton btnUnset = new JButton(f.getString("gui.settings.jvm.jre.unset"));
        btnSelectPath.addActionListener((e) -> {
            File file = GuiUtils.chooseFile(new FileNameExtensionFilter("Java Executable", "exe"));
            if (file != null) {
                JButton source = (JButton) e.getSource();
                statusBar.setText(String.format(f.getString("gui.settings.jvm.jre.success"), file));
                config.setValue("jre", file.getPath());
                source.setText(file.getPath());
            }
        });
        btnUnset.addActionListener((e) -> {
            if (JOptionPane.showConfirmDialog(this, f.getString("gui.settings.jvm.jre.unset.confirm"), "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION) {
                return;
            }
            File java = SystemUtils.getCurrentJavaExec();
            btnSelectPath.setText(java.getPath());
            config.setValue("jre", "");
            statusBar.setText(f.getString("gui.settings.jvm.jre.unset.success"));
        });
        // jre settings
        JPanel p1 = new JPanel();
        p1.add(new JLabel(f.getString("gui.settings.jvm.jre")));
        p1.add(btnSelectPath);
        p1.add(btnUnset);
        panelVM.add(p1);
        // ram settings
        JPanel p2 = new JPanel();
        p2.add(new JLabel(f.getString("gui.settings.jvm.ram")));
        JSlider ramSlider = new JSlider(JSlider.HORIZONTAL, 0, SystemUtils.getTotalMem(), config.getValue("ram").getAsInt());
        ramSlider.setPaintTicks(true);
        ramSlider.setMajorTickSpacing(1024); // 1G
        p2.add(ramSlider);
        DecimalFormat decimalFormat = new DecimalFormat("#.##");

        JLabel labelRam = new JLabel(decimalFormat.format((float) ramSlider.getValue() / 1024F) + "GB");
        ramSlider.addChangeListener((e) -> {
            JSlider source = (JSlider) e.getSource();
            if (!source.getValueIsAdjusting()) {
                // save value
                log.info("Set ram -> " + source.getValue());
                config.setValue("ram", source.getValue());
            }
            labelRam.setText(decimalFormat.format((float) source.getValue() / 1024F) + "GB");
        });
        p2.add(labelRam);
        panelVM.add(p2);

        claim("jre", panelVM);
        claim("ram");
        claim("vm-args");
        claim("wrapper");

        claim("javaagents"); // config in GuiAddonManager

        JPanel panelUnclaimed = new JPanel();
        panelUnclaimed.setBorder(new TitledBorder(null, f.getString("gui.settings.unclaimed"), TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, Color.orange));
        panelUnclaimed.setLayout(new VerticalFlowLayout(VerticalFlowLayout.LEFT));
        addUnclaimed(panelUnclaimed, config.getConfig().entrySet());
        panel.add(panelUnclaimed);
    }

    private void addUnclaimed(JPanel basePanel, Set<Map.Entry<String, JsonElement>> entry) {
        for (Map.Entry<String, JsonElement> s : entry) {
            if (!claimed.contains(s.getKey())) {
                // unclaimed
                if (s.getValue().isJsonPrimitive()) {
                    JPanel p = getSimplePanel(s.getKey(), s.getValue().getAsJsonPrimitive());
                    basePanel.add(p);
                }
                if (s.getValue().isJsonObject()) {
                    JPanel subPanel = new JPanel();
                    subPanel.setBorder(new TitledBorder(null, s.getKey(), TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, Color.orange));
                    subPanel.setLayout(new VerticalFlowLayout(VerticalFlowLayout.LEFT));
                    basePanel.add(subPanel);
                    addUnclaimed(subPanel, s.getValue().getAsJsonObject().entrySet());
                }
            }
        }
    }

    private @NotNull JPanel getSimplePanel(String key, @NotNull JsonPrimitive value) {
        JPanel panel = new JPanel();
        if (value.isBoolean()) {
            JCheckBox cb = new JCheckBox(key);
            cb.addActionListener((e) -> {
                JCheckBox source = (JCheckBox) e.getSource();
                config.setValue(key, source.isSelected());
            });
            panel.add(cb);
        } else if (value.isString()) {
            panel.add(new JLabel(key));
            JTextField input = getAutoSaveTextField(key, value);
            panel.add(input);
        } else if (value.isNumber()) {
            panel.add(new JLabel(key));
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(value.getAsDouble(), Double.MIN_VALUE, Double.MAX_VALUE, 0.01));
            spinner.setAutoscrolls(true);
            JComponent editor = spinner.getEditor();
            JFormattedTextField textField = ((JSpinner.DefaultEditor) editor).getTextField();
            textField.setColumns(20);
            panel.add(spinner);

        }
        return panel;
    }

    @NotNull
    private static JTextField getAutoSaveTextField(String key, @NotNull JsonPrimitive value) {
        JTextField input = new JTextField(value.getAsString());
        input.addActionListener((e) -> {
            JTextField source = (JTextField) e.getSource();
            // save value
            config.setValue(key, source.getText());
        });
        input.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                JTextField source = (JTextField) e.getSource();
                // save value
                config.setValue(key, source.getText());
            }
        });
        return input;
    }

    /**
     * Mark a key as claimed and add the panel
     *
     * @param key      key in celestial.json
     * @param cfgPanel a panel to config this value
     */
    private void claim(String key, JPanel cfgPanel) {
        claim(key);
        panel.add(cfgPanel); // add the panel
    }

    private void claim(String key) {
        if (claimed.add(key)) {
            log.debug("Claimed " + key);
        } else {
            log.warn("Failed to claim " + key + " : always claimed.");
        }
    }
}
