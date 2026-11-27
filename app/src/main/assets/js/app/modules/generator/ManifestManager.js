/**
 * ManifestManager.js — Complete parser, patcher, and generator for AndroidManifest.xml
 *
 * Dedicated to Android Manifest manipulation, completely decoupled from Gradle.
 */

export const COMMON_PERMISSIONS = [
  { name: 'android.permission.INTERNET', label: 'Internet Access', desc: 'Allows application to open network sockets', category: 'Network' },
  { name: 'android.permission.ACCESS_NETWORK_STATE', label: 'Access Network State', desc: 'Allows access to network connection status', category: 'Network' },
  { name: 'android.permission.ACCESS_WIFI_STATE', label: 'Access Wi-Fi State', desc: 'Allows access to Wi-Fi connection info', category: 'Network' },
  { name: 'android.permission.CAMERA', label: 'Camera', desc: 'Required to be able to access the camera device', category: 'Hardware' },
  { name: 'android.permission.RECORD_AUDIO', label: 'Record Audio', desc: 'Allows an application to record audio', category: 'Hardware' },
  { name: 'android.permission.ACCESS_FINE_LOCATION', label: 'Fine Location (GPS)', desc: 'Allows access to precise GPS location', category: 'Location' },
  { name: 'android.permission.ACCESS_COARSE_LOCATION', label: 'Coarse Location (Cell/WiFi)', desc: 'Allows access to approximate location', category: 'Location' },
  { name: 'android.permission.POST_NOTIFICATIONS', label: 'Post Notifications', desc: 'Required to post notifications on Android 13+', category: 'Notifications' },
  { name: 'android.permission.VIBRATE', label: 'Vibrate', desc: 'Allows access to the vibrator', category: 'Hardware' },
  { name: 'android.permission.WAKE_LOCK', label: 'Wake Lock', desc: 'Allows using PowerManager WakeLocks to keep processor from sleeping', category: 'System' },
  { name: 'android.permission.READ_MEDIA_IMAGES', label: 'Read Media Images', desc: 'Allows read access to photos on Android 13+', category: 'Storage' },
  { name: 'android.permission.READ_EXTERNAL_STORAGE', label: 'Read External Storage', desc: 'Legacy read external storage permission', category: 'Storage' },
  { name: 'android.permission.WRITE_EXTERNAL_STORAGE', label: 'Write External Storage', desc: 'Legacy write external storage permission', category: 'Storage' },
  { name: 'android.permission.FOREGROUND_SERVICE', label: 'Foreground Service', desc: 'Allows use of foreground services', category: 'System' }
];

export class ManifestManager {
  /**
   * Parse an AndroidManifest.xml string into a structured data object.
   * @param {string} xmlString
   * @returns {object} Parsed manifest details
   */
  static parse(xmlString = '') {
    const res = {
      isValid: true,
      rawXml: xmlString,
      packageName: '',
      hasAndroidNamespace: false,
      permissions: [],
      features: [],
      application: {
        present: false,
        allowBackup: null,
        supportsRtl: null,
        theme: '',
        label: '',
        icon: '',
        roundIcon: '',
        usesCleartextTraffic: null
      },
      activities: [],
      services: [],
      receivers: [],
      providers: [],
      diagnostics: []
    };

    if (!xmlString || typeof xmlString !== 'string') {
      res.isValid = false;
      res.diagnostics.push({ severity: 'error', message: 'Manifest file is empty' });
      return res;
    }

    // Check Android namespace
    if (xmlString.includes('xmlns:android="http://schemas.android.com/apk/res/android"')) {
      res.hasAndroidNamespace = true;
    } else {
      res.diagnostics.push({ severity: 'warn', message: 'Missing standard xmlns:android namespace' });
    }

    // Extract package attribute from <manifest>
    const pkgMatch = xmlString.match(/<manifest[^>]*package=["']([^"']+)["']/i);
    if (pkgMatch) {
      res.packageName = pkgMatch[1];
    }

    // Extract <uses-permission>
    const permMatches = xmlString.matchAll(/<uses-permission[^>]*android:name=["']([^"']+)["'][^>]*\/?>/gi);
    for (const m of permMatches) {
      if (m[1] && !res.permissions.includes(m[1])) {
        res.permissions.push(m[1]);
      }
    }

    // Extract <uses-feature>
    const featureMatches = xmlString.matchAll(/<uses-feature[^>]*android:name=["']([^"']+)["'](?:[^>]*android:required=["']([^"']+)["'])?[^>]*\/?>/gi);
    for (const m of featureMatches) {
      if (m[1]) {
        res.features.push({
          name: m[1],
          required: m[2] !== 'false'
        });
      }
    }

    // Extract <application> attributes
    const appMatch = xmlString.match(/<application([^>]*)>/i);
    if (appMatch) {
      res.application.present = true;
      const appAttrs = appMatch[1];

      const getAttr = (name) => {
        const m = appAttrs.match(new RegExp(`android:${name}=["']([^"']+)["']`, 'i'));
        return m ? m[1] : null;
      };

      res.application.allowBackup = getAttr('allowBackup');
      res.application.supportsRtl = getAttr('supportsRtl');
      res.application.theme = getAttr('theme') || '';
      res.application.label = getAttr('label') || '';
      res.application.icon = getAttr('icon') || '';
      res.application.roundIcon = getAttr('roundIcon') || '';
      res.application.usesCleartextTraffic = getAttr('usesCleartextTraffic');
    }

    // Extract <activity> components
    const activityMatches = xmlString.matchAll(/<activity([^>]*)>([\s\S]*?)<\/activity>|<activity([^>]*)\/>/gi);
    for (const m of activityMatches) {
      const attrs = m[1] || m[3] || '';
      const body = m[2] || '';

      const nameMatch = attrs.match(/android:name=["']([^"']+)["']/i);
      const expMatch = attrs.match(/android:exported=["']([^"']+)["']/i);
      const isLauncher = body.includes('android.intent.action.MAIN') && body.includes('android.intent.category.LAUNCHER');

      if (nameMatch) {
        res.activities.push({
          name: nameMatch[1],
          exported: expMatch ? expMatch[1] : null,
          isLauncher,
          rawAttrs: attrs
        });

        // Diagnostic: Android 12+ requires android:exported on activities with intent-filters
        if (body.includes('<intent-filter') && !expMatch) {
          res.diagnostics.push({
            severity: 'warn',
            message: `Activity "${nameMatch[1]}" has an intent-filter but is missing explicit android:exported="true|false" (required on Android 12+)`
          });
        }
      }
    }

    // Extract <service> components
    const serviceMatches = xmlString.matchAll(/<service([^>]*)(?:\/?>|>([\s\S]*?)<\/service>)/gi);
    for (const m of serviceMatches) {
      const attrs = m[1] || '';
      const nameMatch = attrs.match(/android:name=["']([^"']+)["']/i);
      if (nameMatch) {
        res.services.push({ name: nameMatch[1], rawAttrs: attrs });
      }
    }

    // Extract <receiver> components
    const receiverMatches = xmlString.matchAll(/<receiver([^>]*)(?:\/?>|>([\s\S]*?)<\/receiver>)/gi);
    for (const m of receiverMatches) {
      const attrs = m[1] || '';
      const nameMatch = attrs.match(/android:name=["']([^"']+)["']/i);
      if (nameMatch) {
        res.receivers.push({ name: nameMatch[1], rawAttrs: attrs });
      }
    }

    return res;
  }

  /**
   * Add a permission to the manifest XML string.
   */
  static addPermission(xmlString, permissionName) {
    if (!permissionName || xmlString.includes(`"${permissionName}"`) || xmlString.includes(`'${permissionName}'`)) {
      return xmlString;
    }

    const permTag = `    <uses-permission android:name="${permissionName}" />`;

    // Try inserting after existing uses-permission
    const lastPermIdx = xmlString.lastIndexOf('</uses-permission>');
    const lastSelfPermIdx = xmlString.lastIndexOf('<uses-permission');

    if (lastSelfPermIdx !== -1) {
      const endOfTag = xmlString.indexOf('>', lastSelfPermIdx);
      if (endOfTag !== -1) {
        return xmlString.slice(0, endOfTag + 1) + '\n' + permTag + xmlString.slice(endOfTag + 1);
      }
    }

    // Try inserting before <application
    const appIdx = xmlString.indexOf('<application');
    if (appIdx !== -1) {
      return xmlString.slice(0, appIdx) + permTag + '\n\n    ' + xmlString.slice(appIdx);
    }

    // Insert after <manifest ...>
    const manifestIdx = xmlString.indexOf('>');
    if (manifestIdx !== -1) {
      return xmlString.slice(0, manifestIdx + 1) + '\n\n' + permTag + xmlString.slice(manifestIdx + 1);
    }

    return xmlString + '\n' + permTag;
  }

  /**
   * Remove a permission from the manifest XML string.
   */
  static removePermission(xmlString, permissionName) {
    const reg = new RegExp(`\\s*<uses-permission[^>]*android:name=["']${permissionName}["'][^>]*\\/?>`, 'gi');
    return xmlString.replace(reg, '');
  }

  /**
   * Set or update an attribute on the <application> tag.
   */
  static setApplicationAttribute(xmlString, attrName, attrValue) {
    const appMatch = xmlString.match(/<application([^>]*)>/i);
    if (!appMatch) return xmlString;

    const fullTag = appMatch[0];
    let attrs = appMatch[1];
    const attrPattern = new RegExp(`android:${attrName}=["'][^"']*["']`, 'i');

    if (attrValue === null || attrValue === '') {
      // Remove attribute
      attrs = attrs.replace(attrPattern, '').trim();
    } else if (attrPattern.test(attrs)) {
      // Replace existing
      attrs = attrs.replace(attrPattern, `android:${attrName}="${attrValue}"`);
    } else {
      // Append new attribute
      attrs = `${attrs} android:${attrName}="${attrValue}"`;
    }

    const newTag = `<application ${attrs.trim().replace(/\s+/g, ' ')}>`;
    return xmlString.replace(fullTag, newTag);
  }

  /**
   * Add an activity to the manifest XML string.
   */
  static addActivity(xmlString, { name, exported = 'true', isLauncher = false }) {
    if (!name) return xmlString;

    const appEndIdx = xmlString.lastIndexOf('</application>');
    if (appEndIdx === -1) return xmlString;

    let activityXml = '';
    if (isLauncher) {
      activityXml = `        <activity
            android:name="${name}"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>\n`;
    } else {
      activityXml = `        <activity
            android:name="${name}"
            android:exported="${exported}" />\n`;
    }

    return xmlString.slice(0, appEndIdx) + activityXml + '    ' + xmlString.slice(appEndIdx);
  }

  /**
   * Format and tidy XML indentation.
   */
  static formatXml(xmlString) {
    let formatted = '';
    let pad = 0;
    const lines = xmlString.replace(/>\s*</g, '><').split(/(?=<)|(?<=>)/g);

    for (let i = 0; i < lines.length; i++) {
      let line = lines[i].trim();
      if (!line) continue;

      if (line.match(/^<\/\w/)) {
        pad = Math.max(0, pad - 1);
      }

      formatted += '    '.repeat(pad) + line + '\n';

      if (line.match(/^<\w[^>]*[^\/]>$/) && !line.startsWith('<?') && !line.startsWith('<!')) {
        pad++;
      }
    }

    return formatted.trim();
  }

  /**
   * Generate a standard clean AndroidManifest.xml.
   */
  static createDefaultManifest({ isApp = false, packageName = 'com.example.android', label = 'AndroidModule' } = {}) {
    if (isApp) {
      return `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${packageName}">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppCompat.Light.DarkActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
`;
    }

    // Android Library Manifest
    return `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${packageName}">

    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:supportsRtl="true" />

</manifest>
`;
  }
}
