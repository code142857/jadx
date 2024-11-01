package jadx.gui.ui.codearea

import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.PrimitiveType
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.settings.XposedCodegenLanguage
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JField
import jadx.gui.treemodel.JMethod
import jadx.gui.treemodel.JNode
import jadx.gui.ui.action.ActionModel
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.swing.JOptionPane

class YukiAction(codeArea: CodeArea) : JNodeAction(ActionModel.YUKI_COPY, codeArea) {
	override fun runAction(node: JNode) {
		try {
			val xposedSnippet = generateXposedSnippet(node)
			LOG.info("YukiHook snippet:\n{}", xposedSnippet)
			UiUtils.copyToClipboard(xposedSnippet)
		} catch (e: Exception) {
			LOG.error("Failed to generate YukiHook code snippet", e)
			JOptionPane.showMessageDialog(
				getCodeArea().mainWindow,
				e.localizedMessage,
				NLS.str("error_dialog.title"),
				JOptionPane.ERROR_MESSAGE,
			)
		}
	}

	override fun isActionEnabled(node: JNode?): Boolean {
		return node is JMethod || node is JClass || node is JField
	}

	private fun generateXposedSnippet(node: JNode): String {
		return when (node) {
			is JMethod -> generateMethodSnippet(node)
			is JClass -> generateClassSnippet(node)
			is JField -> generateFieldSnippet(node)
			else -> throw JadxRuntimeException("Unsupported node type: " + node.javaClass)
		}
	}

	private fun generateMethodSnippet(jMethod: JMethod): String {
		val javaMethod = jMethod.javaMethod
		val rawClassName = javaMethod.declaringClass.rawName

		val template =
			"""
				"$rawClassName".toClass()
                .apply {
                    method {
                        name = "${javaMethod.name}"
						paramCount(${javaMethod.arguments.size})
                    }.hook {
                        before {

                        }
                        after {

                        }
                    }
                }
				""".trimIndent()
		return template
	}

	private fun generateClassSnippet(jClass: JClass): String {
		val javaClass = jClass.cls
		val rawClassName = javaClass.rawName
		val className = javaClass.name
		return String.format("val %sClass = appClassLoader?.loadClass(\"%s\")", className, rawClassName)
	}

	private fun generateFieldSnippet(jField: JField): String {
		val javaField = jField.javaField
		val static = if (javaField.accessFlags.isStatic) "Static" else ""
		val type = PRIMITIVE_TYPE_MAPPING.getOrDefault(javaField.fieldNode.type.toString(), "Object")
		val xposedMethod = "XposedHelpers.get${static}${type}Field"

		val template = when (language) {
			XposedCodegenLanguage.JAVA -> "%s(instance, \"%s\");"
			XposedCodegenLanguage.KOTLIN -> "%s(instance, \"%s\")"
		}

		return String.format(template, xposedMethod, javaField.fieldNode.fieldInfo.name)
	}

	private val language: XposedCodegenLanguage
		get() = getCodeArea().mainWindow.settings.xposedCodegenLanguage

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(YukiAction::class.java)
		private const val serialVersionUID = 2641585141624592578L

		private val PRIMITIVE_TYPE_MAPPING = mapOf(
			"int" to "Int",
			"byte" to "Byte",
			"short" to "Short",
			"long" to "Long",
			"float" to "Float",
			"double" to "Double",
			"char" to "Char",
			"boolean" to "Boolean",
		)
	}
}
