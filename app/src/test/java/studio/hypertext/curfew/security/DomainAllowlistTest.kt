package studio.hypertext.curfew.security

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DomainAllowlistTest {
    @Test
    fun `Curfew source and configuration use only approved product hosts`() {
        val repository =
            generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
                it.parentFile
            }.first { File(it, "settings.gradle.kts").isFile }
        val hostPattern = Regex("https?://([A-Za-z0-9.-]+)")
        val approvedProductHosts =
            Regex(
                "^(?:curfew(?:-[a-z0-9-]+)?\\.hypertext\\.studio|(?:[a-z0-9-]+\\.)?thecurfew\\.app)$",
            )
        val violations =
            repository
                .walkTopDown()
                .onEnter { it.name !in setOf(".git", ".gradle", ".kotlin", "build") }
                .filter(File::isFile)
                .filterNot { it.invariantSeparatorsPath.contains("/src/test/") || it.invariantSeparatorsPath.contains("/src/androidTest/") }
                .filter { it.extension in TEXT_EXTENSIONS || it.name in TEXT_NAMES }
                .flatMap { file ->
                    file.readLines().asSequence().mapIndexedNotNull { index, line ->
                        hostPattern
                            .findAll(line)
                            .map { it.groupValues[1].lowercase() }
                            .firstOrNull { host -> "curfew" in host && !approvedProductHosts.matches(host) }
                            ?.let { "${file.relativeTo(repository)}:${index + 1} uses invalid Curfew host $it" }
                    }
                }.toList()

        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    private companion object {
        val TEXT_EXTENSIONS =
            setOf(
                "kt",
                "kts",
                "java",
                "xml",
                "json",
                "md",
                "yaml",
                "yml",
                "toml",
                "properties",
            )
        val TEXT_NAMES = setOf("README", "LICENSE")
    }
}
