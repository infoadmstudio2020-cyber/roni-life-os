/**
 * BinaryClassInspector — inspects classes.jar and embedded JARs in input AAR/JAR archives.
 * Extracts class listings and detects duplicate class collisions across binaries.
 */

export class BinaryClassInspector {
  /**
   * Normalize zip entry path to fully-qualified Java class name.
   * e.g. "com/example/MyClass.class" -> "com.example.MyClass"
   * e.g. "classes.jar!com/example/MyClass.class" -> "com.example.MyClass"
   */
  static pathToClassName(entryPath) {
    if (!entryPath) return null;
    let clean = entryPath.replace(/\\/g, '/');
    if (clean.includes('!')) {
      clean = clean.split('!').pop();
    }
    if (!clean.endsWith('.class') || clean.endsWith('module-info.class')) {
      return null;
    }
    // Ignore package-info or metadata
    const withoutExt = clean.slice(0, -6);
    return withoutExt.replace(/^\/+/, '').replace(/\//g, '.');
  }

  /**
   * Inspect a list of file entries or archive descriptors.
   * An archive descriptor can be:
   * {
   *   id: 'guava-31.0.jar',
   *   name: 'guava-31.0.jar',
   *   type: 'jar' | 'aar',
   *   classes: ['com.google.common.base.Preconditions', ...] // or files: { 'com/google/...class': ... }
   * }
   * 
   * @param {Array<Object>} archives
   * @returns {Object} Inspection result with class index and duplicate collision report
   */
  static inspectArchives(archives = []) {
    const classMap = new Map(); // className -> Array<{ archiveId, archiveName, path, isEmbedded, size }>
    const archiveSummaries = [];

    for (const archive of archives) {
      const archiveId = archive.id || archive.name || 'unknown-artifact';
      const archiveName = archive.name || archiveId;
      const isAar = archive.type === 'aar' || archiveName.endsWith('.aar');
      const classList = [];

      // If pre-parsed class list provided
      if (Array.isArray(archive.classes)) {
        for (const cls of archive.classes) {
          const className = cls.endsWith('.class') ? this.pathToClassName(cls) : cls;
          if (className) {
            classList.push({
              className,
              path: cls,
              isEmbedded: false,
              size: 0
            });
          }
        }
      }

      // If embedded jars provided (common in AARs like libs/internal.jar)
      if (Array.isArray(archive.embeddedJars)) {
        for (const emb of archive.embeddedJars) {
          const embName = emb.name || 'embedded.jar';
          if (Array.isArray(emb.classes)) {
            for (const cls of emb.classes) {
              const className = cls.endsWith('.class') ? this.pathToClassName(cls) : cls;
              if (className) {
                classList.push({
                  className,
                  path: `libs/${embName}!${cls}`,
                  isEmbedded: true,
                  embeddedJarName: embName,
                  size: emb.size || 0
                });
              }
            }
          }
        }
      }

      // If file map provided (e.g. extracted ZIP file keys)
      if (archive.files && typeof archive.files === 'object') {
        for (const [filePath, content] of Object.entries(archive.files)) {
          const isEmbedded = filePath.startsWith('libs/') && filePath.endsWith('.jar');
          const className = this.pathToClassName(filePath);
          if (className) {
            classList.push({
              className,
              path: filePath,
              isEmbedded: filePath.includes('libs/') || filePath.startsWith('classes.jar!'),
              size: typeof content === 'string' ? content.length : (content?.byteLength || 0)
            });
          }
        }
      }

      // Populate into inverted class map
      for (const item of classList) {
        if (!classMap.has(item.className)) {
          classMap.set(item.className, []);
        }
        classMap.get(item.className).push({
          archiveId,
          archiveName,
          path: item.path,
          isEmbedded: Boolean(item.isEmbedded),
          embeddedJarName: item.embeddedJarName || null,
          size: item.size || 0
        });
      }

      archiveSummaries.push({
        id: archiveId,
        name: archiveName,
        isAar,
        totalClasses: classList.length,
        hasEmbeddedJars: Boolean(archive.embeddedJars?.length)
      });
    }

    // Detect duplicate classes (present in > 1 distinct archive)
    const duplicates = [];
    let totalDuplicatesCount = 0;

    for (const [className, locations] of classMap.entries()) {
      // Distinct archives containing this class
      const distinctArchives = new Set(locations.map(l => l.archiveId));
      if (distinctArchives.size > 1 || locations.length > 1) {
        totalDuplicatesCount++;
        duplicates.push({
          className,
          occurrences: locations.length,
          distinctArchivesCount: distinctArchives.size,
          locations: [...locations]
        });
      }
    }

    // Sort duplicates alphabetically
    duplicates.sort((a, b) => a.className.localeCompare(b.className));

    return {
      totalArchives: archives.length,
      totalUniqueClasses: classMap.size,
      totalDuplicatesCount,
      hasConflicts: totalDuplicatesCount > 0,
      archiveSummaries,
      duplicates,
      classMap
    };
  }

  /**
   * Async inspection of binary File / ArrayBuffer using JSZip if available.
   * Extracts classes.jar and any embedded libs/*.jar within AARs.
   */
  static async inspectZipArchive(fileOrBuffer, archiveName, JSZipLib) {
    if (!JSZipLib) {
      throw new Error('JSZip library is required to inspect binary ZIP/JAR/AAR files');
    }
    const zip = new JSZipLib();
    const loadedZip = await zip.loadAsync(fileOrBuffer);
    const classes = [];
    const embeddedJars = [];

    const isAar = archiveName.endsWith('.aar');

    if (isAar) {
      // Inspect classes.jar inside AAR
      const classesJarEntry = loadedZip.file('classes.jar');
      if (classesJarEntry) {
        const classesJarBuffer = await classesJarEntry.async('arraybuffer');
        const innerZip = new JSZipLib();
        const loadedInner = await innerZip.loadAsync(classesJarBuffer);
        for (const innerPath of Object.keys(loadedInner.files)) {
          const cls = this.pathToClassName(innerPath);
          if (cls) classes.push(cls);
        }
      }

      // Inspect any libs/*.jar embedded in AAR
      for (const [path, entry] of Object.entries(loadedZip.files)) {
        if (path.startsWith('libs/') && path.endsWith('.jar') && !entry.dir) {
          try {
            const embBuffer = await entry.async('arraybuffer');
            const embZip = new JSZipLib();
            const loadedEmb = await embZip.loadAsync(embBuffer);
            const embClasses = [];
            for (const embPath of Object.keys(loadedEmb.files)) {
              const cls = this.pathToClassName(embPath);
              if (cls) embClasses.push(cls);
            }
            embeddedJars.push({
              name: path.split('/').pop(),
              path,
              classes: embClasses
            });
          } catch {
            // Silently continue if embedded jar is unreadable
          }
        }
      }
    } else {
      // Standard JAR
      for (const path of Object.keys(loadedZip.files)) {
        const cls = this.pathToClassName(path);
        if (cls) classes.push(cls);
      }
    }

    return {
      id: archiveName,
      name: archiveName,
      type: isAar ? 'aar' : 'jar',
      classes,
      embeddedJars
    };
  }
}
