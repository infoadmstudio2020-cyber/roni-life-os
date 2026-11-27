/**
 * AIProjectSpecGenerator.js — Comprehensive AI-Ready Project Specification & Architecture Generator.
 *
 * Produces structured, self-contained JSON specifications that any AI (ChatGPT, Claude, Gemini, DeepSeek)
 * can parse to generate, scaffold, or update an entire Android or Java project without missing details.
 */
import { ClipboardUtil } from '../../core/ClipboardUtil.js';
import { WebViewBridge } from '../../core/WebViewBridge.js';

export const TEMPLATES_CATALOG = [
  {
    id: 'android-java-library',
    name: 'Android Library + Companion App',
    category: 'library',
    badge: 'Popular',
    icon: 'android',
    color: '#22D3EE',
    language: 'Java',
    buildDsl: 'Groovy / Kotlin DSL',
    targetArtifact: 'AAR + Sample APK',
    description: 'Production-ready Android Library (AAR) with companion demonstration app, JitPack/Maven Central publishing, and GitHub Actions CI/CD.',
    recommendedBuildEnvironment: {
      gradleVersion: '8.13',
      agpVersion: '8.10.1',
      jdkVersion: '17',
      compileSdk: 35,
      minSdk: 21,
      targetSdk: 35
    },
    defaultConfig: {
      projectName: 'AndroidAwesomeLib',
      libraryModuleName: 'library',
      appModuleName: 'app',
      packageName: 'com.example.awesomelib',
      groupId: 'com.github.username',
      artifactId: 'awesome-lib',
      version: '1.0.0',
      description: 'A modular and high-performance Android Library',
      author: '<USER_CUSTOMIZABLE: Author Name>',
      license: 'Apache-2.0',
      compileSdk: 35,
      minSdk: 21,
      targetSdk: 35
    },
    dependencies: [
      { coordinate: 'androidx.core:core-ktx:1.15.0', scope: 'api', purpose: 'AndroidX Core runtime extensions', required: true },
      { coordinate: 'androidx.appcompat:appcompat:1.7.0', scope: 'api', purpose: 'Backward compatible UI & Activity foundation', required: true },
      { coordinate: 'com.google.android.material:material:1.12.0', scope: 'implementation', purpose: 'Material Design 3 Components', required: true },
      { coordinate: 'junit:junit:4.13.2', scope: 'testImplementation', purpose: 'Standard JVM unit testing', required: true },
      { coordinate: 'androidx.test.ext:junit:1.2.1', scope: 'androidTestImplementation', purpose: 'Android instrumentation testing runner', required: true },
      { coordinate: 'androidx.test.espresso:espresso-core:3.6.1', scope: 'androidTestImplementation', purpose: 'UI interaction testing framework', required: false }
    ],
    fileStructure: [
      { path: 'settings.gradle', purpose: 'Root project settings & module includes (:library, :app)', required: true },
      { path: 'build.gradle', purpose: 'Root buildscript with AGP plugin definition and clean task', required: true },
      { path: 'gradle.properties', purpose: 'AndroidX enable, non-transitive R, and JVM memory tuning', required: true },
      { path: 'gradle/wrapper/gradle-wrapper.properties', purpose: 'Gradle distribution version 8.13 declaration', required: true },
      { path: 'library/build.gradle', purpose: 'AAR library module build configuration with maven-publish', required: true },
      { path: 'library/src/main/AndroidManifest.xml', purpose: 'Library manifest declaration', required: true },
      { path: 'library/src/main/java/com/example/awesomelib/AwesomeLib.java', purpose: 'Primary library public API facade and singleton/builder', required: true },
      { path: 'library/src/main/java/com/example/awesomelib/AwesomeLibConfig.java', purpose: 'Immutable library configuration builder', required: true },
      { path: 'library/src/test/java/com/example/awesomelib/AwesomeLibTest.java', purpose: 'Unit tests for core library logic', required: true },
      { path: 'app/build.gradle', purpose: 'Companion demonstration application with implementation project(":library")', required: true },
      { path: 'app/src/main/AndroidManifest.xml', purpose: 'Companion app manifest with launcher activity', required: true },
      { path: 'app/src/main/java/com/example/awesomelib/sample/MainActivity.java', purpose: 'Sample activity demonstrating library usage', required: true },
      { path: 'app/src/main/res/layout/activity_main.xml', purpose: 'Sample app interactive layout', required: true },
      { path: '.github/workflows/build-and-publish.yml', purpose: 'Automated CI/CD matrix build and JitPack/Maven publishing workflow', required: true },
      { path: 'jitpack.yml', purpose: 'JitPack container JDK 17 environment declaration', required: true },
      { path: 'README.md', purpose: 'Documentation, installation badges, and quick-start instructions', required: true },
      { path: 'LICENSE', purpose: 'Open-source software license file', required: true }
    ],
    customizableFields: [
      { key: 'packageName', placeholder: 'com.yourname.library', description: 'Unique Android namespace & Java package' },
      { key: 'groupId', placeholder: 'com.github.yourusername', description: 'Maven Group ID (use GitHub username for JitPack)' },
      { key: 'artifactId', placeholder: 'your-library-name', description: 'Maven Artifact ID matching library repository' },
      { key: 'libraryModuleName', placeholder: 'library', description: 'Gradle module directory name for library AAR' }
    ]
  },
  {
    id: 'android-app',
    name: 'Android Application (Single/Multi-Module)',
    category: 'app',
    badge: 'Standard',
    icon: 'smartphone',
    color: '#10B981',
    language: 'Java / Kotlin',
    buildDsl: 'Groovy / Kotlin DSL',
    targetArtifact: 'APK + AAB',
    description: 'Clean, production-ready Android Application project for Android Studio with Material 3, ViewBinding, and automated CI/CD.',
    recommendedBuildEnvironment: {
      gradleVersion: '8.13',
      agpVersion: '8.10.1',
      jdkVersion: '17',
      compileSdk: 35,
      minSdk: 24,
      targetSdk: 35
    },
    defaultConfig: {
      projectName: 'ModernAndroidApp',
      appModuleName: 'app',
      packageName: 'com.example.modernapp',
      version: '1.0.0',
      versionCode: 1,
      description: 'Modern Android Studio Application',
      author: '<USER_CUSTOMIZABLE: Author Name>',
      license: 'Proprietary / MIT',
      compileSdk: 35,
      minSdk: 24,
      targetSdk: 35
    },
    dependencies: [
      { coordinate: 'androidx.appcompat:appcompat:1.7.0', scope: 'implementation', purpose: 'AppCompat activity & UI base', required: true },
      { coordinate: 'com.google.android.material:material:1.12.0', scope: 'implementation', purpose: 'Material Design 3 UI Theme & Controls', required: true },
      { coordinate: 'androidx.constraintlayout:constraintlayout:2.2.0', scope: 'implementation', purpose: 'ConstraintLayout responsive UI layout', required: true },
      { coordinate: 'androidx.lifecycle:lifecycle-viewmodel:2.8.7', scope: 'implementation', purpose: 'Lifecycle-aware ViewModel architecture', required: true },
      { coordinate: 'junit:junit:4.13.2', scope: 'testImplementation', purpose: 'Standard JVM test suite', required: true }
    ],
    fileStructure: [
      { path: 'settings.gradle', purpose: 'Project settings including :app module', required: true },
      { path: 'build.gradle', purpose: 'Root build configuration with clean task', required: true },
      { path: 'gradle.properties', purpose: 'AndroidX enable and JVM options', required: true },
      { path: 'gradle/wrapper/gradle-wrapper.properties', purpose: 'Gradle 8.13 wrapper specification', required: true },
      { path: 'app/build.gradle', purpose: 'com.android.application build script with defaultConfig & buildTypes', required: true },
      { path: 'app/proguard-rules.pro', purpose: 'ProGuard release obfuscation & optimization rules', required: true },
      { path: 'app/src/main/AndroidManifest.xml', purpose: 'App manifest with launcher activity and permissions', required: true },
      { path: 'app/src/main/java/com/example/modernapp/MainActivity.java', purpose: 'Main launcher activity implementation', required: true },
      { path: 'app/src/main/res/layout/activity_main.xml', purpose: 'Root activity UI layout with Material 3 components', required: true },
      { path: 'app/src/main/res/values/strings.xml', purpose: 'Localized string resources', required: true },
      { path: 'app/src/main/res/values/colors.xml', purpose: 'Semantic color palette definitions', required: true },
      { path: 'app/src/main/res/values/themes.xml', purpose: 'Base Material 3 day/night themes', required: true },
      { path: '.github/workflows/ci.yml', purpose: 'GitHub Actions continuous integration build & test', required: true },
      { path: 'README.md', purpose: 'Project documentation and developer setup guide', required: true }
    ],
    customizableFields: [
      { key: 'packageName', placeholder: 'com.yourcompany.app', description: 'Application ID / package namespace' },
      { key: 'versionCode', placeholder: '1', description: 'Integer build code for Google Play release' },
      { key: 'version', placeholder: '1.0.0', description: 'User-facing version name' }
    ]
  },
  {
    id: 'multi-module-android-sdk',
    name: 'Multi-Module Android SDK (:core + :ui + :app)',
    category: 'sdk',
    badge: 'Enterprise',
    icon: 'account_tree',
    color: '#F59E0B',
    language: 'Java / Kotlin',
    buildDsl: 'Groovy / Kotlin DSL',
    targetArtifact: 'Multiple AARs + Demo APK',
    description: 'Enterprise multi-module SDK architecture decoupling pure business logic (:core) from customizable UI elements (:ui) and demo app (:app).',
    recommendedBuildEnvironment: {
      gradleVersion: '8.13',
      agpVersion: '8.10.1',
      jdkVersion: '17',
      compileSdk: 35,
      minSdk: 21,
      targetSdk: 35
    },
    defaultConfig: {
      projectName: 'EnterpriseSDK',
      coreModuleName: 'core',
      uiModuleName: 'ui',
      appModuleName: 'app',
      packageName: 'com.example.enterprisesdk',
      groupId: 'com.github.username',
      version: '1.0.0',
      description: 'Decoupled Multi-Module Enterprise Android SDK',
      author: '<USER_CUSTOMIZABLE: Author Name>',
      license: 'Apache-2.0',
      compileSdk: 35,
      minSdk: 21,
      targetSdk: 35
    },
    dependencies: [
      { coordinate: 'androidx.core:core-ktx:1.15.0', scope: 'api', purpose: 'Core platform foundation', required: true },
      { coordinate: 'com.google.android.material:material:1.12.0', scope: 'api (in :ui module)', purpose: 'SDK UI widgets styling', required: true },
      { coordinate: 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1', scope: 'api', purpose: 'Asynchronous concurrency & Flow', required: false },
      { coordinate: 'junit:junit:4.13.2', scope: 'testImplementation', purpose: 'Module unit test verification', required: true }
    ],
    fileStructure: [
      { path: 'settings.gradle', purpose: 'Include :core, :ui, and :app modules', required: true },
      { path: 'build.gradle', purpose: 'Root build file declaring plugins with apply false', required: true },
      { path: 'core/build.gradle', purpose: 'Headless core logic AAR library with maven-publish', required: true },
      { path: 'core/src/main/AndroidManifest.xml', purpose: 'Core SDK manifest', required: true },
      { path: 'core/src/main/java/com/example/enterprisesdk/core/SdkEngine.java', purpose: 'Core SDK backend logic & engine', required: true },
      { path: 'ui/build.gradle', purpose: 'UI AAR library depending on project(":core")', required: true },
      { path: 'ui/src/main/AndroidManifest.xml', purpose: 'UI module manifest', required: true },
      { path: 'ui/src/main/java/com/example/enterprisesdk/ui/SdkCustomView.java', purpose: 'Custom UI component rendered in host apps', required: true },
      { path: 'app/build.gradle', purpose: 'Consumer sample application implementing project(":ui")', required: true },
      { path: '.github/workflows/sdk-ci.yml', purpose: 'Matrix build publishing both :core and :ui to JitPack', required: true },
      { path: 'README.md', purpose: 'Multi-module consumption & integration guide', required: true }
    ],
    customizableFields: [
      { key: 'coreModuleName', placeholder: 'core', description: 'Name of the core engine module' },
      { key: 'uiModuleName', placeholder: 'ui', description: 'Name of the UI components module' },
      { key: 'groupId', placeholder: 'com.github.yourorg', description: 'Maven Group ID' }
    ]
  },
  {
    id: 'pure-java-library',
    name: 'Pure Java/Kotlin Library (JAR)',
    category: 'jvm',
    badge: 'Universal',
    icon: 'coffee',
    color: '#EAB308',
    language: 'Java / Kotlin',
    buildDsl: 'Groovy',
    targetArtifact: 'JAR Artifact',
    description: 'Pure JVM library generating lightweight JAR artifact, suitable for backend services, Android shared logic, and multiplatform algorithms.',
    recommendedBuildEnvironment: {
      gradleVersion: '8.13',
      agpVersion: 'N/A (Pure Java Library)',
      jdkVersion: '17 or 21',
      compileSdk: 'N/A',
      minSdk: 'N/A',
      targetSdk: 'N/A'
    },
    defaultConfig: {
      projectName: 'JavaCommonUtils',
      packageName: 'com.example.common',
      groupId: 'com.github.username',
      artifactId: 'common-utils',
      version: '1.0.0',
      description: 'Zero-dependency universal JVM library',
      author: '<USER_CUSTOMIZABLE: Author Name>',
      license: 'MIT',
      javaVersion: '17'
    },
    dependencies: [
      { coordinate: 'org.junit.jupiter:junit-jupiter:5.10.2', scope: 'testImplementation', purpose: 'JUnit 5 modern unit testing engine', required: true },
      { coordinate: 'org.assertj:assertj-core:3.25.3', scope: 'testImplementation', purpose: 'Fluent assertions for unit testing', required: false }
    ],
    fileStructure: [
      { path: 'settings.gradle', purpose: 'Root project name configuration', required: true },
      { path: 'build.gradle', purpose: 'java-library & maven-publish plugins with sourcesJar and javadocJar', required: true },
      { path: 'gradle.properties', purpose: 'JVM arguments and properties', required: true },
      { path: 'src/main/java/com/example/common/CommonUtils.java', purpose: 'Universal algorithm and utility methods', required: true },
      { path: 'src/test/java/com/example/common/CommonUtilsTest.java', purpose: 'JUnit 5 unit test verification', required: true },
      { path: 'jitpack.yml', purpose: 'JitPack JDK 17 build declaration', required: true },
      { path: '.github/workflows/ci.yml', purpose: 'GitHub Actions JVM compilation and publish workflow', required: true },
      { path: 'README.md', purpose: 'Library API usage guide and Maven/Gradle coordinates', required: true }
    ],
    customizableFields: [
      { key: 'packageName', placeholder: 'com.yourname.utils', description: 'Java package namespace' },
      { key: 'groupId', placeholder: 'com.github.username', description: 'Maven Group ID' },
      { key: 'artifactId', placeholder: 'common-utils', description: 'Maven Artifact ID' }
    ]
  },
  {
    id: 'android-library-standalone',
    name: 'Android Library Standalone (AAR Only)',
    category: 'library',
    badge: 'Lightweight',
    icon: 'layers',
    color: '#38BDF8',
    language: 'Java',
    buildDsl: 'Groovy',
    targetArtifact: 'AAR Artifact Only',
    description: 'Minimalist single-module Android Library project without sample app overhead, designed for embeddable SDKs and fast CI builds.',
    recommendedBuildEnvironment: {
      gradleVersion: '8.13',
      agpVersion: '8.10.1',
      jdkVersion: '17',
      compileSdk: 35,
      minSdk: 21,
      targetSdk: 35
    },
    defaultConfig: {
      projectName: 'AndroidMinimalLib',
      libraryModuleName: 'library',
      packageName: 'com.example.minimallib',
      groupId: 'com.github.username',
      artifactId: 'minimal-lib',
      version: '1.0.0',
      description: 'Single-module lightweight Android Library',
      author: '<USER_CUSTOMIZABLE: Author Name>',
      license: 'Apache-2.0',
      compileSdk: 35,
      minSdk: 21,
      targetSdk: 35
    },
    dependencies: [
      { coordinate: 'androidx.annotation:annotation:1.9.1', scope: 'compileOnly', purpose: 'Compile-time annotations without runtime footprint', required: true },
      { coordinate: 'androidx.core:core:1.15.0', scope: 'api', purpose: 'Essential AndroidX primitives', required: true },
      { coordinate: 'junit:junit:4.13.2', scope: 'testImplementation', purpose: 'Unit tests', required: true }
    ],
    fileStructure: [
      { path: 'settings.gradle', purpose: 'Include :library module', required: true },
      { path: 'build.gradle', purpose: 'Root build configuration', required: true },
      { path: 'library/build.gradle', purpose: 'AAR module configuration with maven-publish', required: true },
      { path: 'library/proguard-rules.pro', purpose: 'Consumer ProGuard rules embedded in AAR', required: true },
      { path: 'library/src/main/AndroidManifest.xml', purpose: 'Library manifest', required: true },
      { path: 'library/src/main/java/com/example/minimallib/MinimalLib.java', purpose: 'Library core class', required: true },
      { path: 'jitpack.yml', purpose: 'JitPack JDK build configuration', required: true },
      { path: 'README.md', purpose: 'Installation and usage instructions', required: true }
    ],
    customizableFields: [
      { key: 'packageName', placeholder: 'com.yourname.library', description: 'Library namespace' },
      { key: 'artifactId', placeholder: 'minimal-lib', description: 'Artifact identifier' }
    ]
  }
];

export class AIProjectSpecGenerator {
  /**
   * Generate an exhaustive, AI-Ready Project Specification JSON object.
   *
   * @param {string} templateId - Identifier of the template from TEMPLATES_CATALOG
   * @param {object} customConfig - User-provided configurations and overrides
   * @returns {object} Structured AI-Ready Specification JSON
   */
  static generateSpec(templateId, customConfig = {}) {
    const tmpl = TEMPLATES_CATALOG.find(t => t.id === templateId) || TEMPLATES_CATALOG[0];
    const cfg = { ...tmpl.defaultConfig, ...customConfig };

    const spec = {
      $schema: 'https://raw.githubusercontent.com/infoadmstudio2020-cyber/schemas/main/android-project-spec.v1.json',
      generator: 'RONi Android Library Generator v0.1.3.5',
      timestamp: new Date().toISOString(),
      format: 'AI_READY_PROJECT_SPECIFICATION',
      purpose: 'Complete structured specifications and requirements for autonomous AI project generation and updates.',
      
      ai_prompt_instruction: `You are an expert Android & Gradle Software Architect and Senior Systems Engineer.
Follow this structured JSON specification to generate or update the entire project repository.
STRICT RULES FOR CODE GENERATION:
1. Generate EVERY file listed under 'file_structure' with 100% complete, working code. NEVER use placeholders like '// TODO' or '/* add rest of code here */'.
2. Strictly maintain Gradle brace balance and valid DSL syntax.
3. Configure the exact Gradle (${cfg.gradleVersion || tmpl.recommendedBuildEnvironment.gradleVersion}), AGP (${cfg.agpVersion || tmpl.recommendedBuildEnvironment.agpVersion}), JDK (${cfg.jdkVersion || tmpl.recommendedBuildEnvironment.jdkVersion}), and compileSdk (${cfg.compileSdk || 35}).
4. Ensure all public classes, namespaces, and package statements align exactly with '${cfg.packageName}'.
5. For JitPack/Maven publishing, ensure POM metadata includes proper groupId ('${cfg.groupId || 'com.github.username'}') and artifactId ('${cfg.artifactId || 'library'}').`,

      project_metadata: {
        template_id: tmpl.id,
        template_name: tmpl.name,
        category: tmpl.category,
        project_name: cfg.projectName || 'AndroidProject',
        package_name: cfg.packageName || 'com.example.project',
        group_id: cfg.groupId || '<SPECIFY_IF_APPLICABLE: com.github.username>',
        artifact_id: cfg.artifactId || '<SPECIFY_IF_APPLICABLE: artifact-name>',
        version: cfg.version || '1.0.0',
        version_code: cfg.versionCode || 1,
        description: cfg.description || tmpl.description,
        author: cfg.author || '<USER_CUSTOMIZABLE: Author Name>',
        license: cfg.license || 'Apache-2.0',
        target_artifact: tmpl.targetArtifact,
        language: tmpl.language,
        build_dsl: tmpl.buildDsl
      },

      build_environment: {
        gradle_wrapper_version: cfg.gradleVersion || tmpl.recommendedBuildEnvironment.gradleVersion,
        agp_version: cfg.agpVersion || tmpl.recommendedBuildEnvironment.agpVersion,
        jdk_target_version: cfg.jdkVersion || tmpl.recommendedBuildEnvironment.jdkVersion,
        compile_sdk: Number(cfg.compileSdk || tmpl.recommendedBuildEnvironment.compileSdk || 35),
        min_sdk: Number(cfg.minSdk || tmpl.recommendedBuildEnvironment.minSdk || 21),
        target_sdk: Number(cfg.targetSdk || tmpl.recommendedBuildEnvironment.targetSdk || 35),
        compatibility_notes: `Tested and guaranteed compatible with Android Studio Ladybug/Koala, AGP ${tmpl.recommendedBuildEnvironment.agpVersion}, and Gradle ${tmpl.recommendedBuildEnvironment.gradleVersion}.`
      },

      publishing_and_distribution: {
        supported_platforms: ['JitPack', 'Maven Central', 'Local Maven Cache'],
        primary_repository: 'JitPack',
        jitpack_coordinate: `${cfg.groupId || 'com.github.username'}:${cfg.artifactId || 'library'}:${cfg.version || '1.0.0'}`,
        jitpack_configuration: {
          jdk: cfg.jdkVersion || '17',
          build_command: './gradlew clean build publishToMavenLocal'
        },
        pom_metadata: {
          name: cfg.projectName || 'Library',
          description: cfg.description || tmpl.description,
          url: `<SPECIFY_IF_APPLICABLE: https://github.com/${(cfg.groupId || 'username').replace(/^com\.github\./, '')}/${cfg.artifactId || 'repo'}>`,
          scm_url: `<SPECIFY_IF_APPLICABLE: scm:git:github.com/${(cfg.groupId || 'username').replace(/^com\.github\./, '')}/${cfg.artifactId || 'repo'}.git>`
        }
      },

      architecture: {
        pattern: tmpl.category === 'app' ? 'MVVM / Material 3' : 'Modular AAR Library with Clean API Boundary',
        modules: tmpl.id === 'android-java-library'
          ? [
              { name: `:${cfg.libraryModuleName || 'library'}`, type: 'com.android.library', output: 'AAR' },
              { name: `:${cfg.appModuleName || 'app'}`, type: 'com.android.application', output: 'Sample APK' }
            ]
          : tmpl.id === 'multi-module-android-sdk'
          ? [
              { name: `:${cfg.coreModuleName || 'core'}`, type: 'com.android.library', output: 'Core AAR' },
              { name: `:${cfg.uiModuleName || 'ui'}`, type: 'com.android.library', output: 'UI AAR' },
              { name: `:${cfg.appModuleName || 'app'}`, type: 'com.android.application', output: 'Demo APK' }
            ]
          : tmpl.id === 'pure-java-library'
          ? [{ name: ':root', type: 'java-library', output: 'JAR' }]
          : [{ name: `:${cfg.libraryModuleName || 'library'}`, type: 'com.android.library', output: 'AAR' }]
      },

      dependencies: tmpl.dependencies.map(dep => ({
        coordinate: dep.coordinate,
        scope: dep.scope,
        purpose: dep.purpose,
        required: dep.required,
        is_dynamic: false
      })),

      file_structure: tmpl.fileStructure.map(f => {
        // Adapt paths if custom module name is used
        let adjustedPath = f.path;
        if (cfg.libraryModuleName && cfg.libraryModuleName !== 'library') {
          adjustedPath = adjustedPath.replace(/^library\//, `${cfg.libraryModuleName}/`);
        }
        if (cfg.packageName) {
          const pkgFolder = cfg.packageName.replace(/\./g, '/');
          adjustedPath = adjustedPath.replace(/com\/example\/[a-z0-9_]+/, pkgFolder);
        }
        return {
          path: adjustedPath,
          purpose: f.purpose,
          required: f.required,
          status: 'ready_for_generation'
        };
      }),

      customizable_parameters: tmpl.customizableFields.map(cf => ({
        field_key: cf.key,
        current_value: cfg[cf.key] || cf.placeholder,
        placeholder: cf.placeholder,
        description: cf.description,
        is_user_customizable: true
      })),

      validation_and_safety_rules: [
        'Braces Balance Guarantee: All open curly braces "{" must strictly match closing curly braces "}".',
        'D8 / R8 Duplicate Class Safety: Never bundle standard runtime libraries (e.g. Kotlin stdlib) into raw class directories.',
        'Namespace Consistency: android.namespace in build.gradle must match the package identifier in AndroidManifest.xml.',
        'JVM Toolchain Alignment: Target Java version must be consistent between compileOptions, kotlinOptions, and CI workflows.',
        'JitPack Gradle Plugin Portal Resolution: repositories block must declare google(), mavenCentral(), and gradlePluginPortal().'
      ]
    };

    return spec;
  }

  /**
   * Generate dynamic, template-specific Required Information Schema.
   * Analyzes the template's actual structure, configuration, modules,
   * dependencies, build environment, and validation rules to produce
   * a comprehensive JSON Schema & Requirements Specification for AI or tools.
   *
   * @param {string} templateId - Template identifier
   * @param {object} customConfig - User-provided configurations
   * @returns {object} Structured Required Information Schema
   */
  static generateRequiredInformationSchema(templateId, customConfig = {}) {
    const tmpl = TEMPLATES_CATALOG.find(t => t.id === templateId) || TEMPLATES_CATALOG[0];
    const cfg = { ...tmpl.defaultConfig, ...customConfig };

    // Dynamically derive required/optional fields and properties per template
    let properties = {};
    let requiredFields = [];
    let optionalFields = [];
    let modulesSpec = [];
    let publishingSpec = {};
    let templateSpecificRules = [];

    if (tmpl.id === 'android-java-library') {
      requiredFields = [
        'projectName',
        'packageName',
        'groupId',
        'artifactId',
        'version',
        'compileSdk',
        'minSdk',
        'targetSdk',
        'libraryModuleName'
      ];
      optionalFields = ['appModuleName', 'description', 'author', 'license'];

      properties = {
        projectName: {
          type: 'string',
          title: 'Project Name',
          description: 'Root Android Studio project directory name',
          minLength: 1,
          maxLength: 100,
          pattern: '^[a-zA-Z0-9_-]+$',
          defaultValue: tmpl.defaultConfig.projectName,
          currentValue: cfg.projectName || tmpl.defaultConfig.projectName,
          isUserCustomizable: true
        },
        packageName: {
          type: 'string',
          title: 'Package Name / Namespace',
          description: 'Root Java/Kotlin package namespace and Android build namespace',
          format: 'java-package-name',
          pattern: '^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$',
          defaultValue: tmpl.defaultConfig.packageName,
          currentValue: cfg.packageName || tmpl.defaultConfig.packageName,
          isUserCustomizable: true
        },
        groupId: {
          type: 'string',
          title: 'Maven Group ID',
          description: 'Maven repository organization identifier (for JitPack, format: com.github.<username>)',
          pattern: '^[a-z0-9_.-]+$',
          defaultValue: tmpl.defaultConfig.groupId,
          currentValue: cfg.groupId || tmpl.defaultConfig.groupId,
          isUserCustomizable: true
        },
        artifactId: {
          type: 'string',
          title: 'Maven Artifact ID',
          description: 'Library repository artifact name for dependency resolution',
          pattern: '^[a-z0-9-]+$',
          defaultValue: tmpl.defaultConfig.artifactId,
          currentValue: cfg.artifactId || tmpl.defaultConfig.artifactId,
          isUserCustomizable: true
        },
        version: {
          type: 'string',
          title: 'Version',
          description: 'Semantic version for artifact distribution (SemVer)',
          pattern: '^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?$',
          defaultValue: tmpl.defaultConfig.version,
          currentValue: cfg.version || tmpl.defaultConfig.version,
          isUserCustomizable: true
        },
        libraryModuleName: {
          type: 'string',
          title: 'Library Module Name',
          description: 'Gradle subproject directory name generating the AAR binary',
          pattern: '^[a-z0-9_-]+$',
          defaultValue: tmpl.defaultConfig.libraryModuleName || 'library',
          currentValue: cfg.libraryModuleName || 'library',
          isUserCustomizable: true
        },
        appModuleName: {
          type: 'string',
          title: 'Companion App Module Name',
          description: 'Sample demonstration application directory name',
          pattern: '^[a-z0-9_-]+$',
          defaultValue: tmpl.defaultConfig.appModuleName || 'app',
          currentValue: cfg.appModuleName || 'app',
          isUserCustomizable: true
        },
        compileSdk: {
          type: 'integer',
          title: 'Compile SDK',
          description: 'Target Android API level against which the library compiles',
          minimum: 21,
          maximum: 36,
          defaultValue: tmpl.defaultConfig.compileSdk || 35,
          currentValue: Number(cfg.compileSdk || 35),
          isUserCustomizable: true
        },
        minSdk: {
          type: 'integer',
          title: 'Min SDK',
          description: 'Minimum Android API level supported by the library',
          minimum: 21,
          maximum: 36,
          defaultValue: tmpl.defaultConfig.minSdk || 21,
          currentValue: Number(cfg.minSdk || 21),
          isUserCustomizable: true
        },
        targetSdk: {
          type: 'integer',
          title: 'Target SDK',
          description: 'Target Android API level for the companion sample application',
          minimum: 21,
          maximum: 36,
          defaultValue: tmpl.defaultConfig.targetSdk || 35,
          currentValue: Number(cfg.targetSdk || 35),
          isUserCustomizable: true
        },
        description: {
          type: 'string',
          title: 'Project Description',
          description: 'Concise explanation of the library purpose and features',
          defaultValue: tmpl.defaultConfig.description,
          currentValue: cfg.description || tmpl.defaultConfig.description,
          isUserCustomizable: true
        },
        author: {
          type: 'string',
          title: 'Author / Maintainer',
          description: 'Individual or organization authoring the library',
          defaultValue: tmpl.defaultConfig.author,
          currentValue: cfg.author || tmpl.defaultConfig.author,
          isUserCustomizable: true
        },
        license: {
          type: 'string',
          title: 'License',
          description: 'Open source or proprietary software license',
          enum: ['Apache-2.0', 'MIT', 'GPL-3.0', 'BSD-3-Clause', 'Proprietary'],
          defaultValue: tmpl.defaultConfig.license || 'Apache-2.0',
          currentValue: cfg.license || 'Apache-2.0',
          isUserCustomizable: true
        }
      };

      modulesSpec = [
        {
          modulePath: `:${cfg.libraryModuleName || 'library'}`,
          moduleType: 'com.android.library',
          targetArtifact: 'AAR',
          appliedPlugins: ['com.android.library', 'maven-publish'],
          isPublished: true,
          description: 'Reusable Android Library module exposing public classes and APIs'
        },
        {
          modulePath: `:${cfg.appModuleName || 'app'}`,
          moduleType: 'com.android.application',
          targetArtifact: 'Sample APK',
          appliedPlugins: ['com.android.application'],
          isPublished: false,
          internalDependencies: [`project(':${cfg.libraryModuleName || 'library'}')`],
          description: 'Companion sample application testing and demonstrating library functionality'
        }
      ];

      publishingSpec = {
        enabled: true,
        primaryTarget: 'JitPack',
        secondaryTargets: ['Maven Central', 'Local Maven Repository (~/.m2)'],
        groupId: cfg.groupId || tmpl.defaultConfig.groupId,
        artifactId: cfg.artifactId || tmpl.defaultConfig.artifactId,
        version: cfg.version || tmpl.defaultConfig.version,
        coordinate: `${cfg.groupId || 'com.github.username'}:${cfg.artifactId || 'library'}:${cfg.version || '1.0.0'}`,
        jitpackBuildEnvironment: {
          jdkVersion: '17',
          installCommand: './gradlew clean build publishToMavenLocal'
        },
        mavenPublishingBlockRequired: true,
        companionAppExcludedFromPublishing: true
      };

      templateSpecificRules = [
        'Companion :app module must depend on :library module via `implementation project(":library")`.',
        'JitPack publish task must isolate :library module avoiding attempts to publish the executable :app.',
        'Consumer proguard file (consumer-rules.pro) must be configured in library build.gradle.'
      ];
    } else if (tmpl.id === 'android-app') {
      requiredFields = [
        'projectName',
        'packageName',
        'version',
        'versionCode',
        'compileSdk',
        'minSdk',
        'targetSdk'
      ];
      optionalFields = ['appModuleName', 'description', 'author', 'license'];

      properties = {
        projectName: {
          type: 'string',
          title: 'Project Name',
          description: 'Application project name and Android Studio directory',
          minLength: 1,
          maxLength: 100,
          pattern: '^[a-zA-Z0-9_-]+$',
          defaultValue: tmpl.defaultConfig.projectName,
          currentValue: cfg.projectName || tmpl.defaultConfig.projectName,
          isUserCustomizable: true
        },
        packageName: {
          type: 'string',
          title: 'Application ID / Namespace',
          description: 'Unique Android Application ID on Google Play and build namespace',
          format: 'java-package-name',
          pattern: '^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$',
          defaultValue: tmpl.defaultConfig.packageName,
          currentValue: cfg.packageName || tmpl.defaultConfig.packageName,
          isUserCustomizable: true
        },
        version: {
          type: 'string',
          title: 'Version Name',
          description: 'User-facing version string displayed in app settings (versionName)',
          pattern: '^\\d+\\.\\d+\\.\\d+',
          defaultValue: tmpl.defaultConfig.version,
          currentValue: cfg.version || tmpl.defaultConfig.version,
          isUserCustomizable: true
        },
        versionCode: {
          type: 'integer',
          title: 'Version Code',
          description: 'Positive integer build number used by Google Play Store for updates',
          minimum: 1,
          defaultValue: tmpl.defaultConfig.versionCode || 1,
          currentValue: Number(cfg.versionCode || 1),
          isUserCustomizable: true
        },
        appModuleName: {
          type: 'string',
          title: 'Application Module Name',
          description: 'Directory name for the application module',
          defaultValue: tmpl.defaultConfig.appModuleName || 'app',
          currentValue: cfg.appModuleName || 'app',
          isUserCustomizable: true
        },
        compileSdk: {
          type: 'integer',
          title: 'Compile SDK',
          description: 'Target compilation Android API level',
          minimum: 24,
          maximum: 36,
          defaultValue: tmpl.defaultConfig.compileSdk || 35,
          currentValue: Number(cfg.compileSdk || 35),
          isUserCustomizable: true
        },
        minSdk: {
          type: 'integer',
          title: 'Min SDK',
          description: 'Minimum supported Android API level for devices running this app',
          minimum: 21,
          maximum: 36,
          defaultValue: tmpl.defaultConfig.minSdk || 24,
          currentValue: Number(cfg.minSdk || 24),
          isUserCustomizable: true
        },
        targetSdk: {
          type: 'integer',
          title: 'Target SDK',
          description: 'Target Android API level governing runtime privacy and permissions behavior',
          minimum: 24,
          maximum: 36,
          defaultValue: tmpl.defaultConfig.targetSdk || 35,
          currentValue: Number(cfg.targetSdk || 35),
          isUserCustomizable: true
        },
        description: {
          type: 'string',
          title: 'Description',
          description: 'Overview of the application purpose',
          defaultValue: tmpl.defaultConfig.description,
          currentValue: cfg.description || tmpl.defaultConfig.description,
          isUserCustomizable: true
        },
        author: {
          type: 'string',
          title: 'Developer / Organization',
          description: 'App developer or publisher entity',
          defaultValue: tmpl.defaultConfig.author,
          currentValue: cfg.author || tmpl.defaultConfig.author,
          isUserCustomizable: true
        },
        license: {
          type: 'string',
          title: 'License',
          description: 'Application license or distribution terms',
          defaultValue: tmpl.defaultConfig.license || 'Proprietary / MIT',
          currentValue: cfg.license || 'Proprietary / MIT',
          isUserCustomizable: true
        }
      };

      modulesSpec = [
        {
          modulePath: `:${cfg.appModuleName || 'app'}`,
          moduleType: 'com.android.application',
          targetArtifact: 'APK + AAB',
          appliedPlugins: ['com.android.application'],
          isPublished: false,
          description: 'Primary Android executable application module generating release binaries'
        }
      ];

      publishingSpec = {
        enabled: false,
        primaryTarget: 'Google Play / Sideload Distribution',
        targetArtifacts: ['app-release.apk', 'app-release.aab'],
        jitpackPublishingRequired: false,
        signingConfigRequired: 'Required for production release builds',
        proguardConfig: 'proguard-rules.pro'
      };

      templateSpecificRules = [
        'Must configure applicationId, versionCode, and versionName in defaultConfig.',
        'ViewBinding or DataBinding can be enabled without breaking build integrity.',
        'Requires launcher activity declaration with android.intent.action.MAIN in AndroidManifest.xml.'
      ];
    } else if (tmpl.id === 'multi-module-android-sdk') {
      requiredFields = [
        'projectName',
        'packageName',
        'groupId',
        'coreModuleName',
        'uiModuleName',
        'version',
        'compileSdk',
        'minSdk',
        'targetSdk'
      ];
      optionalFields = ['appModuleName', 'description', 'author', 'license'];

      properties = {
        projectName: {
          type: 'string',
          title: 'Project Name',
          description: 'Enterprise SDK project root directory name',
          minLength: 1,
          maxLength: 100,
          pattern: '^[a-zA-Z0-9_-]+$',
          defaultValue: tmpl.defaultConfig.projectName,
          currentValue: cfg.projectName || tmpl.defaultConfig.projectName,
          isUserCustomizable: true
        },
        packageName: {
          type: 'string',
          title: 'Base Package Name',
          description: 'Common root namespace for SDK modules',
          format: 'java-package-name',
          pattern: '^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$',
          defaultValue: tmpl.defaultConfig.packageName,
          currentValue: cfg.packageName || tmpl.defaultConfig.packageName,
          isUserCustomizable: true
        },
        groupId: {
          type: 'string',
          title: 'Maven Group ID',
          description: 'Maven group for multi-artifact SDK publishing (e.g. com.github.<org>)',
          pattern: '^[a-z0-9_.-]+$',
          defaultValue: tmpl.defaultConfig.groupId,
          currentValue: cfg.groupId || tmpl.defaultConfig.groupId,
          isUserCustomizable: true
        },
        coreModuleName: {
          type: 'string',
          title: 'Core Module Name',
          description: 'Directory name for the headless core logic library module',
          defaultValue: tmpl.defaultConfig.coreModuleName || 'core',
          currentValue: cfg.coreModuleName || 'core',
          isUserCustomizable: true
        },
        uiModuleName: {
          type: 'string',
          title: 'UI Module Name',
          description: 'Directory name for the UI widgets library module',
          defaultValue: tmpl.defaultConfig.uiModuleName || 'ui',
          currentValue: cfg.uiModuleName || 'ui',
          isUserCustomizable: true
        },
        appModuleName: {
          type: 'string',
          title: 'Consumer App Module Name',
          description: 'Sample testing app directory name',
          defaultValue: tmpl.defaultConfig.appModuleName || 'app',
          currentValue: cfg.appModuleName || 'app',
          isUserCustomizable: true
        },
        version: {
          type: 'string',
          title: 'SDK Version',
          description: 'Synchronized semantic version across all published SDK modules',
          pattern: '^\\d+\\.\\d+\\.\\d+',
          defaultValue: tmpl.defaultConfig.version,
          currentValue: cfg.version || tmpl.defaultConfig.version,
          isUserCustomizable: true
        },
        compileSdk: {
          type: 'integer',
          title: 'Compile SDK',
          description: 'Unified compile SDK for all SDK modules',
          minimum: 21,
          maximum: 36,
          defaultValue: tmpl.defaultConfig.compileSdk || 35,
          currentValue: Number(cfg.compileSdk || 35),
          isUserCustomizable: true
        },
        minSdk: {
          type: 'integer',
          title: 'Min SDK',
          description: 'Unified minimum SDK level across all modules',
          minimum: 21,
          maximum: 36,
          defaultValue: tmpl.defaultConfig.minSdk || 21,
          currentValue: Number(cfg.minSdk || 21),
          isUserCustomizable: true
        },
        targetSdk: {
          type: 'integer',
          title: 'Target SDK',
          description: 'Target SDK for consumer sample application',
          minimum: 21,
          maximum: 36,
          defaultValue: tmpl.defaultConfig.targetSdk || 35,
          currentValue: Number(cfg.targetSdk || 35),
          isUserCustomizable: true
        },
        description: {
          type: 'string',
          title: 'Description',
          description: 'Enterprise SDK architecture explanation',
          defaultValue: tmpl.defaultConfig.description,
          currentValue: cfg.description || tmpl.defaultConfig.description,
          isUserCustomizable: true
        },
        author: {
          type: 'string',
          title: 'Author / Organization',
          description: 'Organization responsible for the SDK',
          defaultValue: tmpl.defaultConfig.author,
          currentValue: cfg.author || tmpl.defaultConfig.author,
          isUserCustomizable: true
        },
        license: {
          type: 'string',
          title: 'License',
          description: 'SDK distribution license',
          defaultValue: tmpl.defaultConfig.license || 'Apache-2.0',
          currentValue: cfg.license || 'Apache-2.0',
          isUserCustomizable: true
        }
      };

      modulesSpec = [
        {
          modulePath: `:${cfg.coreModuleName || 'core'}`,
          moduleType: 'com.android.library',
          targetArtifact: 'Core AAR',
          appliedPlugins: ['com.android.library', 'maven-publish'],
          isPublished: true,
          description: 'Headless business logic, data models, network interfaces, and state managers'
        },
        {
          modulePath: `:${cfg.uiModuleName || 'ui'}`,
          moduleType: 'com.android.library',
          targetArtifact: 'UI AAR',
          appliedPlugins: ['com.android.library', 'maven-publish'],
          isPublished: true,
          internalDependencies: [`project(':${cfg.coreModuleName || 'core'}')`],
          description: 'Decoupled UI components, dialogs, widgets, and styles consuming :core module'
        },
        {
          modulePath: `:${cfg.appModuleName || 'app'}`,
          moduleType: 'com.android.application',
          targetArtifact: 'Demo APK',
          appliedPlugins: ['com.android.application'],
          isPublished: false,
          internalDependencies: [`project(':${cfg.uiModuleName || 'ui'}')`],
          description: 'End-to-end integration and demonstration client'
        }
      ];

      publishingSpec = {
        enabled: true,
        primaryTarget: 'JitPack Multi-Artifact Publishing',
        groupId: cfg.groupId || tmpl.defaultConfig.groupId,
        version: cfg.version || tmpl.defaultConfig.version,
        publishedArtifacts: [
          {
            module: `:${cfg.coreModuleName || 'core'}`,
            artifactId: `${cfg.projectName ? cfg.projectName.toLowerCase() : 'sdk'}-core`,
            coordinate: `${cfg.groupId || 'com.github.username'}:${cfg.projectName ? cfg.projectName.toLowerCase() : 'sdk'}-core:${cfg.version || '1.0.0'}`
          },
          {
            module: `:${cfg.uiModuleName || 'ui'}`,
            artifactId: `${cfg.projectName ? cfg.projectName.toLowerCase() : 'sdk'}-ui`,
            coordinate: `${cfg.groupId || 'com.github.username'}:${cfg.projectName ? cfg.projectName.toLowerCase() : 'sdk'}-ui:${cfg.version || '1.0.0'}`
          }
        ],
        jitpackBuildEnvironment: {
          jdkVersion: '17',
          installCommand: './gradlew clean build publishToMavenLocal'
        }
      };

      templateSpecificRules = [
        ':core module must NOT depend on :ui module (strict unidirectional dependency flow).',
        ':ui module depends on :core via `api project(":core")` or `implementation project(":core")`.',
        'Both :core and :ui configure maven-publish with separate publication blocks.'
      ];
    } else if (tmpl.id === 'pure-java-library') {
      requiredFields = [
        'projectName',
        'packageName',
        'groupId',
        'artifactId',
        'version',
        'javaVersion'
      ];
      optionalFields = ['description', 'author', 'license'];

      properties = {
        projectName: {
          type: 'string',
          title: 'Project Name',
          description: 'Universal JVM library project directory name',
          minLength: 1,
          maxLength: 100,
          pattern: '^[a-zA-Z0-9_-]+$',
          defaultValue: tmpl.defaultConfig.projectName,
          currentValue: cfg.projectName || tmpl.defaultConfig.projectName,
          isUserCustomizable: true
        },
        packageName: {
          type: 'string',
          title: 'Java Package Name',
          description: 'Base Java package identifier',
          format: 'java-package-name',
          pattern: '^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$',
          defaultValue: tmpl.defaultConfig.packageName,
          currentValue: cfg.packageName || tmpl.defaultConfig.packageName,
          isUserCustomizable: true
        },
        groupId: {
          type: 'string',
          title: 'Maven Group ID',
          description: 'Maven group coordinate (e.g. com.github.<username>)',
          pattern: '^[a-z0-9_.-]+$',
          defaultValue: tmpl.defaultConfig.groupId,
          currentValue: cfg.groupId || tmpl.defaultConfig.groupId,
          isUserCustomizable: true
        },
        artifactId: {
          type: 'string',
          title: 'Maven Artifact ID',
          description: 'Artifact identifier for the generated JAR binary',
          pattern: '^[a-z0-9-]+$',
          defaultValue: tmpl.defaultConfig.artifactId,
          currentValue: cfg.artifactId || tmpl.defaultConfig.artifactId,
          isUserCustomizable: true
        },
        version: {
          type: 'string',
          title: 'Version',
          description: 'Semantic version number',
          pattern: '^\\d+\\.\\d+\\.\\d+',
          defaultValue: tmpl.defaultConfig.version,
          currentValue: cfg.version || tmpl.defaultConfig.version,
          isUserCustomizable: true
        },
        javaVersion: {
          type: 'string',
          title: 'Target Java Version',
          description: 'Java Standard Edition bytecode compatibility level',
          enum: ['17', '21'],
          defaultValue: tmpl.defaultConfig.javaVersion || '17',
          currentValue: cfg.javaVersion || '17',
          isUserCustomizable: true
        },
        description: {
          type: 'string',
          title: 'Description',
          description: 'Summary of the JVM utility or library',
          defaultValue: tmpl.defaultConfig.description,
          currentValue: cfg.description || tmpl.defaultConfig.description,
          isUserCustomizable: true
        },
        author: {
          type: 'string',
          title: 'Author',
          description: 'Library author / creator',
          defaultValue: tmpl.defaultConfig.author,
          currentValue: cfg.author || tmpl.defaultConfig.author,
          isUserCustomizable: true
        },
        license: {
          type: 'string',
          title: 'License',
          description: 'Software license',
          defaultValue: tmpl.defaultConfig.license || 'MIT',
          currentValue: cfg.license || 'MIT',
          isUserCustomizable: true
        }
      };

      modulesSpec = [
        {
          modulePath: ':root',
          moduleType: 'java-library',
          targetArtifact: 'JAR',
          appliedPlugins: ['java-library', 'maven-publish'],
          isPublished: true,
          description: 'Pure JVM library generating standard JAR with sourcesJar and javadocJar'
        }
      ];

      publishingSpec = {
        enabled: true,
        primaryTarget: 'JitPack / Maven Central',
        coordinate: `${cfg.groupId || 'com.github.username'}:${cfg.artifactId || 'common-utils'}:${cfg.version || '1.0.0'}`,
        jitpackBuildEnvironment: {
          jdkVersion: cfg.javaVersion || '17',
          installCommand: './gradlew clean build publishToMavenLocal'
        },
        artifacts: ['JAR binary', 'sourcesJar', 'javadocJar']
      };

      templateSpecificRules = [
        'Android SDK tools (AGP, AndroidManifest, compileSdk) are NOT applicable for this Pure JVM template.',
        'Target Java compatibility must match sourceCompatibility = JavaVersion.VERSION_17 and targetCompatibility.',
        'Uses JUnit 5 (org.junit.jupiter) for modern unit testing.'
      ];
    } else {
      // android-library-standalone and generic fallback
      requiredFields = [
        'projectName',
        'packageName',
        'groupId',
        'artifactId',
        'version',
        'compileSdk',
        'minSdk',
        'targetSdk',
        'libraryModuleName'
      ];
      optionalFields = ['description', 'author', 'license'];

      properties = {
        projectName: {
          type: 'string',
          title: 'Project Name',
          description: 'Minimalist library project root directory name',
          minLength: 1,
          maxLength: 100,
          pattern: '^[a-zA-Z0-9_-]+$',
          defaultValue: tmpl.defaultConfig.projectName,
          currentValue: cfg.projectName || tmpl.defaultConfig.projectName,
          isUserCustomizable: true
        },
        packageName: {
          type: 'string',
          title: 'Package Name / Namespace',
          description: 'Base Android namespace and Java package',
          format: 'java-package-name',
          pattern: '^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$',
          defaultValue: tmpl.defaultConfig.packageName,
          currentValue: cfg.packageName || tmpl.defaultConfig.packageName,
          isUserCustomizable: true
        },
        groupId: {
          type: 'string',
          title: 'Maven Group ID',
          description: 'Maven group coordinate for JitPack publishing',
          pattern: '^[a-z0-9_.-]+$',
          defaultValue: tmpl.defaultConfig.groupId,
          currentValue: cfg.groupId || tmpl.defaultConfig.groupId,
          isUserCustomizable: true
        },
        artifactId: {
          type: 'string',
          title: 'Maven Artifact ID',
          description: 'Artifact identifier for JitPack resolution',
          pattern: '^[a-z0-9-]+$',
          defaultValue: tmpl.defaultConfig.artifactId,
          currentValue: cfg.artifactId || tmpl.defaultConfig.artifactId,
          isUserCustomizable: true
        },
        version: {
          type: 'string',
          title: 'Version',
          description: 'Semantic version number',
          pattern: '^\\d+\\.\\d+\\.\\d+',
          defaultValue: tmpl.defaultConfig.version,
          currentValue: cfg.version || tmpl.defaultConfig.version,
          isUserCustomizable: true
        },
        libraryModuleName: {
          type: 'string',
          title: 'Library Module Name',
          description: 'Subproject module directory name',
          defaultValue: tmpl.defaultConfig.libraryModuleName || 'library',
          currentValue: cfg.libraryModuleName || 'library',
          isUserCustomizable: true
        },
        compileSdk: {
          type: 'integer',
          title: 'Compile SDK',
          description: 'Target compile Android API level',
          minimum: 21,
          maximum: 36,
          defaultValue: tmpl.defaultConfig.compileSdk || 35,
          currentValue: Number(cfg.compileSdk || 35),
          isUserCustomizable: true
        },
        minSdk: {
          type: 'integer',
          title: 'Min SDK',
          description: 'Minimum supported Android API level',
          minimum: 21,
          maximum: 36,
          defaultValue: tmpl.defaultConfig.minSdk || 21,
          currentValue: Number(cfg.minSdk || 21),
          isUserCustomizable: true
        },
        targetSdk: {
          type: 'integer',
          title: 'Target SDK',
          description: 'Target API level',
          minimum: 21,
          maximum: 36,
          defaultValue: tmpl.defaultConfig.targetSdk || 35,
          currentValue: Number(cfg.targetSdk || 35),
          isUserCustomizable: true
        },
        description: {
          type: 'string',
          title: 'Description',
          description: 'Library summary',
          defaultValue: tmpl.defaultConfig.description,
          currentValue: cfg.description || tmpl.defaultConfig.description,
          isUserCustomizable: true
        },
        author: {
          type: 'string',
          title: 'Author',
          description: 'Library maintainer',
          defaultValue: tmpl.defaultConfig.author,
          currentValue: cfg.author || tmpl.defaultConfig.author,
          isUserCustomizable: true
        },
        license: {
          type: 'string',
          title: 'License',
          description: 'Software license',
          defaultValue: tmpl.defaultConfig.license || 'Apache-2.0',
          currentValue: cfg.license || 'Apache-2.0',
          isUserCustomizable: true
        }
      };

      modulesSpec = [
        {
          modulePath: `:${cfg.libraryModuleName || 'library'}`,
          moduleType: 'com.android.library',
          targetArtifact: 'AAR',
          appliedPlugins: ['com.android.library', 'maven-publish'],
          isPublished: true,
          description: 'Single-module lightweight Android Library producing AAR artifact'
        }
      ];

      publishingSpec = {
        enabled: true,
        primaryTarget: 'JitPack',
        coordinate: `${cfg.groupId || 'com.github.username'}:${cfg.artifactId || 'minimal-lib'}:${cfg.version || '1.0.0'}`,
        jitpackBuildEnvironment: {
          jdkVersion: '17',
          installCommand: './gradlew clean build publishToMavenLocal'
        }
      };

      templateSpecificRules = [
        'Single module only: no companion app is generated.',
        'Consumer rules are embedded directly inside the resulting AAR.'
      ];
    }

    // Assemble the complete Required Information Schema
    const schema = {
      $schema: 'https://json-schema.org/draft-07/schema#',
      schema_version: '1.0.0',
      title: `RONi Android Project Required Information Schema: ${tmpl.name}`,
      generator: 'RONi Android Library Generator v0.1.3.5',
      generated_at: new Date().toISOString(),
      template_id: tmpl.id,
      template_name: tmpl.name,
      template_category: tmpl.category,
      project_type: tmpl.category === 'library'
        ? 'android_library'
        : tmpl.category === 'app'
        ? 'android_application'
        : tmpl.category === 'sdk'
        ? 'multi_module_android_sdk'
        : 'java_library',
      language: tmpl.language,
      build_dsl: tmpl.buildDsl,
      target_artifact: tmpl.targetArtifact,
      description: tmpl.description,

      ai_usage_guide: {
        role: 'Expert Android & Gradle Architect',
        instruction: `This schema specifies the precise requirements, input parameters, and validation rules needed to generate a valid, production-ready ${tmpl.name} project. When an AI receives this specification:
1. Extract and validate user values according to the 'required_information' schema properties and validation constraints.
2. If any optional field is not provided, use the stated 'defaultValue' or derive it sensibly.
3. Generate all modules and files matching the 'modules_specification' and 'dependencies_specification'.
4. Strictly enforce 'validation_rules_summary' ensuring balanced Gradle braces, consistent package naming, and correct SDK targeting.`,
        unknown_fields_policy: 'Any parameter not recognized by this schema should be flagged or ignored to prevent corrupting build scripts.'
      },

      required_information: {
        type: 'object',
        description: `Input configuration schema required to scaffold the ${tmpl.name} project.`,
        required: requiredFields,
        optional: optionalFields,
        properties: properties
      },

      build_environment_specification: {
        gradle_wrapper_version: tmpl.recommendedBuildEnvironment.gradleVersion,
        agp_version: tmpl.recommendedBuildEnvironment.agpVersion,
        jdk_target_version: tmpl.recommendedBuildEnvironment.jdkVersion,
        compile_sdk: tmpl.recommendedBuildEnvironment.compileSdk,
        min_sdk: tmpl.recommendedBuildEnvironment.minSdk,
        target_sdk: tmpl.recommendedBuildEnvironment.targetSdk,
        build_tools_compatibility: 'Android Studio Ladybug / Koala / Iguana, Gradle 8.7 - 8.13, AGP 8.3.2 - 8.10.1'
      },

      modules_specification: modulesSpec,

      dependencies_specification: tmpl.dependencies.map(d => ({
        coordinate: d.coordinate,
        scope: d.scope,
        purpose: d.purpose,
        required: d.required
      })),

      publishing_specification: publishingSpec,

      file_structure_summary: tmpl.fileStructure.map(f => ({
        path: f.path,
        purpose: f.purpose,
        required: f.required
      })),

      validation_rules_summary: [
        'Braces Balance Guarantee: All open curly braces "{" must strictly match closing curly braces "}".',
        'Package Namespace Match: Package/Namespace declarations in AndroidManifest.xml and build.gradle must be identical.',
        'SDK Boundary Constraint: minSdk <= targetSdk <= compileSdk.',
        ...templateSpecificRules
      ]
    };

    return schema;
  }

  /**
   * Download a JSON specification as a physical .json file.
   * Utilizes WebViewBridge for safe Android WebView / Browser download handling.
   *
   * @param {object} specObject
   * @param {string} filename
   */
  static downloadJson(specObject, filename = 'project-ai-spec.json') {
    const jsonStr = typeof specObject === 'string' ? specObject : JSON.stringify(specObject, null, 2);
    const fname = filename.endsWith('.json') ? filename : `${filename}.json`;
    WebViewBridge.downloadFile(fname, jsonStr, 'application/json;charset=utf-8');
  }

  /**
   * Copy JSON string to system clipboard with Android WebView and secure context fallback.
   *
   * @param {object|string} specObject
   * @returns {Promise<boolean>}
   */
  static async copyToClipboard(specObject) {
    const jsonStr = typeof specObject === 'string' ? specObject : JSON.stringify(specObject, null, 2);
    return ClipboardUtil.copyText(jsonStr);
  }
}
