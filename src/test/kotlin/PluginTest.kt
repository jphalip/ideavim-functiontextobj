import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes

data class TextObjectTest(
    val file: String,
    val vif: Pair<String, String>,
    val vaf: Pair<String, String>,
    val cif: String,
    val daf: String,
)

class PluginTest {
    init {
        di =
            DI {
                extend(di)
                bindSingleton<CIServer>(overrides = true) {
                    object : CIServer by NoCIServer {
                        override fun reportTestFailure(
                            testName: String,
                            message: String,
                            details: String,
                            linkToLogs: String?,
                        ) {
                            if (details.contains("ProcessNotCreatedException") || details.contains("NoRInterpreterException") ||
                                // Rider spurious errors
                                details.contains("LicensingFacade is null") ||
                                details.contains("java.util.ConcurrentModificationException")
                            ) {
                                // Ignore some spurious errors that might happen with "test.py",
                                // "test.r", or "test.cs", respectively because the Python & R interpreters are not set in IntelliJ, and because of some apparent licencing and concurrency issues in Rider
                                return
                            }
                            fail { "$testName has failed: $message. \n$details" }
                        }
                    }
                }
            }
    }

    fun installBasePlugins(context: IDETestContext) {
        PluginConfigurator(context).installPluginFromPath(
            Path(
                System.getProperty(
                    "path.to.build.plugin",
                ),
            ),
        )
        PluginConfigurator(context).installPluginFromPluginManager(
            "IdeaVIM",
            System.getProperty("ideaVimPluginVersion"),
        )
    }

    fun installIntellijUltimatePlugins(context: IDETestContext) {
        val plugins = System.getProperty("intellijUltimatePlugins").split(",")
        for (plugin in plugins) {
            val (id, version) = plugin.split(":")
            PluginConfigurator(context).installPluginFromPluginManager(id, version)
        }
    }

    @Test
    fun testRider() {
        Starter
            .newContext(
                "testRider",
                TestCase(
                    IdeProductProvider.RD,
                    LocalProjectInfo(Path(System.getProperty("path.to.project"))),
                ).withVersion(System.getProperty("platform.version")),
            ).apply {
                installBasePlugins(this)
            }.runIdeWithDriver()
            .useDriverAndCloseIde {
                ideFrame {
                    // Ensure the IDE frame is visible and ready before proceeding.
                    waitFound()

                    // Allow the IDE to finish indexing and other background tasks
                    // to ensure the project is fully ready for testing.
                    waitForIndicators(5.minutes)

                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test.cs",
                            vif = Pair("\n    if (n < 0)", "return result;\n"),
                            vaf = Pair("public static int Factorial(int n)", "return result;\n}"),
                            cif = "public static int Factorial(int n) {}",
                            daf = "// A factorial function\n",
                        ),
                    )
                }
            }
    }

    @Test
    fun testCLion() {
        Starter
            .newContext(
                "testCLion",
                TestCase(
                    IdeProductProvider.CL,
                    LocalProjectInfo(Path(System.getProperty("path.to.project"))),
                ).withVersion(System.getProperty("platform.version")),
            ).apply {
                installBasePlugins(this)
            }.runIdeWithDriver()
            .useDriverAndCloseIde {
                ideFrame {
                    // Ensure the IDE frame is visible and ready before proceeding.
                    waitFound()

                    // Allow the IDE to finish indexing and other background tasks
                    // to ensure the project is fully ready for testing.
                    waitForIndicators(5.minutes)

                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test.c",
                            vif = Pair("\n    if (n < 0)", "return result;\n"),
                            vaf = Pair("// A factorial function\nint factorial", "return result;\n}"),
                            cif = "int factorial(int n) {}",
                            daf = "#include <stdio.h>\n\n",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test.cpp",
                            vif = Pair("\n    if (n < 0)", "return result;\n"),
                            vaf = Pair("// A factorial function\nint factorial", "return result;\n}"),
                            cif = "int factorial(int n) {}",
                            daf = "#include <iostream>\n\n",
                        ),
                    )
                }
            }
    }

    @Test
    fun testIntelliJ() {
        Starter
            .newContext(
                "testIntelliJ",
                TestCase(
                    IdeProductProvider.IU,
                    LocalProjectInfo(Path(System.getProperty("path.to.project"))),
                ).withVersion(System.getProperty("platform.version")),
            ).apply {
                installBasePlugins(this)
                installIntellijUltimatePlugins(this)
            }.runIdeWithDriver()
            .useDriverAndCloseIde {
                ideFrame {
                    // Ensure the IDE frame is visible and ready before proceeding.
                    waitFound()

                    // Allow the IDE to finish indexing and other background tasks
                    // to ensure the project is fully ready for testing.
                    waitForIndicators(5.minutes)

                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test.dart",
                            vif = Pair("\n  if (n < 0)", "return result;\n"),
                            vaf = Pair("int factorial", "return result;\n}"),
                            cif = "int factorial(int n) {}",
                            daf = "// A factorial function\n",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test.py",
                            vif = Pair("if not isinstance(n, int):", "return result"),
                            vaf = Pair("def factorial(n):", "return result"),
                            cif = "def factorial(n):\n    ",
                            daf = "# A factorial function",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test-js.js",
                            vif = Pair("\n  if (!Number.isInteger(n))", "return result;\n"),
                            vaf = Pair("function factorial(n)", "return result;\n}"),
                            cif = "function factorial(n) {}",
                            daf = "// A factorial function\n",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test-js-arrow.ts",
                            vif = Pair("\n    if (!Number.isInteger(n))", "return result;\n"),
                            vaf = Pair("const factorial = n =>", "return result;\n}"),
                            cif = "const factorial = n => {}",
                            daf = "// A factorial arrow function\n const factorial = ;",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test-ts.ts",
                            vif = Pair("\n    if (!Number.isInteger(n))", "return result;\n"),
                            vaf = Pair("function factorial(n: number)", "return result;\n}"),
                            cif = "function factorial(n: number): number {}",
                            daf = "// A factorial function\n",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test-ts-arrow.ts",
                            vif = Pair("\n    if (!Number.isInteger(n))", "return result;\n"),
                            vaf = Pair("const factorial = (n: number): number =>", "return result;\n}"),
                            cif = "const factorial = (n: number): number => {}",
                            daf = "// A factorial arrow function\n const factorial = ;",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test.go",
                            vif = Pair("\n\tif n < 0", "return result\n"),
                            vaf = Pair("func factorial(n int)", "return result\n}"),
                            cif = "func factorial(n int) int {}\n",
                            daf = "// A factorial function\n\n",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test.java",
                            vif = Pair("\n    if (n < 0)", "return result;\n"),
                            vaf = Pair("public static int factorial(int n)", "return result;\n}"),
                            cif = "public static int factorial(int n) {}",
                            daf = "// A factorial function\n",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test.kts",
                            vif = Pair("\n    require", "return result\n"),
                            vaf = Pair("// A factorial function", "return result\n}"),
                            cif = "fun factorial(n: Int): Int {}",
                            daf = "import a.c.b.Defg\n\n",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test.php",
                            vif = Pair("\n    if (!is_int(\$n))", "return \$result;\n"),
                            vaf = Pair("function factorial(\$n)", "return \$result;\n}"),
                            cif = "function factorial(\$n) {}",
                            daf = "// A factorial function\n",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test.pl",
                            vif = Pair("\n    my \$n = shift;", "return \$result;\n"),
                            vaf = Pair("sub factorial", "return \$result;\n}"),
                            cif = "sub factorial {}",
                            daf = "# A factorial function\n",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test.r",
                            vif = Pair("\n    if (!is.numeric(n)", "return(result)\n"),
                            vaf = Pair("function(n)", "return(result)\n}"),
                            cif = "function(n) {}",
                            daf = "# A factorial function\nfactorial <- ",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test.rb",
                            vif = Pair("raise TypeError", "return result"),
                            vaf = Pair("def factorial(n)", "return result\nend"),
                            cif = "def factorial(n)\n  \nend",
                            daf = "# A factorial function\n",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test.rs",
                            vif = Pair("\n    if n < 0", "Ok(result)\n"),
                            vaf = Pair("// A factorial function", "Ok(result)\n}"),
                            cif = "n factorial(n: i32) -> Result<i32, &'static str> {}",
                            daf = "// This is a Rust file\n\n",
                        ),
                    )
                    runTextObjectTest(
                        driver,
                        TextObjectTest(
                            "test.scala",
                            vif = Pair("\n  require(n >= 0", "result\n"),
                            vaf = Pair("// A factorial function", "result\n}"),
                            cif = "def factorial(n: Int): Int = {}",
                            daf = "// This is a Scala file\n\n",
                        ),
                    )
                    testIssue9(driver)
                    testIssue11(driver)
                }
            }
    }

    fun runTextObjectTest(
        driver: Driver,
        test: TextObjectTest,
    ) {
        driver.openFile(test.file)

        driver.ideFrame {
            // Grab focus on the code editor
            codeEditor().click()

            // vif
            keyboard {
                escape()
                typeText("6G")
                typeText("vif")
            }
            assertTrue(
                codeEditor().getSelection()?.let {
                    it.startsWith(test.vif.first) && it.endsWith(test.vif.second)
                } ?: false,
            )

            // vaf
            keyboard {
                escape()
                typeText("6G")
                typeText("vaf")
            }
            assertTrue(
                codeEditor().getSelection()?.let {
                    it.startsWith(test.vaf.first) && it.endsWith(test.vaf.second)
                } ?: false,
            )

            // cif
            keyboard {
                escape()
                typeText("6G")
                typeText("cif")
            }
            assertTrue(
                codeEditor().text.endsWith(test.cif),
            )

            // daf
            keyboard {
                escape()
                typeText("daf")
            }
            assertTrue(
                codeEditor().text.endsWith(test.daf),
            )
        }
    }

    // https://github.com/jphalip/ideavim-functiontextobj/issues/9
    fun testIssue9(driver: Driver) {
        driver.openFile("issue9.java")
        driver.ideFrame {
            // Grab focus on the code editor
            codeEditor().click()

            // Copy-paste method1 (yaf + P)
            keyboard {
                escape()
                typeText("4G")
                typeText("yaf")
                typeText("P")
            }
            assertTrue(
                codeEditor().text.endsWith(
                    "{\n\n    public void method1() {}public void method1() {}\n\n    public void method2() {}\n\n}",
                ),
            )

            // Undo paste
            keyboard {
                typeText("u")
            }

            // Copy-paste method2 (yaf + P)
            keyboard {
                escape()
                typeText("6G")
                typeText("yaf")
                typeText("P")
            }
            assertTrue(
                codeEditor().text.endsWith(
                    "{\n\n    public void method1() {}\n\n    public void method2() {}public void method2() {}\n\n}",
                ),
            )
        }
    }

    // https://github.com/jphalip/ideavim-functiontextobj/issues/11
    fun testIssue11(driver: Driver) {
        driver.openFile("issue11.kts")
        driver.ideFrame {
            // Grab focus on the code editor
            codeEditor().click()

            // Block body function
            keyboard {
                escape()
                typeText("4G")
                typeText("vif")
            }
            assertEquals(
                "\n    println(\"hello world\")\n",
                codeEditor().getSelection() ?: "",
            )

            // Expression body function
            keyboard {
                escape()
                typeText("8G")
                typeText("vif")
            }
            assertEquals(
                "println(\"bonjour monde\")",
                codeEditor().getSelection() ?: "",
            )
        }
    }
}
