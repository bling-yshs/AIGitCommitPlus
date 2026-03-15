package com.yshs.aicommit.config

import com.yshs.aicommit.service.ModelDiscoveryService
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.Action
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.UIManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ModelImportDialog(
    private val parentComponent: Component,
    private val client: String,
    fetchedModels: List<String>?,
    private val settings: ApiKeySettings,
    private val modelDiscoveryService: ModelDiscoveryService,
    private val onModelsChanged: Runnable,
) : DialogWrapper(parentComponent, true) {

    private val fetchedModels = mutableListOf<String>()
    private var rootPanel: JPanel? = null
    private var searchTextField: SearchTextField? = null
    private var modelRowsPanel: JPanel? = null
    private var addAllButton: JButton? = null
    private var refreshButton: JButton? = null

    init {
        if (fetchedModels != null) {
            this.fetchedModels.addAll(fetchedModels)
        }
        title = "Import Models"
        init()
        reloadModelRows()
    }

    override fun createCenterPanel(): JComponent {
        rootPanel = JPanel(BorderLayout(0, 10)).apply {
            preferredSize = Dimension(560, 380)
        }

        searchTextField = SearchTextField().apply {
            textEditor.putClientProperty("JTextField.placeholderText", "Search models")
            addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = reloadModelRows()
                override fun removeUpdate(e: DocumentEvent) = reloadModelRows()
                override fun changedUpdate(e: DocumentEvent) = reloadModelRows()
            })
        }

        addAllButton = JButton(AllIcons.General.Add).apply {
            toolTipText = "Add all currently visible models"
            isFocusable = false
            addActionListener { addAllVisibleModels() }
        }

        refreshButton = JButton(AllIcons.Actions.Refresh).apply {
            toolTipText = "Refresh models from the provider"
            isFocusable = false
            addActionListener { refreshFetchedModels() }
        }

        val toolbarPanel = JPanel(BorderLayout(8, 0)).apply {
            add(searchTextField, BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                    add(addAllButton)
                    add(refreshButton)
                },
                BorderLayout.EAST,
            )
        }
        rootPanel!!.add(toolbarPanel, BorderLayout.NORTH)

        modelRowsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        rootPanel!!.add(JBScrollPane(modelRowsPanel), BorderLayout.CENTER)

        rootPanel!!.add(
            JBLabel("Click + to add a single model to the outer model list.").apply {
                foreground = JBColor.GRAY
            },
            BorderLayout.SOUTH,
        )

        return rootPanel!!
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)

    private fun reloadModelRows() {
        val rowsPanel = modelRowsPanel ?: return
        rowsPanel.removeAll()
        val visibleModels = getFilteredModels()
        if (visibleModels.isEmpty()) {
            rowsPanel.add(
                JBLabel("No models match the current filter.").apply {
                    foreground = JBColor.GRAY
                    border = JBUI.Borders.empty(8)
                },
            )
        } else {
            visibleModels.forEach { rowsPanel.add(createModelRow(it)) }
        }
        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    private fun createModelRow(model: String): JPanel {
        val alreadyAdded = settings.getAvailableModels(client).contains(model)
        val row = JPanel(BorderLayout(8, 0)).apply {
            border = JBUI.Borders.empty(6, 8)
            maximumSize = Dimension(Int.MAX_VALUE, 34)
            background = UIManager.getColor("Panel.background")
        }

        val label = JBLabel(model).apply {
            foreground = if (alreadyAdded) JBColor.GRAY else JBColor.foreground()
        }

        val toggleButton = JButton(if (alreadyAdded) AllIcons.General.Remove else AllIcons.General.Add).apply {
            toolTipText = if (alreadyAdded) "Remove model" else "Add model"
            isFocusable = false
            addActionListener { toggleModel(model) }
        }

        val selectListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                toggleModel(model)
            }
        }
        row.addMouseListener(selectListener)
        label.addMouseListener(selectListener)

        row.add(label, BorderLayout.CENTER)
        row.add(toggleButton, BorderLayout.EAST)
        return row
    }

    private fun addModel(model: String) {
        settings.addAvailableModel(client, model)
        if (settings.getSelectedModel(client).isBlank()) {
            settings.setSelectedModel(client, model)
        }
        onModelsChanged.run()
        reloadModelRows()
    }

    private fun removeModel(model: String) {
        settings.removeAvailableModel(client, model)
        if (model == settings.getSelectedModel(client)) {
            val remaining = settings.getAvailableModels(client)
            settings.setSelectedModel(client, if (remaining.isEmpty()) "" else remaining[0])
        }
        onModelsChanged.run()
        reloadModelRows()
    }

    private fun toggleModel(model: String) {
        if (settings.getAvailableModels(client).contains(model)) {
            removeModel(model)
        } else {
            addModel(model)
        }
    }

    private fun addAllVisibleModels() {
        val visibleModels = getFilteredModels()
        if (visibleModels.isEmpty()) return

        var addedAny = false
        visibleModels.forEach {
            if (!settings.getAvailableModels(client).contains(it)) {
                settings.addAvailableModel(client, it)
                addedAny = true
            }
        }
        if (!addedAny) return

        if (settings.getSelectedModel(client).isBlank()) {
            settings.setSelectedModel(client, visibleModels[0])
        }
        onModelsChanged.run()
        reloadModelRows()
    }

    private fun refreshFetchedModels() {
        ProgressManager.getInstance().run(object : Task.Modal(null, "Refreshing Models", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = true
                    indicator.text = "Fetching models from $client..."
                    val models = modelDiscoveryService.fetchModels(client)
                    ApplicationManager.getApplication().invokeLater {
                        fetchedModels.clear()
                        fetchedModels.addAll(models)
                        reloadModelRows()
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(parentComponent, "Failed to refresh models: ${ex.message}", "Refresh Models")
                    }
                }
            }
        })
    }

    private fun getFilteredModels(): List<String> {
        val keyword = searchTextField?.text?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (keyword.isEmpty()) {
            return fetchedModels.toList()
        }
        return fetchedModels.filter { it.lowercase(Locale.ROOT).contains(keyword) }
    }
}
