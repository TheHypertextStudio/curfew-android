package studio.hypertext.curfew.security

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainAllowlistTest {
    @Test
    fun `Curfew source and configuration use only approved product hosts`() {
        val repository = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
            it.parentFile
        }
            .first { File(it, "settings.gradle.kts").isFile }
        val staleProductDomain = "curfew" + ".app"
        val hostPattern = Regex("https?://([A-Za-z0-9.-]+)")
        val approvedProductHosts = Regex(
            "^curfew(?:-[a-z0-9-]+)?\\.hypertext\\.studio$",
        )
        val violations = repository.walkTopDown()
            .onEnter { it.name !in setOf(".git", ".gradle", ".kotlin", "build") }
            .filter(File::isFile)
            .filter { it.extension in TEXT_EXTENSIONS || it.name in TEXT_NAMES }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    when {
                        staleProductDomain in line -> "${file.relativeTo(repository)}:${index + 1} uses retired product domain"
                        else -> hostPattern.findAll(line)
                            .map { it.groupValues[1].lowercase() }
                            .firstOrNull { host ->
                                "curfew" in host && !approvedProductHosts.matches(host)
                            }
                            ?.let { "${file.relativeTo(repository)}:${index + 1} uses invalid Curfew host $it" }
                    }
                }
            }
            .toList()

        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    private companion object {
        val TEXT_EXTENSIONS = setOf(
            "kt", "kts", "java", "xml", "json", "md", "yaml", "yml", "toml", "properties",
        )
        val TEXT_NAMES = setOf("README", "LICENSE")
    }
}
