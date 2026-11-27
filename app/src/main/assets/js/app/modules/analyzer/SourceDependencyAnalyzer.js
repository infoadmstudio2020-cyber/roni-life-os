/**
 * SourceDependencyAnalyzer.js — Automatic Source File & External Dependency Analyzer for Android.
 *
 * Scans Java (.java) and Kotlin (.kt) source files to:
 * 1. Identify external library imports and usages (AndroidX, Material, Gson, Retrofit, OkHttp, Coroutines, etc.)
 * 2. Map external dependencies to the exact source files that import/use them
 * 3. Map each source file to all external dependencies it requires
 * 4. Cross-reference with project build.gradle files to detect missing or declared dependencies
 * 5. Provide automatic file selection based on dependency selection, and vice versa
 * 6. Provide automated implementation injection into Gradle build scripts
 */

// Pre-compiled regular expressions for fast header scanning
const RE_PACKAGE_STMT = /^package\s+([a-zA-Z0-9_.]+);?/;
const RE_IMPORT_STMT = /^import\s+(?:static\s+)?([a-zA-Z0-9_.*]+);?/;
const RE_DECLARATION_START = /^(?:public\s+|private\s+|protected\s+|internal\s+|abstract\s+|sealed\s+|open\s+|data\s+)*(?:class|interface|enum|object|annotation\s+class)\b/;

// Knowledge base of common Android and Java libraries
export const KNOWN_LIBRARIES = [
  // ── AndroidX Core & UI ──
  {
    pattern: /^androidx\.appcompat\./,
    coordinate: 'androidx.appcompat:appcompat:1.6.1',
    group: 'androidx.appcompat',
    name: 'appcompat',
    version: '1.6.1',
    category: 'AndroidX / Core UI',
    description: 'Backward-compatible Android UI framework and AppCompatActivity',
  },
  {
    pattern: /^androidx\.core\./,
    coordinate: 'androidx.core:core-ktx:1.12.0',
    group: 'androidx.core',
    name: 'core-ktx',
    version: '1.12.0',
    category: 'AndroidX / Core',
    description: 'Core Android Kotlin extensions and compatibility utilities',
  },
  {
    pattern: /^androidx\.activity\./,
    coordinate: 'androidx.activity:activity-ktx:1.8.2',
    group: 'androidx.activity',
    name: 'activity-ktx',
    version: '1.8.2',
    category: 'AndroidX / Activity',
    description: 'Activity-level extensions and modern ComponentActivity support',
  },
  {
    pattern: /^androidx\.fragment\./,
    coordinate: 'androidx.fragment:fragment-ktx:1.6.2',
    group: 'androidx.fragment',
    name: 'fragment-ktx',
    version: '1.6.2',
    category: 'AndroidX / Fragment',
    description: 'Fragment manager, fragment transitions, and lifecycle integration',
  },
  {
    pattern: /^androidx\.recyclerview\./,
    coordinate: 'androidx.recyclerview:recyclerview:1.3.2',
    group: 'androidx.recyclerview',
    name: 'recyclerview',
    version: '1.3.2',
    category: 'AndroidX / List & Grid',
    description: 'High-performance scrolling list and grid widget for Android',
  },
  {
    pattern: /^androidx\.constraintlayout\./,
    coordinate: 'androidx.constraintlayout:constraintlayout:2.1.4',
    group: 'androidx.constraintlayout',
    name: 'constraintlayout',
    version: '2.1.4',
    category: 'AndroidX / Layout',
    description: 'Flexible constraint-based layout management with flat view hierarchy',
  },
  {
    pattern: /^androidx\.cardview\./,
    coordinate: 'androidx.cardview:cardview:1.0.0',
    group: 'androidx.cardview',
    name: 'cardview',
    version: '1.0.0',
    category: 'AndroidX / UI',
    description: 'CardView widget with rounded corners and elevation shadow',
  },
  {
    pattern: /^androidx\.viewpager2\./,
    coordinate: 'androidx.viewpager2:viewpager2:1.0.0',
    group: 'androidx.viewpager2',
    name: 'viewpager2',
    version: '1.0.0',
    category: 'AndroidX / UI',
    description: 'Modern ViewPager built on top of RecyclerView',
  },
  {
    pattern: /^androidx\.swiperefreshlayout\./,
    coordinate: 'androidx.swiperefreshlayout:swiperefreshlayout:1.1.0',
    group: 'androidx.swiperefreshlayout',
    name: 'swiperefreshlayout',
    version: '1.1.0',
    category: 'AndroidX / UI',
    description: 'Pull-to-refresh swipe gesture layout',
  },
  {
    pattern: /^androidx\.lifecycle\./,
    coordinate: 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0',
    group: 'androidx.lifecycle',
    name: 'lifecycle-viewmodel-ktx',
    version: '2.7.0',
    category: 'AndroidX / Architecture',
    description: 'Lifecycle-aware ViewModel, LiveData, and CoroutineScope',
  },
  {
    pattern: /^androidx\.room\./,
    coordinate: 'androidx.room:room-runtime:2.6.1',
    group: 'androidx.room',
    name: 'room-runtime',
    version: '2.6.1',
    category: 'AndroidX / Database',
    description: 'SQLite object-mapping abstraction layer with compile-time verification',
  },
  {
    pattern: /^androidx\.work\./,
    coordinate: 'androidx.work:work-runtime-ktx:2.9.0',
    group: 'androidx.work',
    name: 'work-runtime-ktx',
    version: '2.9.0',
    category: 'AndroidX / Background Work',
    description: 'Guaranteed background task scheduler with constraint support',
  },
  {
    pattern: /^androidx\.navigation\./,
    coordinate: 'androidx.navigation:navigation-fragment-ktx:2.7.7',
    group: 'androidx.navigation',
    name: 'navigation-fragment-ktx',
    version: '2.7.7',
    category: 'AndroidX / Navigation',
    description: 'Declarative navigation graph management across Android screens',
  },
  {
    pattern: /^androidx\.security\./,
    coordinate: 'androidx.security:security-crypto:1.1.0-alpha06',
    group: 'androidx.security',
    name: 'security-crypto',
    version: '1.1.0-alpha06',
    category: 'AndroidX / Security',
    description: 'EncryptedSharedPreferences and EncryptedFile for secure local data',
  },

  // ── Google Material Components ──
  {
    pattern: /^com\.google\.android\.material\./,
    coordinate: 'com.google.android.material:material:1.11.0',
    group: 'com.google.android.material',
    name: 'material',
    version: '1.11.0',
    category: 'Material Design',
    description: 'Google Material Design 3 components, themes, and dynamic color',
  },

  // ── Network & API ──
  {
    pattern: /^okhttp3\./,
    coordinate: 'com.squareup.okhttp3:okhttp:4.12.0',
    group: 'com.squareup.okhttp3',
    name: 'okhttp',
    version: '4.12.0',
    category: 'Networking',
    description: 'Square OkHttp HTTP/2 & WebSockets client for Android and Java',
  },
  {
    pattern: /^retrofit2\./,
    coordinate: 'com.squareup.retrofit2:retrofit:2.9.0',
    group: 'com.squareup.retrofit2',
    name: 'retrofit',
    version: '2.9.0',
    category: 'Networking',
    description: 'Type-safe HTTP and REST client for Android and Java by Square',
  },
  {
    pattern: /^com\.google\.gson\./,
    coordinate: 'com.google.code.gson:gson:2.10.1',
    group: 'com.google.code.gson',
    name: 'gson',
    version: '2.10.1',
    category: 'Serialization / JSON',
    description: 'Google JSON serialization and deserialization library',
  },
  {
    pattern: /^com\.squareup\.moshi\./,
    coordinate: 'com.squareup.moshi:moshi:1.15.1',
    group: 'com.squareup.moshi',
    name: 'moshi',
    version: '1.15.1',
    category: 'Serialization / JSON',
    description: 'Modern JSON library for Kotlin and Java by Square',
  },
  {
    pattern: /^kotlinx\.serialization\./,
    coordinate: 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3',
    group: 'org.jetbrains.kotlinx',
    name: 'kotlinx-serialization-json',
    version: '1.6.3',
    category: 'Serialization / Kotlin',
    description: 'Official Kotlin cross-platform JSON and binary serialization',
  },

  // ── Asynchronous & Concurrency ──
  {
    pattern: /^kotlinx\.coroutines\./,
    coordinate: 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0',
    group: 'org.jetbrains.kotlinx',
    name: 'kotlinx-coroutines-android',
    version: '1.8.0',
    category: 'Kotlin / Concurrency',
    description: 'Official Kotlin coroutines support with Android main thread dispatcher',
  },
  {
    pattern: /^io\.reactivex\.rxjava3\./,
    coordinate: 'io.reactivex.rxjava3:rxandroid:3.0.2',
    group: 'io.reactivex.rxjava3',
    name: 'rxandroid',
    version: '3.0.2',
    category: 'Reactive Programming',
    description: 'RxJava 3 extensions with Android MainThread Schedulers',
  },

  // ── Image Loading ──
  {
    pattern: /^com\.bumptech\.glide\./,
    coordinate: 'com.github.bumptech.glide:glide:4.16.0',
    group: 'com.github.bumptech.glide',
    name: 'glide',
    version: '4.16.0',
    category: 'Image Loading',
    description: 'Fast and efficient image loading and caching framework for Android',
  },
  {
    pattern: /^coil\./,
    coordinate: 'io.coil-kt:coil:2.6.0',
    group: 'io.coil-kt',
    name: 'coil',
    version: '2.6.0',
    category: 'Image Loading / Kotlin',
    description: 'Kotlin-first image loading library backed by Coroutines and OkHttp',
  },
  {
    pattern: /^com\.squareup\.picasso\./,
    coordinate: 'com.squareup.picasso:picasso:2.8',
    group: 'com.squareup.picasso',
    name: 'picasso',
    version: '2.8',
    category: 'Image Loading',
    description: 'Square image downloading and caching library for Android',
  },

  // ── Logging & Utilities ──
  {
    pattern: /^com\.github\.infoadmstudio2020.*native.*logger/i,
    coordinate: 'com.github.infoadmstudio2020-cyber:native-logger-sdk:v1.0.1',
    group: 'com.github.infoadmstudio2020-cyber',
    name: 'native-logger-sdk',
    version: 'v1.0.1',
    category: 'Logging / Utilities / JitPack',
    description: 'High-performance Android native logger SDK via JitPack',
  },
  {
    pattern: /^timber\.log\.Timber/,
    coordinate: 'com.jakewharton.timber:timber:5.0.1',
    group: 'com.jakewharton.timber',
    name: 'timber',
    version: '5.0.1',
    category: 'Logging / Utilities',
    description: 'Utility logger with automatic class tag generation by Jake Wharton',
  },

  // ── Dependency Injection ──
  {
    pattern: /^dagger\./,
    coordinate: 'com.google.dagger:dagger:2.51',
    group: 'com.google.dagger',
    name: 'dagger',
    version: '2.51',
    category: 'Dependency Injection',
    description: 'Compile-time dependency injection framework by Google',
  },
  {
    pattern: /^javax\.inject\./,
    coordinate: 'javax.inject:javax.inject:1',
    group: 'javax.inject',
    name: 'javax.inject',
    version: '1',
    category: 'Dependency Injection',
    description: 'Standard JSR-330 dependency injection annotations',
  },

  // ── Unit Testing ──
  {
    pattern: /^org\.junit\./,
    coordinate: 'junit:junit:4.13.2',
    group: 'junit',
    name: 'junit',
    version: '4.13.2',
    category: 'Testing',
    description: 'Standard Java unit testing framework',
  },
  {
    pattern: /^org\.mockito\./,
    coordinate: 'org.mockito:mockito-core:5.11.0',
    group: 'org.mockito',
    name: 'mockito-core',
    version: '5.11.0',
    category: 'Testing',
    description: 'Tasty mocking framework for unit tests in Java and Android',
  },
];

export class SourceDependencyAnalyzer {
  /**
   * Deep scan project source files and cross-reference with build.gradle files.
   * @param {Object.<string, string|Uint8Array>} files - Project files map
   * @param {object} [options]
   * @returns {object} Analysis result with dependencies, sourceFiles, and statistics
   */
  static analyze(files = {}, options = {}) {
    const filePaths = Object.keys(files);

    // 1. Find all Java & Kotlin source files
    const sourceFilePaths = filePaths.filter(p =>
      (p.endsWith('.java') || p.endsWith('.kt')) &&
      !p.includes('/build/') &&
      !p.includes('/.gradle/')
    );

    // 2. Find all build.gradle and build.gradle.kts files
    const gradleFilePaths = filePaths.filter(p =>
      p.endsWith('.gradle') || p.endsWith('.gradle.kts')
    );

    // Read all gradle files to extract declared dependencies
    const declaredDeps = [];
    const gradleContents = {};

    for (const gp of gradleFilePaths) {
      const content = String(files[gp] || '');
      gradleContents[gp] = content;
      // Extract dependency lines: implementation '...', api '...', etc.
      const depMatches = content.matchAll(/(?:implementation|api|compileOnly|runtimeOnly|testImplementation|androidTestImplementation)\s*\(?['"]([^'"]+)['"]\)?/g);
      for (const m of depMatches) {
        const notation = m[1].trim();
        const parts = notation.split(':');
        declaredDeps.push({
          file: gp,
          raw: m[0],
          notation,
          group: parts[0] || '',
          name: parts[1] || '',
          version: parts[2] || '',
        });
      }
    }

    // 3. Scan each source file for package and import statements
    const sourceFilesInfo = [];
    const depMap = new Map(); // coordinate -> dependency info

    for (const sp of sourceFilePaths) {
      const content = String(files[sp] || '');
      const lines = content.split('\n');

      let pkg = '';
      const imports = [];

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('//') || trimmed.startsWith('/*') || trimmed.startsWith('*')) continue;
        if (trimmed.startsWith('package ')) {
          const m = trimmed.match(RE_PACKAGE_STMT);
          if (m) pkg = m[1];
        } else if (trimmed.startsWith('import ')) {
          const m = trimmed.match(RE_IMPORT_STMT);
          if (m) imports.push(m[1]);
        } else if (RE_DECLARATION_START.test(trimmed)) {
          // In Java/Kotlin, all package & imports appear before type definitions. Stop early.
          break;
        }
      }

      // Determine module from file path (e.g. "library/src/main/..." -> "library")
      const pathParts = sp.split('/');
      const moduleName = pathParts.length > 1 && pathParts[1] === 'src' ? pathParts[0] : (pathParts[0] || 'app');

      const srcEntry = {
        path: sp,
        fileName: pathParts[pathParts.length - 1],
        module: moduleName,
        package: pkg,
        imports,
        dependencies: [],
      };

      // Match imports against known library definitions
      for (const imp of imports) {
        // Skip standard JDK / Android SDK framework packages (part of android.jar runtime)
        if (this.#isStandardPlatform(imp, pkg)) continue;

        // Match with known libraries
        let matchedLib = KNOWN_LIBRARIES.find(lib => lib.pattern.test(imp));

        if (!matchedLib) {
          // If not in known libraries, create generic external dependency coordinate
          matchedLib = this.#inferGenericLibrary(imp);
        }

        if (matchedLib) {
          const coord = matchedLib.coordinate;
          if (!srcEntry.dependencies.includes(coord)) {
            srcEntry.dependencies.push(coord);
          }

          if (!depMap.has(coord)) {
            // Check if declared in any build.gradle
            const parts = coord.split(':');
            const groupArtifact = `${parts[0]}:${parts[1]}`;
            const declaredIn = declaredDeps
              .filter(d => d.notation.startsWith(groupArtifact) || (parts[0] && d.group === parts[0] && d.name === parts[1]))
              .map(d => d.file);

            depMap.set(coord, {
              coordinate: coord,
              group: matchedLib.group,
              name: matchedLib.name,
              version: matchedLib.version,
              category: matchedLib.category || 'Third-Party Library',
              description: matchedLib.description || 'External Java / Android Library',
              isDeclared: declaredIn.length > 0,
              declaredInModules: declaredIn,
              sourceFiles: [],
              matchedImports: new Set(),
            });
          }

          const depObj = depMap.get(coord);
          if (!depObj.sourceFiles.includes(sp)) {
            depObj.sourceFiles.push(sp);
          }
          depObj.matchedImports.add(imp);
        }
      }

      sourceFilesInfo.push(srcEntry);
    }

    // Convert Set to Array in matchedImports
    const dependencyList = Array.from(depMap.values()).map(d => ({
      ...d,
      matchedImports: Array.from(d.matchedImports),
    }));

    // Sort: missing dependencies first, then by name
    dependencyList.sort((a, b) => {
      if (a.isDeclared !== b.isDeclared) return a.isDeclared ? 1 : -1;
      return a.name.localeCompare(b.name);
    });

    const missingCount = dependencyList.filter(d => !d.isDeclared).length;
    const declaredCount = dependencyList.filter(d => d.isDeclared).length;

    return {
      dependencies: dependencyList,
      sourceFiles: sourceFilesInfo,
      totalCount: dependencyList.length,
      missingCount,
      declaredCount,
      gradleFiles: gradleFilePaths,
    };
  }

  /**
   * Filter source files based on selected dependency coordinate.
   * @param {object} analysis - Result of SourceDependencyAnalyzer.analyze()
   * @param {string} coordinate - e.g. "com.google.code.gson:gson:2.10.1"
   * @returns {object[]} List of matching source file entries
   */
  static selectFilesForDependency(analysis, coordinate) {
    if (!analysis || !coordinate) return [];
    return analysis.sourceFiles.filter(sf => sf.dependencies.includes(coordinate));
  }

  /**
   * Filter dependencies based on selected source file path.
   * @param {object} analysis - Result of SourceDependencyAnalyzer.analyze()
   * @param {string} filePath - Source file path
   * @returns {object[]} List of matching dependency entries
   */
  static selectDependenciesForFile(analysis, filePath) {
    if (!analysis || !filePath) return [];
    const srcFile = analysis.sourceFiles.find(sf => sf.path === filePath);
    if (!srcFile) return [];
    return analysis.dependencies.filter(d => srcFile.dependencies.includes(d.coordinate));
  }

  /**
   * Determines if an import is standard built-in Java or standard Android platform (android.*).
   */
  static #isStandardPlatform(importStr, currentPackage) {
    // Project own package classes
    if (currentPackage && importStr.startsWith(currentPackage)) return true;

    // Standard JDK packages
    if (importStr.startsWith('java.') || importStr.startsWith('javax.net.') ||
        importStr.startsWith('javax.crypto.') || importStr.startsWith('javax.security.') ||
        importStr.startsWith('javax.xml.') || importStr.startsWith('org.w3c.') ||
        importStr.startsWith('org.xml.')) {
      return true;
    }

    // Standard Android SDK framework (android.os, android.view, android.content, etc.)
    // Note: androidx.* is external, android.* is built into android.jar
    if (importStr.startsWith('android.') && !importStr.startsWith('androidx.')) {
      return true;
    }

    // Kotlin standard library (included by kotlin gradle plugin)
    if (importStr.startsWith('kotlin.') && !importStr.startsWith('kotlinx.coroutines') && !importStr.startsWith('kotlinx.serialization')) {
      return true;
    }

    return false;
  }

  /**
   * Infers a generic library coordinate from an unknown external import.
   */
  static #inferGenericLibrary(importStr) {
    const parts = importStr.split('.');
    if (parts.length < 2) return null;

    const group = `${parts[0]}.${parts[1]}`;
    const name = parts[2] ? parts[2].toLowerCase() : parts[1].toLowerCase();

    return {
      pattern: new RegExp(`^${parts[0]}\\.${parts[1]}\\.`),
      coordinate: `${group}:${name}:1.0.0`,
      group,
      name,
      version: '1.0.0',
      category: 'External Library',
      description: `Inferred from source import: ${importStr}`,
    };
  }
}
