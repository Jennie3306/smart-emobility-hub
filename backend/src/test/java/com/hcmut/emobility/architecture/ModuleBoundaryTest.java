package com.hcmut.emobility.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces the accepted module rule from README.md and
 * docs/product/module-contracts.md without introducing a second test framework.
 */
class ModuleBoundaryTest {

    private static final Path MAIN_SOURCES =
            Path.of("src/main/java/com/hcmut/emobility");

    private static final Pattern MODULE_NAME = Pattern.compile("m[1-8][a-zA-Z0-9_]*");
    private static final Pattern SERVICE_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+com\\.hcmut\\.emobility\\."
                    + "(m[1-8][a-zA-Z0-9_]*)\\.service\\.([a-zA-Z0-9_$.]+)\\s*;");

    @Test
    void repositoryRespectsCrossModuleServiceBoundary() throws IOException {
        List<String> violations = findViolations(MAIN_SOURCES);
        assertEquals(List.of(), violations,
                "Cross-module service boundary violations:\n"
                        + String.join("\n", violations));
    }

    @Test
    void sameModuleAndSharedImportsAreAllowed(@TempDir Path tempDir) throws IOException {
        writeSource(tempDir, "m6simulator", "IncidentService.java", """
                package com.hcmut.emobility.m6simulator.service;
                import com.hcmut.emobility.m6simulator.service.IncidentWorkflow;
                import com.hcmut.emobility.shared.events.ChargerFailedEvent;
                class IncidentService {}
                """);

        assertTrue(findViolations(tempDir).isEmpty());
    }

    @Test
    void m4MayUseM2PublicReservationCore(@TempDir Path tempDir) throws IOException {
        writeSource(tempDir, "m4parking", "ParkingService.java", """
                package com.hcmut.emobility.m4parking.service;
                import com.hcmut.emobility.m2reservation.service.ReservationCore;
                class ParkingService {}
                """);

        assertTrue(findViolations(tempDir).isEmpty());
    }

    @Test
    void anotherModulesServiceImportIsRejectedWithActionableDiagnostic(
            @TempDir Path tempDir) throws IOException {
        writeSource(tempDir, "m6simulator", "BadIntegration.java", """
                package com.hcmut.emobility.m6simulator.service;
                import com.hcmut.emobility.m5charging.service.ChargingService;
                class BadIntegration {}
                """);

        List<String> violations = findViolations(tempDir);

        assertEquals(1, violations.size());
        assertTrue(violations.getFirst().contains("m6simulator/BadIntegration.java"));
        assertTrue(violations.getFirst().contains("m5charging.service.ChargingService"));
        assertTrue(violations.getFirst().contains("docs/product/module-contracts.md"));
        assertTrue(violations.getFirst().contains("publish a shared event"));
    }

    private static List<String> findViolations(Path sourceRoot) throws IOException {
        List<String> violations = new ArrayList<>();
        if (!Files.isDirectory(sourceRoot)) {
            return List.of("Missing Java source root: " + sourceRoot);
        }

        try (var paths = Files.walk(sourceRoot)) {
            for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String sourceModule = sourceModule(sourceRoot, source);
                if (sourceModule == null) {
                    continue;
                }

                Matcher imports = SERVICE_IMPORT.matcher(Files.readString(source));
                while (imports.find()) {
                    String targetModule = imports.group(1);
                    String importedType = imports.group(2);
                    if (sourceModule.equals(targetModule)
                            || isReservationCoreException(sourceModule, targetModule, importedType)) {
                        continue;
                    }

                    String relativeSource = sourceRoot.relativize(source).toString()
                            .replace('\\', '/');
                    violations.add(relativeSource + " imports "
                            + targetModule + ".service." + importedType
                            + ": feature modules must not import another module's service package "
                            + "(docs/product/module-contracts.md). Depend on a shared contract and "
                            + "publish a shared event instead.");
                }
            }
        }
        return violations;
    }

    private static String sourceModule(Path sourceRoot, Path source) {
        Path relative = sourceRoot.relativize(source);
        if (relative.getNameCount() < 2) {
            return null;
        }
        String candidate = relative.getName(0).toString();
        return MODULE_NAME.matcher(candidate).matches() ? candidate : null;
    }

    private static boolean isReservationCoreException(
            String sourceModule, String targetModule, String importedType) {
        return sourceModule.equals("m4parking")
                && targetModule.equals("m2reservation")
                && importedType.equals("ReservationCore");
    }

    private static void writeSource(
            Path root, String module, String fileName, String content) throws IOException {
        Path moduleDirectory = root.resolve(module);
        Files.createDirectories(moduleDirectory);
        Files.writeString(moduleDirectory.resolve(fileName), content);
    }
}
