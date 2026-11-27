/**
 * GradleAstHelper.js — Robust, Brace-Aware & AST/Context-Aware Gradle Script Parser & Manipulator.
 *
 * Provides syntax-safe, brace-depth tracked parsing and editing of Groovy (.gradle)
 * and Kotlin DSL (.gradle.kts) scripts. Eliminates regex truncation, unclosed closures,
 * and unmatched closing brace errors.
 */

export class GradleAstHelper {
  /**
   * Scan content while tracking string, comment, and brace states.
   * Calls onChar(char, index, state) for each character.
   * @param {string} content
   * @param {(char: string, index: number, state: { inSingle: boolean, inDouble: boolean, inTripleSingle: boolean, inTripleDouble: boolean, inLineComment: boolean, inBlockComment: boolean, depth: number }) => boolean|void} onChar
   */
  static scan(content, onChar) {
    const len = content.length;
    let depth = 0;
    let inSingle = false;
    let inDouble = false;
    let inTripleSingle = false;
    let inTripleDouble = false;
    let inLineComment = false;
    let inBlockComment = false;

    for (let i = 0; i < len; i++) {
      const ch = content[i];
      const next1 = content[i + 1] || '';
      const next2 = content[i + 2] || '';
      const prev = i > 0 ? content[i - 1] : '';

      // Block comments
      if (inBlockComment) {
        if (ch === '*' && next1 === '/') {
          inBlockComment = false;
          i++; // skip /
        }
        continue;
      }

      // Line comments
      if (inLineComment) {
        if (ch === '\n') {
          inLineComment = false;
        }
        continue;
      }

      // Triple-quoted strings (Groovy / Kotlin)
      if (inTripleDouble) {
        if (ch === '"' && next1 === '"' && next2 === '"' && prev !== '\\') {
          inTripleDouble = false;
          i += 2;
        }
        continue;
      }
      if (inTripleSingle) {
        if (ch === "'" && next1 === "'" && next2 === "'" && prev !== '\\') {
          inTripleSingle = false;
          i += 2;
        }
        continue;
      }

      // Start of comments or strings when outside strings
      if (!inSingle && !inDouble && !inTripleSingle && !inTripleDouble) {
        if (ch === '/' && next1 === '/') {
          inLineComment = true;
          i++;
          continue;
        }
        if (ch === '/' && next1 === '*') {
          inBlockComment = true;
          i++;
          continue;
        }
        if (ch === '"' && next1 === '"' && next2 === '"') {
          inTripleDouble = true;
          i += 2;
          continue;
        }
        if (ch === "'" && next1 === "'" && next2 === "'") {
          inTripleSingle = true;
          i += 2;
          continue;
        }
        if (ch === '"' && prev !== '\\') {
          inDouble = true;
          continue;
        }
        if (ch === "'" && prev !== '\\') {
          inSingle = true;
          continue;
        }
      } else {
        // Ending of single or double quotes
        if (inSingle && ch === "'" && prev !== '\\') {
          inSingle = false;
          continue;
        }
        if (inDouble && ch === '"' && prev !== '\\') {
          inDouble = false;
          continue;
        }
      }

      // Skip character processing if inside string
      if (inSingle || inDouble || inTripleSingle || inTripleDouble) {
        continue;
      }

      // Track braces outside strings and comments
      if (ch === '{') depth++;
      else if (ch === '}') depth--;

      const state = {
        inSingle,
        inDouble,
        inTripleSingle,
        inTripleDouble,
        inLineComment,
        inBlockComment,
        depth
      };

      const shouldStop = onChar(ch, i, state);
      if (shouldStop === true) break;
    }
  }

  /**
   * Validate brace balance outside comments and strings.
   * @param {string} content
   * @returns {{ balanced: boolean, openCount: number, closeCount: number, diff: number, errors: string[] }}
   */
  static validateBraces(content = '') {
    let openCount = 0;
    let closeCount = 0;
    const errors = [];

    this.scan(content, (ch, index, state) => {
      if (ch === '{') {
        openCount++;
      } else if (ch === '}') {
        closeCount++;
        if (state.depth < 0) {
          const lines = content.slice(0, index).split('\n');
          errors.push(`Unmatched closing brace '}' at line ${lines.length}, column ${lines[lines.length - 1].length + 1}`);
        }
      }
    });

    if (openCount > closeCount) {
      errors.push(`${openCount - closeCount} unclosed opening brace(s) '{'`);
    } else if (closeCount > openCount) {
      errors.push(`${closeCount - openCount} unmatched closing brace(s) '}'`);
    }

    return {
      balanced: openCount === closeCount && errors.length === 0,
      openCount,
      closeCount,
      diff: openCount - closeCount,
      errors
    };
  }

  /**
   * Find a named block at any level or top level.
   * Handles e.g. `dependencies {`, `android {`, `afterEvaluate {`, `publishing {`, `repositories {`.
   * @param {string} content
   * @param {string} blockName
   * @param {number} [searchStartIndex=0]
   * @returns {{ start: number, braceStart: number, braceEnd: number, end: number, body: number, raw: string, bodyText: string } | null}
   */
  static findBlock(content = '', blockName = '', searchStartIndex = 0) {
    if (!content || !blockName) return null;

    // Pattern to match: word boundary, blockName, optional whitespace/parens/args, opening brace
    const escaped = blockName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(`\\b${escaped}\\b[^{]*\\{`, 'g');
    regex.lastIndex = searchStartIndex;

    let match;
    while ((match = regex.exec(content)) !== null) {
      const blockStart = match.index;
      // Opening brace is at index of last '{' in matched text
      const braceStart = blockStart + match[0].lastIndexOf('{');

      // Now track depth starting from braceStart
      let depth = 0;
      let inSingle = false;
      let inDouble = false;
      let inTripleSingle = false;
      let inTripleDouble = false;
      let inLineComment = false;
      let inBlockComment = false;
      let braceEnd = -1;

      for (let i = braceStart; i < content.length; i++) {
        const ch = content[i];
        const next1 = content[i + 1] || '';
        const next2 = content[i + 2] || '';
        const prev = i > 0 ? content[i - 1] : '';

        if (inBlockComment) {
          if (ch === '*' && next1 === '/') { inBlockComment = false; i++; }
          continue;
        }
        if (inLineComment) {
          if (ch === '\n') inLineComment = false;
          continue;
        }
        if (inTripleDouble) {
          if (ch === '"' && next1 === '"' && next2 === '"' && prev !== '\\') { inTripleDouble = false; i += 2; }
          continue;
        }
        if (inTripleSingle) {
          if (ch === "'" && next1 === "'" && next2 === "'" && prev !== '\\') { inTripleSingle = false; i += 2; }
          continue;
        }

        if (!inSingle && !inDouble && !inTripleSingle && !inTripleDouble) {
          if (ch === '/' && next1 === '/') { inLineComment = true; i++; continue; }
          if (ch === '/' && next1 === '*') { inBlockComment = true; i++; continue; }
          if (ch === '"' && next1 === '"' && next2 === '"') { inTripleDouble = true; i += 2; continue; }
          if (ch === "'" && next1 === "'" && next2 === "'") { inTripleSingle = true; i += 2; continue; }
          if (ch === '"' && prev !== '\\') { inDouble = true; continue; }
          if (ch === "'" && prev !== '\\') { inSingle = true; continue; }
        } else {
          if (inSingle && ch === "'" && prev !== '\\') { inSingle = false; continue; }
          if (inDouble && ch === '"' && prev !== '\\') { inDouble = false; continue; }
        }

        if (inSingle || inDouble || inTripleSingle || inTripleDouble) continue;

        if (ch === '{') {
          depth++;
        } else if (ch === '}') {
          depth--;
          if (depth === 0) {
            braceEnd = i;
            break;
          }
        }
      }

      if (braceEnd !== -1) {
        return {
          start: blockStart,
          braceStart,
          braceEnd,
          end: braceEnd + 1,
          bodyText: content.slice(braceStart + 1, braceEnd),
          raw: content.slice(blockStart, braceEnd + 1)
        };
      }
    }

    return null;
  }

  /**
   * Find all occurrences of a named block.
   * @param {string} content
   * @param {string} blockName
   * @returns {Array<{ start: number, braceStart: number, braceEnd: number, end: number, raw: string, bodyText: string }>}
   */
  static findAllBlocks(content = '', blockName = '') {
    const results = [];
    let searchIdx = 0;
    while (searchIdx < content.length) {
      const block = this.findBlock(content, blockName, searchIdx);
      if (!block) break;
      results.push(block);
      searchIdx = block.end;
    }
    return results;
  }

  /**
   * Find a nested child block inside a parent block.
   * @param {string} content
   * @param {string} parentBlockName
   * @param {string} childBlockName
   * @returns {{ parent: object, child: object } | null}
   */
  static findNestedBlock(content = '', parentBlockName = '', childBlockName = '') {
    const parent = this.findBlock(content, parentBlockName);
    if (!parent) return null;

    const child = this.findBlock(content, childBlockName, parent.braceStart);
    if (!child || child.braceEnd > parent.braceEnd) return null;

    return { parent, child };
  }

  /**
   * Remove a block cleanly, optionally removing any immediate banner comments preceding it.
   * @param {string} content
   * @param {string} blockName
   * @param {{ includeBannerComments?: boolean }} [options]
   * @returns {{ content: string, removed: boolean }}
   */
  static removeBlock(content = '', blockName = '', options = {}) {
    const block = this.findBlock(content, blockName);
    if (!block) return { content, removed: false };

    let removeStart = block.start;

    // Remove banner comments right above if requested
    if (options.includeBannerComments !== false) {
      const before = content.slice(0, block.start);
      // Look back for comments like // ==================== \n // MAVEN PUBLISH ... \n
      const lines = before.split('\n');
      let commentLineCount = 0;
      for (let i = lines.length - 1; i >= 0; i--) {
        const trimmed = lines[i].trim();
        if (!trimmed) {
          if (commentLineCount > 0) break; // empty line after comment
          continue;
        }
        if (trimmed.startsWith('//') || trimmed.startsWith('/*') || trimmed.startsWith('*') || trimmed.endsWith('*/')) {
          commentLineCount++;
        } else {
          break;
        }
      }

      if (commentLineCount > 0) {
        // Calculate index of first comment line
        const keptLines = lines.slice(0, lines.length - commentLineCount);
        removeStart = keptLines.join('\n').length;
      }
    }

    let removeEnd = block.end;
    // Consume trailing newline
    if (content[removeEnd] === '\r') removeEnd++;
    if (content[removeEnd] === '\n') removeEnd++;

    const patched = (content.slice(0, removeStart).trimEnd() + '\n\n' + content.slice(removeEnd).trimStart()).trim();
    return { content: patched, removed: true };
  }

  /**
   * Parse declared dependencies from dependencies body content.
   * @param {string} bodyText
   * @returns {Array<{ line: string, configuration: string, coordinate: string, group: string, name: string, version: string, isProject: boolean, isKts: boolean }>}
   */
  static parseDependencies(bodyText = '') {
    const results = [];
    const lines = bodyText.split('\n');

    for (const rawLine of lines) {
      const line = rawLine.trim();
      if (!line || line.startsWith('//') || line.startsWith('/*') || line.startsWith('*')) continue;

      // Match Groovy or Kotlin dependency declaration
      // e.g.: implementation 'com.google.code.gson:gson:2.10.1'
      //       api("com.squareup.okhttp3:okhttp:4.12.0")
      //       implementation project(':core')
      //       testImplementation("junit:junit:4.13.2")
      const depMatch = line.match(
        /^(implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|androidTestImplementation)\s*(?:\(\s*|\s+)(?:['"]([^'"]+)['"]|project\s*\(\s*['"]([^'"]+)['"]\s*\)|platform\s*\(\s*['"]([^'"]+)['"]\s*\))/
      );

      if (depMatch) {
        const configuration = depMatch[1];
        const coord = depMatch[2] || depMatch[3] || depMatch[4] || '';
        const isProject = !!depMatch[3];
        const isKts = line.includes('(');

        let group = '';
        let name = '';
        let version = '';

        if (!isProject && coord.includes(':')) {
          const parts = coord.split(':');
          group = parts[0] || '';
          name = parts[1] || '';
          version = parts[2] || '';
        } else if (isProject) {
          name = coord.replace(/^:/, '');
        }

        results.push({
          line,
          configuration,
          coordinate: coord,
          group,
          name,
          version,
          isProject,
          isKts
        });
      }
    }

    return results;
  }

  /**
   * Insert or update a dependency in dependencies { ... } block safely.
   * Preserves all braces, formatting, and handles scope changes.
   * @param {string} content - build.gradle content
   * @param {string} coordinate - e.g. "com.github.infoadmstudio2020-cyber:native-logger-sdk:v1.0.1"
   * @param {string} targetScope - "api", "implementation", "compileOnly", "testImplementation", "androidTestImplementation"
   * @param {boolean} [isKts=false]
   * @returns {{ content: string, added: boolean, updated: boolean, skipped: boolean, message: string }}
   */
  static insertOrUpdateDependency(content = '', coordinate = '', targetScope = 'implementation', isKts = false) {
    if (!coordinate || !coordinate.trim()) {
      return { content, added: false, updated: false, skipped: true, message: 'Coordinate cannot be empty' };
    }

    const cleanCoord = coordinate.trim().replace(/^['"]|['"]$/g, '');
    const parts = cleanCoord.split(':');
    const targetGroup = parts[0] || '';
    const targetName = parts[1] || '';
    const targetVersion = parts[2] || '';
    const groupArtifact = targetName ? `${targetGroup}:${targetName}` : cleanCoord;

    let depBlock = this.findBlock(content, 'dependencies');

    // If no dependencies block exists, insert one at a clean logical location
    if (!depBlock) {
      const newLine = isKts
        ? `dependencies {\n    ${targetScope}("${cleanCoord}")\n}`
        : `dependencies {\n    ${targetScope} '${cleanCoord}'\n}`;

      // Place after android block or plugins block if possible
      const androidBlock = this.findBlock(content, 'android');
      if (androidBlock) {
        const insertPos = androidBlock.end;
        const newContent = content.slice(0, insertPos).trimEnd() + '\n\n' + newLine + '\n\n' + content.slice(insertPos).trimStart();
        return { content: newContent.trim(), added: true, updated: false, skipped: false, message: `Added ${targetScope} '${cleanCoord}' in new dependencies block` };
      }

      const newContent = content.trimEnd() + '\n\n' + newLine + '\n';
      return { content: newContent, added: true, updated: false, skipped: false, message: `Added ${targetScope} '${cleanCoord}' in new dependencies block` };
    }

    // Inspect existing dependencies in the block
    const declared = this.parseDependencies(depBlock.bodyText);
    const existing = declared.find(d => {
      if (d.group && d.name && targetGroup && targetName) {
        return d.group === targetGroup && d.name === targetName;
      }
      return d.coordinate === cleanCoord;
    });

    if (existing) {
      // If scope is already identical and version is same (or no version update specified), skip
      if (existing.configuration === targetScope && (!targetVersion || existing.version === targetVersion)) {
        return {
          content,
          added: false,
          updated: false,
          skipped: true,
          message: `${cleanCoord} is already declared with scope '${targetScope}'`
        };
      }

      // Scope or version needs update on existing line
      const bodyLines = depBlock.bodyText.split('\n');
      const updatedLines = bodyLines.map(l => {
        if (l.trim() === existing.line) {
          const indent = l.match(/^\s*/)[0] || '    ';
          const newVer = targetVersion || existing.version;
          const fullNotation = newVer ? `${targetGroup}:${targetName}:${newVer}` : `${targetGroup}:${targetName}`;
          return isKts || existing.isKts
            ? `${indent}${targetScope}("${fullNotation}")`
            : `${indent}${targetScope} '${fullNotation}'`;
        }
        return l;
      });

      const newBodyText = updatedLines.join('\n');
      const newContent = content.slice(0, depBlock.braceStart + 1) + newBodyText + content.slice(depBlock.braceEnd);
      return {
        content: newContent,
        added: false,
        updated: true,
        skipped: false,
        message: `Updated ${groupArtifact} scope from '${existing.configuration}' to '${targetScope}'`
      };
    }

    // Dependency not present -> insert before depBlock.braceEnd with proper indentation
    const indent = '    ';
    const depLine = isKts
      ? `${indent}${targetScope}("${cleanCoord}")`
      : `${indent}${targetScope} '${cleanCoord}'`;

    const beforeClosingBrace = content.slice(0, depBlock.braceEnd);
    const afterClosingBrace = content.slice(depBlock.braceEnd);

    // Ensure clean newline separation
    const cleanBefore = beforeClosingBrace.trimEnd();
    const newContent = `${cleanBefore}\n${depLine}\n${afterClosingBrace}`;

    return {
      content: newContent,
      added: true,
      updated: false,
      skipped: false,
      message: `Added ${targetScope} '${cleanCoord}'`
    };
  }

  /**
   * Remove a dependency from dependencies block safely without altering braces.
   * @param {string} content
   * @param {string} coordinateOrName
   * @returns {{ content: string, removed: boolean }}
   */
  static removeDependency(content = '', coordinateOrName = '') {
    const depBlock = this.findBlock(content, 'dependencies');
    if (!depBlock) return { content, removed: false };

    const target = coordinateOrName.trim();
    const parts = target.split(':');
    const targetGroup = parts[0] || '';
    const targetName = parts[1] || '';

    const lines = depBlock.bodyText.split('\n');
    let removed = false;

    const filtered = lines.filter(l => {
      const trimmed = l.trim();
      if (!trimmed || trimmed.startsWith('//')) return true;

      const isMatch = targetName && targetGroup
        ? trimmed.includes(targetGroup) && trimmed.includes(targetName)
        : trimmed.includes(target);

      if (isMatch) {
        removed = true;
        return false;
      }
      return true;
    });

    if (!removed) return { content, removed: false };

    const newBodyText = filtered.join('\n');
    const newContent = content.slice(0, depBlock.braceStart + 1) + newBodyText + content.slice(depBlock.braceEnd);
    return { content: newContent, removed: true };
  }

  /**
   * Ensure publishing block inside android { ... } block.
   * @param {string} content
   * @param {boolean} [isKts=false]
   * @returns {{ content: string, modified: boolean }}
   */
  static ensureAndroidPublishing(content = '', isKts = false) {
    const androidBlock = this.findBlock(content, 'android');
    if (!androidBlock) return { content, modified: false };

    const hasSingleVariant = androidBlock.bodyText.includes('singleVariant("release")') ||
                             androidBlock.bodyText.includes("singleVariant('release')");
    if (hasSingleVariant) return { content, modified: false };

    const pubBlock = isKts
      ? `\n    publishing {\n        singleVariant("release") {\n            withSourcesJar()\n            withJavadocJar()\n        }\n    }`
      : `\n    publishing {\n        singleVariant("release") {\n            withSourcesJar()\n            withJavadocJar()\n        }\n    }`;

    // Insert right before the closing brace of androidBlock
    const beforeClose = content.slice(0, androidBlock.braceEnd).trimEnd();
    const afterClose = content.slice(androidBlock.braceEnd);
    const newContent = `${beforeClose}\n${pubBlock}\n${afterClose}`;

    return { content: newContent, modified: true };
  }

  /**
   * Ensure JitPack repository in repositories block (settings.gradle or build.gradle).
   * @param {string} content
   * @returns {{ content: string, modified: boolean }}
   */
  static ensureJitpackRepository(content = '') {
    if (content.includes('jitpack.io')) {
      return { content, modified: false };
    }

    // Try finding repositories inside dependencyResolutionManagement
    const drmBlock = this.findBlock(content, 'dependencyResolutionManagement');
    if (drmBlock) {
      const reposBlock = this.findBlock(drmBlock.bodyText, 'repositories');
      if (reposBlock) {
        const repoInsertIdx = drmBlock.braceStart + 1 + reposBlock.braceEnd;
        const line = "        maven { url 'https://jitpack.io' }\n";
        const before = content.slice(0, repoInsertIdx).trimEnd();
        const after = content.slice(repoInsertIdx);
        const newContent = `${before}\n${line}${after}`;
        return { content: newContent, modified: true };
      }
    }

    // Try finding root repositories block
    const rootRepo = this.findBlock(content, 'repositories');
    if (rootRepo) {
      const line = "        maven { url 'https://jitpack.io' }\n";
      const before = content.slice(0, rootRepo.braceEnd).trimEnd();
      const after = content.slice(rootRepo.braceEnd);
      const newContent = `${before}\n${line}${after}`;
      return { content: newContent, modified: true };
    }

    // Fallback: append dependencyResolutionManagement if settings.gradle-like
    if (content.includes('pluginManagement') || content.includes('rootProject')) {
      const drmSnippet = `\ndependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}\n`;
      return { content: content.trimEnd() + '\n' + drmSnippet, modified: true };
    }

    return { content, modified: false };
  }
}
