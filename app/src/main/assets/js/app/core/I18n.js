/**
 * I18n — lightweight internationalization system (English + Bangla).
 *
 * Design: string-key dictionaries. The English string IS the key, so the
 * existing page templates do not need restructuring — rendered DOM text is
 * translated in place (text nodes + common attributes), and `t()` is used
 * directly by components that build UI in JavaScript.
 *
 * - Missing keys gracefully fall back to English (the key itself).
 * - Dynamic values use {param} interpolation.
 * - Persistence: localStorage 'libgen.ui.language' (instant boot) mirrored
 *   into the existing IndexedDB settings store under key 'uiLanguage'.
 * - Technical content (code, logs, icon names, identifiers) is never
 *   translated — the translator skips those subtrees explicitly.
 */
import { eventBus } from './EventBus.js';

const LS_KEY = 'libgen.ui.language';

/** Subtrees whose text must never be translated. */
const SKIP_SELECTOR =
  'script, style, code, pre, textarea, kbd, .material-symbols-outlined, ' +
  '#console-body, [data-no-i18n]';

/** Attributes that may contain user-visible text. */
const TRANSLATABLE_ATTRS = ['title', 'placeholder', 'aria-label', 'data-tooltip', 'alt'];

const translations = {
  /* ── Bangla (বাংলা) ── */
  bn: {
    // Navigation & sections
    'Dashboard': 'ড্যাশবোর্ড',
    'Project': 'প্রজেক্ট',
    'Templates': 'টেমপ্লেট',
    'Explorer': 'এক্সপ্লোরার',
    'Editor': 'এডিটর',
    'Generator': 'জেনারেটর',
    'Build': 'বিল্ড',
    'Publish': 'পাবলিশ',
    'Authentication': 'অথেন্টিকেশন',
    'Repository': 'রিপোজিটরি',
    'Files': 'ফাইল',
    'Commit': 'কমিট',
    'Tags': 'ট্যাগ',
    'Release': 'রিলিজ',
    'History': 'হিস্ট্রি',
    'Version Manager': 'ভার্সন ম্যানেজার',
    'Class & D8 Guard': 'ক্লাস ও D8 গার্ড',
    'Ecosystem Version Manager': 'ইকোসিস্টেম ভার্সন ম্যানেজার',
    'All Libraries Dashboard': 'সকল লাইব্রেরি ড্যাশবোর্ড',
    'Release Monitor': 'রিলিজ মনিটর',
    'JitPack Build Monitor': 'জিটপ্যাক বিল্ড মনিটর',
    'Dependency Monitor': 'ডিপেনডেন্সি মনিটর',
    'Scan GitHub': 'গিটহাব স্ক্যান করুন',
    'Add Repository': 'রিপোজিটরি যোগ করুন',
    'Import My Repos': 'আমার রিপো ইমপোর্ট করুন',
    'Remove Dummy': 'ডামি মুছুন',
    'Load Samples': 'নমুনা লোড করুন',
    'Settings': 'সেটিংস',
    'Downloads': 'ডাউনলোড',
    'Logs': 'লগ',
    'Main': 'প্রধান',
    'Generate': 'জেনারেট',
    'Tools': 'টুলস',

    // Toolbar / app bar
    'New': 'নতুন',
    'Open': 'খুলুন',
    'Save': 'সংরক্ষণ',
    'Export': 'এক্সপোর্ট',
    'Import': 'ইমপোর্ট',
    'New Project': 'নতুন প্রজেক্ট',
    'Open Project': 'প্রজেক্ট খুলুন',
    'Save (Ctrl+S)': 'সংরক্ষণ (Ctrl+S)',
    'Export ZIP': 'ZIP এক্সপোর্ট',
    'Import Project': 'প্রজেক্ট ইমপোর্ট',
    'Toggle Console': 'কনসোল টগল করুন',
    'Toggle sidebar': 'নেভিগেশন খুলুন',
    'No project open': 'কোনো প্রজেক্ট খোলা নেই',
    'Toggle Language (English / বাংলা)': 'ভাষা পরিবর্তন (English / বাংলা)',
    'Toggle Language': 'ভাষা পরিবর্তন',
    'Language': 'ভাষা',
    'Resolution Test': 'রেজোলিউশন টেস্ট',
    'Console': 'কনসোল',
    'More': 'আরও',
    'Home': 'হোম',
    'Menu': 'মেনু',
    'Back': 'পিছনে',
    'All screens': 'সব স্ক্রিন',

    // Common actions
    'Cancel': 'বাতিল',
    'OK': 'ঠিক আছে',
    'Confirm': 'নিশ্চিত করুন',
    'Close': 'বন্ধ করুন',
    'Delete': 'মুছুন',
    'Delete All': 'সব মুছুন',
    'Clear': 'পরিষ্কার',
    'Clear All': 'সব পরিষ্কার করুন',
    'Copy': 'কপি',
    'Refresh': 'রিফ্রেশ',
    'Reset': 'রিসেট',
    'Save Settings': 'সেটিংস সংরক্ষণ করুন',
    'Reset to Defaults': 'ডিফল্টে রিসেট করুন',
    'Loading…': 'লোড হচ্ছে…',
    'Ready': 'প্রস্তুত',
    'Starting…': 'শুরু হচ্ছে…',
    'Done': 'সম্পন্ন',
    'Saved': 'সংরক্ষিত',
    'Generating…': 'জেনারেট হচ্ছে…',
    'Exporting…': 'এক্সপোর্ট হচ্ছে…',
    'Exported': 'এক্সপোর্ট হয়েছে',
    'Value': 'মান',
    'Unknown': 'অজানা',
    'Status': 'স্ট্যাটাস',
    'Date': 'তারিখ',
    'Version': 'ভার্সন',
    'Description': 'বিবরণ',
    'Author': 'লেখক',
    'Company': 'কোম্পানি',
    'Company / Organization': 'কোম্পানি / সংস্থা',
    'License': 'লাইসেন্স',
    'Preview': 'প্রিভিউ',
    'Select': 'নির্বাচন করুন',
    'Find': 'খুঁজুন',
    'Rename': 'নাম পরিবর্তন',
    'Duplicate': 'ডুপ্লিকেট',
    'Replace': 'প্রতিস্থাপন',
    'Abort': 'থামান',
    'Load': 'লোড',
    'Sign Out': 'সাইন আউট',
    'Verify Token': 'টোকেন যাচাই করুন',
    'Authenticate': 'অথেন্টিকেট করুন',
    'View All': 'সব দেখুন',
    'Visit': 'ভিজিট করুন',
    'Get it': 'সংগ্রহ করুন',
    'Go to': 'যান',
    'Open in Editor': 'এডিটরে খুলুন',
    'Open on GitHub': 'GitHub-এ খুলুন',
    'Use Template': 'টেমপ্লেট ব্যবহার করুন',
    'Select Template': 'টেমপ্লেট নির্বাচন করুন',
    'Generate Project': 'প্রজেক্ট জেনারেট করুন',
    'Generate Files': 'ফাইল জেনারেট করুন',
    'New File': 'নতুন ফাইল',
    'New Folder': 'নতুন ফোল্ডার',
    'Save Changes': 'পরিবর্তন সংরক্ষণ করুন',
    'Run Validation': 'ভ্যালিডেশন চালান',
    'Re-validate': 'পুনরায় ভ্যালিডেট করুন',
    'Validation Results': 'ভ্যালিডেশন ফলাফল',
    'Create Repository': 'রিপোজিটরি তৈরি করুন',
    'Create Tag': 'ট্যাগ তৈরি করুন',
    'Create Release': 'রিলিজ তৈরি করুন',
    'Load Tags': 'ট্যাগ লোড করুন',
    'Load Releases': 'রিলিজ লোড করুন',
    'Load Existing': 'বিদ্যমান লোড করুন',
    'General': 'সাধারণ',
    'Actions': 'অ্যাকশন',
    'Copy Step 1': 'ধাপ ১ কপি করুন',
    'Copy Step 2': 'ধাপ ২ কপি করুন',

    // Theme & language
    'Appearance': 'চেহারা',
    'Theme': 'থিম',
    'Light': 'লাইট',
    'Dark': 'ডার্ক',
    'System': 'সিস্টেম',
    'Current color scheme': 'বর্তমান কালার স্কিম',
    'Language': 'ভাষা',
    'UI Language': 'UI ভাষা',
    'Interface language': 'ইন্টারফেসের ভাষা',
    'Switch language': 'ভাষা পরিবর্তন করুন',
    'Toggle theme': 'থিম পরিবর্তন করুন',

    // Dashboard
    'Android Library Generator': 'অ্যান্ড্রয়েড লাইব্রেরি জেনারেটর',
    'Generate fully structured, JitPack-ready Android libraries in seconds.':
      'কয়েক সেকেন্ডেই সম্পূর্ণ স্ট্রাকচার্ড, JitPack-রেডি অ্যান্ড্রয়েড লাইব্রেরি তৈরি করুন।',
    'Recent Projects': 'সাম্প্রতিক প্রজেক্ট',
    'No recent projects. Create one to get started.':
      'কোনো সাম্প্রতিক প্রজেক্ট নেই। শুরু করতে একটি তৈরি করুন।',
    'Template Gallery': 'টেমপ্লেট গ্যালারি',
    'Projects': 'প্রজেক্টসমূহ',
    'Exports': 'এক্সপোর্ট',
    'Total Files': 'মোট ফাইল',
    'JitPack Ready': 'JitPack-প্রস্তুত',
    'total created': 'মোট তৈরি',
    'ZIP exports': 'ZIP এক্সপোর্ট',
    'across all projects': 'সব প্রজেক্ট জুড়ে',
    'projects ready': 'প্রজেক্ট প্রস্তুত',

    // Project / generator
    'Project Details': 'প্রজেক্টের বিবরণ',
    'Project Name': 'প্রজেক্টের নাম',
    'Package Name': 'প্যাকেজ নাম',
    'Project Settings': 'প্রজেক্ট সেটিংস',
    'Project Generator': 'প্রজেক্ট জেনারেটর',
    'Enter project name and package to preview': 'প্রিভিউ দেখতে প্রজেক্টের নাম ও প্যাকেজ লিখুন',
    'Fill in form details to preview': 'প্রিভিউ দেখতে ফর্মের তথ্য পূরণ করুন',
    'Generated Files Preview': 'জেনারেটেড ফাইল প্রিভিউ',
    'Generated Files': 'জেনারেটেড ফাইলসমূহ',
    'Used as class name — PascalCase recommended': 'ক্লাস নাম হিসেবে ব্যবহৃত হয় — PascalCase প্রস্তাবিত',
    '(optional)': '(ঐচ্ছিক)',
    'SDK Configuration': 'SDK কনফিগারেশন',
    'SDK & Build Configuration': 'SDK ও বিল্ড কনফিগারেশন',
    'Min SDK': 'সর্বনিম্ন SDK',
    'Target SDK': 'টার্গেট SDK',
    'Compile SDK': 'কম্পাইল SDK',
    'Kotlin': 'কোটলিন',
    'Java': 'জাভা',
    'Gradle Files': 'গ্র্যাডল ফাইলসমূহ',
    'No Gradle files found': 'কোনো গ্র্যাডল ফাইল পাওয়া যায়নি',
    'View and edit Gradle build files': 'গ্র্যাডল বিল্ড ফাইল দেখুন ও সম্পাদনা করুন',
    'Choose a template to scaffold your Android library project':
      'আপনার অ্যান্ড্রয়েড লাইব্রেরি প্রজেক্ট স্ক্যাফোল্ড করতে একটি টেমপ্লেট নির্বাচন করুন',
    'Generate a fully structured JitPack-compatible Android library project':
      'সম্পূর্ণ স্ট্রাকচার্ড JitPack-সামঞ্জস্যপূর্ণ অ্যান্ড্রয়েড লাইব্রেরি প্রজেক্ট জেনারেট করুন',
    'Export Required Information Schema': 'রিকোয়ার্ড ইনফরমেশন স্কিমা এক্সপোর্ট',
    'Copy JSON': 'JSON কপি',
    'Download JSON': 'JSON ডাউনলোড',
    'Close': 'বন্ধ করুন',
    'Required Information Schema': 'রিকোয়ার্ড ইনফরমেশন স্কিমা',

    // Editor / explorer / empty states
    'Open a file to start editing': 'সম্পাদনা শুরু করতে একটি ফাইল খুলুন',
    'Double-click a file in the Explorer': 'এক্সপ্লোরারে কোনো ফাইলে ডাবল-ক্লিক করুন',
    'Create or open a project from the Dashboard.': 'ড্যাশবোর্ড থেকে একটি প্রজেক্ট তৈরি বা খুলুন।',
    'Create or open a project to begin.': 'শুরু করতে একটি প্রজেক্ট তৈরি বা খুলুন।',
    'No project is currently open.': 'বর্তমানে কোনো প্রজেক্ট খোলা নেই।',
    'No project open.': 'কোনো প্রজেক্ট খোলা নেই।',
    'No project open. Generate a project first.': 'কোনো প্রজেক্ট খোলা নেই। প্রথমে একটি প্রজেক্ট জেনারেট করুন।',
    'No project': 'কোনো প্রজেক্ট নেই',
    'Project is empty.': 'প্রজেক্টটি খালি।',
    'Generate files to get started.': 'শুরু করতে ফাইল জেনারেট করুন।',
    'Properties': 'প্রপার্টিজ',
    'Select a file or folder to view properties.': 'প্রপার্টি দেখতে একটি ফাইল বা ফোল্ডার নির্বাচন করুন।',

    // GitHub pages
    'GitHub Integration': 'GitHub ইন্টিগ্রেশন',
    'Configure your repository and generate GitHub-related files':
      'আপনার রিপোজিটরি কনফিগার করুন এবং GitHub-সংক্রান্ত ফাইল জেনারেট করুন',
    'Repository Configuration': 'রিপোজিটরি কনফিগারেশন',
    'Repository Name': 'রিপোজিটরির নাম',
    'GitHub Username / Org': 'GitHub ইউজারনেম / অর্গানাইজেশন',
    'Generate GitHub Files': 'GitHub ফাইল জেনারেট করুন',
    'No GitHub files yet. Generate them above.': 'এখনও কোনো GitHub ফাইল নেই। উপরে জেনারেট করুন।',
    'GitHub Files': 'GitHub ফাইলসমূহ',
    'GitHub Settings': 'GitHub সেটিংস',
    'Default values used by the GitHub Publisher': 'GitHub পাবলিশার দ্বারা ব্যবহৃত ডিফল্ট মান',
    'Connect your GitHub account using a Personal Access Token':
      'একটি Personal Access Token ব্যবহার করে আপনার GitHub অ্যাকাউন্ট সংযুক্ত করুন',
    'Personal Access Token': 'পার্সোনাল অ্যাক্সেস টোকেন',
    'How to create a token': 'কীভাবে টোকেন তৈরি করবেন',
    'Classic token or fine-grained token with': 'ক্লাসিক টোকেন বা ফাইন-গ্রেইন্ড টোকেন — প্রয়োজন',
    'scope': 'স্কোপ',
    'Select scopes:': 'স্কোপ নির্বাচন করুন:',
    'Set expiration (90 days recommended)': 'এক্সপিরেশন নির্ধারণ করুন (৯০ দিন প্রস্তাবিত)',
    'Generate new token (classic)': 'নতুন টোকেন জেনারেট করুন (ক্লাসিক)',
    'Generate token': 'টোকেন জেনারেট করুন',
    'and copy it': 'এবং এটি কপি করুন',
    'Paste it in the Token field and click': 'এটি টোকেন ফিল্ডে পেস্ট করুন এবং ক্লিক করুন',
    'Show/hide token': 'টোকেন দেখুন/লুকান',
    'Token required': 'টোকেন প্রয়োজন',
    'Paste your GitHub token': 'আপনার GitHub টোকেন পেস্ট করুন',
    'Signed out': 'সাইন আউট হয়েছে',
    'GitHub token cleared': 'GitHub টোকেন মুছে ফেলা হয়েছে',
    'Your Repositories': 'আপনার রিপোজিটরিসমূহ',
    'Create or select a GitHub repository for your library':
      'আপনার লাইব্রেরির জন্য একটি GitHub রিপোজিটরি তৈরি বা নির্বাচন করুন',
    'Open Repository': 'রিপোজিটরি খুলুন',
    'Open repository': 'রিপোজিটরি খুলুন',
    'GitHub repository': 'GitHub রিপোজিটরি',
    'GitHub repository slug': 'GitHub রিপোজিটরি স্লাগ',
    'Create a new repository on': 'নতুন রিপোজিটরি তৈরি করুন',
    'Build commit messages and view commit history': 'কমিট মেসেজ তৈরি করুন এবং কমিট হিস্ট্রি দেখুন',
    'Build Message': 'বিল্ড মেসেজ',
    'Create and manage semantic version tags on your repository':
      'আপনার রিপোজিটরিতে সেমান্টিক ভার্সন ট্যাগ তৈরি ও পরিচালনা করুন',
    'Version Tags': 'ভার্সন ট্যাগ',
    'Create GitHub releases from version tags': 'ভার্সন ট্যাগ থেকে GitHub রিলিজ তৈরি করুন',
    'Quick increment:': 'দ্রুত বৃদ্ধি:',
    '— no tags found —': '— কোনো ট্যাগ পাওয়া যায়নি —',
    '— load failed —': '— লোড ব্যর্থ —',
    'Publish History': 'পাবলিশ হিস্ট্রি',
    'All GitHub publish operations recorded on this device':
      'এই ডিভাইসে রেকর্ড করা সকল GitHub পাবলিশ অপারেশন',
    'No publish history yet': 'এখনও কোনো পাবলিশ হিস্ট্রি নেই',
    'Publish a project to see history here': 'হিস্ট্রি দেখতে একটি প্রজেক্ট পাবলিশ করুন',
    'Delete record': 'রেকর্ড মুছুন',
    'Clear History': 'হিস্ট্রি পরিষ্কার করুন',
    'Delete all publish history records?': 'সব পাবলিশ হিস্ট্রি রেকর্ড মুছে ফেলবেন?',
    'History cleared': 'হিস্ট্রি পরিষ্কার করা হয়েছে',
    'Record deleted': 'রেকর্ড মুছে ফেলা হয়েছে',
    'Publish to GitHub': 'GitHub-এ পাবলিশ করুন',
    'Publishing Checklist': 'পাবলিশিং চেকলিস্ট',
    'Publishing Steps': 'পাবলিশিং ধাপসমূহ',
    'Ready to publish': 'পাবলিশের জন্য প্রস্তুত',
    'Review the publishing checklist and export your project':
      'পাবলিশিং চেকলিস্ট যাচাই করুন এবং প্রজেক্ট এক্সপোর্ট করুন',
    'Published successfully!': 'সফলভাবে পাবলিশ হয়েছে!',
    'Add the remote:': 'রিমোট যোগ করুন:',
    'Initialize a Git repository:': 'একটি Git রিপোজিটরি ইনিশিয়ালাইজ করুন:',
    'Create a Git tag:': 'একটি Git ট্যাগ তৈরি করুন:',
    'Create a release tag:': 'একটি রিলিজ ট্যাগ তৈরি করুন:',
    'Commit and push:': 'কমিট ও পুশ করুন:',
    'Push your project to a': 'আপনার প্রজেক্ট পুশ করুন',
    'Auto-generate standard GitHub repository files for your project':
      'আপনার প্রজেক্টের জন্য স্ট্যান্ডার্ড GitHub রিপোজিটরি ফাইল স্বয়ংক্রিয়ভাবে তৈরি করুন',

    // JitPack & Maven Publish
    'Maven / JitPack': 'মাভেন / জিটপ্যাক',
    'JitPack': 'JitPack',
    'JitPack Integration': 'JitPack ইন্টিগ্রেশন',
    'Maven Publish + JitPack': 'মাভেন পাবলিশ ও জিটপ্যাক',
    'Configure, generate, validate, and export Maven publication & JitPack dependencies':
      'মাভেন পাবলিকেশন ও জিটপ্যাক ডিপেনডেন্সি কনফিগার, জেনারেট, ভ্যালিডেট ও এক্সপোর্ট করুন',
    'Apply to Project': 'প্রজেক্টে প্রয়োগ করুন',
    'Export Config Files': 'কনফিগ ফাইল এক্সপোর্ট করুন',
    'Configuration Status & Validation': 'কনফিগারেশন স্ট্যাটাস ও ভ্যালিডেশন',
    'Maven Publish & JitPack Metadata': 'মাভেন পাবলিশ ও জিটপ্যাক মেটাডেটা',
    'Consumer Setup': 'কনজিউমার সেটআপ',
    'Step 1 — Add JitPack Repository': 'ধাপ ১ — JitPack রিপোজিটরি যোগ করুন',
    'Step 2 — Add Dependency': 'ধাপ ২ — ডিপেনডেন্সি যোগ করুন',
    'Alternative — Legacy Root build.gradle': 'বিকল্প — লেগাসি রুট build.gradle',
    'Save jitpack.yml': 'jitpack.yml সংরক্ষণ করুন',
    'Reset to Recommended': 'প্রস্তাবিত কনফিগারেশনে রিসেট করুন',
    'Badge & README': 'ব্যাজ ও README',
    'Step-by-Step JitPack Publishing Guide': 'ধাপে ধাপে JitPack পাবলিশিং নির্দেশিকা',
    'Maven publishing configured': 'মাভেন পাবলিশিং কনফিগার করা হয়েছে',
    'Configure and publish your library via JitPack': 'JitPack-এর মাধ্যমে আপনার লাইব্রেরি কনফিগার ও পাবলিশ করুন',
    'Published Versions': 'প্রকাশিত ভার্সনসমূহ',
    'No published versions yet': 'এখনও কোনো ভার্সন প্রকাশিত হয়নি',
    'Publish a release from the GitHub → Publish page — every published version will be listed here with its JitPack dependency snippet.':
      'GitHub → Publish পেজ থেকে একটি রিলিজ প্রকাশ করুন — প্রকাশিত প্রতিটি ভার্সন তার JitPack ডিপেন্ডেন্সি স্নিপেটসহ এখানে তালিকাভুক্ত হবে।',
    'Disconnect / Clear Credential': 'ডিসকানেক্ট / ক্রেডেনশিয়াল মুছুন',
    'Run Full Test': 'ফুল টেস্ট চালান',
    'Stop Test': 'টেস্ট বন্ধ করুন',
    'Connect GitHub': 'GitHub সংযোগ করুন',
    'GitHub Connection & Test Target': 'GitHub সংযোগ ও টেস্ট টার্গেট',
    'Strict Real Results (No Simulation)': 'কঠোর বাস্তব ফলাফল (কোনো সিমুলেশন নেই)',
    'RONi Full Library Test': 'RONi ফুল লাইব্রেরি টেস্ট',
    'Real end-to-end GitHub Actions pipeline verification & source generator diagnostics':
      'সত্যিকারের এন্ড-টু-এন্ড GitHub Actions পাইপলাইন যাচাই ও সোর্স জেনারেটর ডায়াগনস্টিকস',
    'Dependency Snippets': 'ডিপেন্ডেন্সি স্নিপেট',
    'Add the dependency snippet to your consumer app and sync Gradle':
      'ডিপেন্ডেন্সি স্নিপেটটি আপনার কনজিউমার অ্যাপে যোগ করুন এবং গ্র্যাডল সিঙ্ক করুন',
    'Step 1 — Add JitPack repository (settings.gradle)': 'ধাপ ১ — JitPack রিপোজিটরি যোগ করুন (settings.gradle)',
    'Step 2 — Add dependency (app/build.gradle)': 'ধাপ ২ — ডিপেন্ডেন্সি যোগ করুন (app/build.gradle)',
    'JitPack Badge': 'JitPack ব্যাজ',
    'Add to your README:': 'আপনার README-এ যোগ করুন:',
    'next to your version — JitPack builds on first request':
      'আপনার ভার্সনের পাশে — প্রথম রিকোয়েস্টে JitPack বিল্ড করে',
    'and look up your repository': 'এবং আপনার রিপোজিটরি খুঁজুন',

    // Build / validation
    'Build Validator': 'বিল্ড ভ্যালিডেটর',
    'Validate your project structure and JitPack compatibility before publishing':
      'পাবলিশের আগে আপনার প্রজেক্ট স্ট্রাকচার ও JitPack সামঞ্জস্য যাচাই করুন',

    // Downloads
    'Previously exported ZIP files': 'পূর্বে এক্সপোর্ট করা ZIP ফাইলসমূহ',
    'No exports yet. Use Export ZIP to generate a download.':
      'এখনও কোনো এক্সপোর্ট নেই। ডাউনলোড তৈরি করতে Export ZIP ব্যবহার করুন।',
    'Export as ZIP': 'ZIP হিসেবে এক্সপোর্ট',
    'Export your project as ZIP and extract it locally':
      'আপনার প্রজেক্ট ZIP হিসেবে এক্সপোর্ট করুন এবং লোকালি এক্সট্র্যাক্ট করুন',

    // Logs
    'Application activity log': 'অ্যাপ্লিকেশন অ্যাক্টিভিটি লগ',

    // Settings page
    'Configure editor, defaults, and storage preferences':
      'এডিটর, ডিফল্ট ও স্টোরেজ প্রেফারেন্স কনফিগার করুন',
    'Font Size': 'ফন্ট সাইজ',
    'Editor font size in pixels': 'পিক্সেলে এডিটর ফন্ট সাইজ',
    'Tab Size': 'ট্যাব সাইজ',
    'Spaces per tab indent': 'প্রতি ট্যাবে স্পেস সংখ্যা',
    'Auto-save Interval': 'অটো-সেভ ব্যবধান',
    'Seconds between auto-saves (0 = off)': 'অটো-সেভের মধ্যে সেকেন্ড (০ = বন্ধ)',
    'Line Wrap': 'লাইন র‍্যাপ',
    'Wrap long lines in editor': 'এডিটরে লম্বা লাইন র‍্যাপ করুন',
    'Default Project Values': 'ডিফল্ট প্রজেক্ট মান',
    'Default Min SDK': 'ডিফল্ট সর্বনিম্ন SDK',
    'Default Target SDK': 'ডিফল্ট টার্গেট SDK',
    'Default Compile SDK': 'ডিফল্ট কম্পাইল SDK',
    'Default License': 'ডিফল্ট লাইসেন্স',
    'Default Author': 'ডিফল্ট লেখক',
    'Storage Management': 'স্টোরেজ ম্যানেজমেন্ট',
    'Storage Usage': 'স্টোরেজ ব্যবহার',
    'IndexedDB usage estimate': 'IndexedDB ব্যবহারের অনুমান',
    'Calculating…': 'গণনা করা হচ্ছে…',
    'Clear All Projects': 'সব প্রজেক্ট মুছুন',
    'Delete all projects from storage': 'স্টোরেজ থেকে সব প্রজেক্ট মুছে ফেলুন',
    'Clear Projects': 'প্রজেক্ট মুছুন',
    'Clear Exports': 'এক্সপোর্ট মুছুন',
    'Delete all export records': 'সব এক্সপোর্ট রেকর্ড মুছে ফেলুন',
    'Clear Logs': 'লগ মুছুন',
    'Remove all stored log entries': 'সব সংরক্ষিত লগ এন্ট্রি মুছে ফেলুন',
    'Reset Settings': 'সেটিংস রিসেট',
    'Reset all settings to defaults?': 'সব সেটিংস ডিফল্টে রিসেট করবেন?',
    'Settings reset to defaults': 'সেটিংস ডিফল্টে রিসেট হয়েছে',
    'Settings Saved': 'সেটিংস সংরক্ষিত হয়েছে',
    'This will delete ALL projects permanently. Are you sure?':
      'এটি সব প্রজেক্ট স্থায়ীভাবে মুছে ফেলবে। আপনি নিশ্চিত?',
    'All projects cleared': 'সব প্রজেক্ট মুছে ফেলা হয়েছে',
    'Export records cleared': 'এক্সপোর্ট রেকর্ড মুছে ফেলা হয়েছে',
    'Logs cleared': 'লগ মুছে ফেলা হয়েছে',

    // Version Manager
    'Version Update Manager': 'ভার্সন আপডেট ম্যানেজার',
    'Scan Versions': 'ভার্সন স্ক্যান করুন',
    'Scan all project files for version references': 'ভার্সন রেফারেন্সের জন্য সমস্ত ফাইল স্ক্যান করুন',
    'Rollback': 'রোলব্যাক',
    'Restore files from pre-update backup snapshot': 'প্রাক-আপডেট ব্যাকআপ থেকে ফাইল পুনরুদ্ধার করুন',
    'Update Selected': 'নির্বাচিত ফাইল আপডেট করুন',
    'Update only currently checked files': 'শুধুমাত্র নির্বাচিত ফাইল আপডেট করুন',
    'Update All Project Versions': 'সকল প্রজেক্ট ভার্সন আপডেট করুন',
    'Download Updated Project ZIP': 'আপডেট করা প্রজেক্ট ZIP ডাউনলোড করুন',
    'Export complete updated project as downloadable ZIP archive': 'সম্পূর্ণ আপডেট করা প্রজেক্ট ZIP হিসেবে এক্সপোর্ট করুন',
    'Current Project Version': 'বর্তমান প্রজেক্ট ভার্সন',
    'Target Project Version': 'টার্গেট প্রজেক্ট ভার্সন',
    'Detected Version Mismatches': 'শনাক্তকৃত ভার্সন অমিল',
    'Consistency Verification': 'ধারাবাহিকতা যাচাই',
    'Changelog & Release Notes': 'চেঞ্জলগ ও রিলিজ নোট',
    'Protection Rules': 'সুরক্ষা নীতি',
    'Toolchain and library dependencies are strictly protected from accidental updates': 'টুলচেন ও লাইব্রেরি ডিপেন্ডেন্সি দুর্ঘটনাবশত আপডেট থেকে সুরক্ষিত',
    'Patch (+1)': 'প্যাচ (+১)',
    'Minor (+1)': 'মাইনর (+১)',
    'Approval Needed': 'অনুমোদন প্রয়োজন',
    'Protected': 'সুরক্ষিত',
    'Update Line': 'লাইন আপডেট করুন',

    // Environment & Workflow Wizard
    'Environment Manager': 'এনভায়রনমেন্ট ম্যানেজার',
    'Diagnose and fix development environment & Android SDK configuration issues': 'ডেভেলপমেন্ট এনভায়রনমেন্ট ও অ্যান্ড্রয়েড SDK সমস্যা চিহ্নিত ও সমাধান করুন',
    'Run Health Check': 'হেলথ চেক চালান',
    'Auto Fix All': 'সব স্বয়ংক্রিয় ফিক্স করুন',
    'Environment Diagnostics': 'এনভায়রনমেন্ট ডায়াগনস্টিকস',
    'Recommended Actions': 'প্রস্তাবিত পদক্ষেপ',
    'GitHub Actions Wizard': 'GitHub অ্যাকশন উইজার্ড',
    'Automated CI/CD workflow generator and status tracker': 'স্বয়ংক্রিয় CI/CD ওয়ার্কফ্লো জেনারেটর ও স্ট্যাটাস ট্র্যাকার',
    'Workflow Intel': 'ওয়ার্কফ্লো ইন্টেলিজেন্স',
    'Generate Workflow': 'ওয়ার্কফ্লো জেনারেট করুন',
    'Dispatch Workflow': 'ওয়ার্কফ্লো রান করুন',
    'Run History': 'রান হিস্ট্রি',
    'Testing & Validation': 'টেস্টিং ও ভ্যালিডেশন',
    'Transitive Auto-Sync': 'ট্রানজিটিভ অটো-সিঙ্ক',
    'Dependency AST Analyzer': 'ডিপেন্ডেন্সি AST অ্যানালাইজার',
    'Missing Files Generator': 'অনুপস্থিত ফাইল জেনারেটর',
    'Auto-generate missing essential project files': 'প্রয়োজনীয় ফাইলসমূহ স্বয়ংক্রিয়ভাবে তৈরি করুন',

    // Misc
    'Edit metadata for': 'মেটাডেটা সম্পাদনা:',
    'Add a note, e.g.': 'একটি নোট যোগ করুন, যেমন',
    '(public, no README)': '(পাবলিক, README নেই)',
    'Expand / Collapse': 'প্রসারিত / সংকুচিত',
    'Export logs': 'লগ এক্সপোর্ট করুন',
    'Clear console': 'কনসোল পরিষ্কার করুন',
    'Open project': 'প্রজেক্ট খুলুন',
    'View Profile': 'প্রোফাইল দেখুন',
    'pre-release': 'প্রি-রিলিজ',
    'public': 'পাবলিক',

    // Interpolated (used via t())
    '{n} files': '{n}টি ফাইল',
    '{n} chars': '{n}টি অক্ষর',
    'Ln {line}, Col {col}': 'লাইন {line}, কলাম {col}',
  },
};

export class I18n {
  #lang = 'en';
  #origText = new WeakMap();   // TextNode  -> original nodeValue
  #origAttrs = new WeakMap();  // Element   -> { attr: originalValue }
  #observer = null;
  #pendingRoots = new Set();
  #rafId = 0;
  #started = false;

  /** Supported language codes. */
  get languages() { return ['en', 'bn']; }

  /** Current language code. */
  get lang() { return this.#lang; }

  /** Language display names (for switchers). */
  static languageName(code) {
    return code === 'bn' ? 'বাংলা' : 'English';
  }

  /**
   * Initialize: restore persisted language and start the DOM observer.
   * @param {Document} [doc]
   */
  init(doc = document) {
    if (this.#started) return;
    this.#started = true;

    let stored = null;
    try { stored = localStorage.getItem(LS_KEY); } catch { /* private mode */ }
    if (stored === 'en' || stored === 'bn') {
      this.#lang = stored;
    } else {
      this.#lang = 'en';
    }

    doc.documentElement.lang = this.#lang;
    this.#startObserver(doc);
  }

  /**
   * Translate a string with optional {param} interpolation.
   * Missing keys fall back to English (the key itself).
   * @param {string} key
   * @param {object} [params]
   * @returns {string}
   */
  t(key, params = null) {
    let out = key;
    if (this.#lang !== 'en') {
      const dict = translations[this.#lang];
      if (dict && Object.prototype.hasOwnProperty.call(dict, key)) {
        out = dict[key];
      }
    }
    if (params) {
      for (const [k, v] of Object.entries(params)) {
        out = out.split(`{${k}}`).join(String(v));
      }
    }
    return out;
  }

  /** Switch language, persist, and live-update the whole document. */
  setLanguage(lang) {
    if (!this.languages.includes(lang) || lang === this.#lang) return;
    this.#lang = lang;
    document.documentElement.lang = lang;
    try { localStorage.setItem(LS_KEY, lang); } catch { /* private mode */ }

    this.translateTree(document.body);
    eventBus.emit('i18n:changed', { lang });

    // Mirror into the existing settings store (new key — no conflicts).
    import('../modules/storage/StorageService.js')
      .then(({ storage }) => storage.setSetting('uiLanguage', lang))
      .catch(() => {});
  }

  /**
   * Translate all text nodes + common attributes under `root`.
   * Safe to call repeatedly — originals are remembered per node, so
   * switching back to English restores the source text.
   * @param {ParentNode} root
   */
  translateTree(root) {
    if (!root) return;

    // Text nodes
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode: node => {
        const parent = node.parentElement;
        if (!parent || parent.closest(SKIP_SELECTOR)) return NodeFilter.FILTER_REJECT;
        return node.nodeValue.trim() ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
      },
    });

    let node = walker.nextNode();
    while (node) {
      const orig = this.#origText.has(node) ? this.#origText.get(node) : node.nodeValue;
      this.#origText.set(node, orig);
      const key = orig.trim();
      if (key) {
        const translated = this.t(key);
        if (translated !== key) {
          node.nodeValue = orig.replace(key, translated);
        } else if (node.nodeValue !== orig) {
          node.nodeValue = orig; // restore English
        }
      }
      node = walker.nextNode();
    }

    // Attributes
    const els = root.querySelectorAll ? root.querySelectorAll('*') : [];
    els.forEach(el => {
      if (el.closest(SKIP_SELECTOR)) return;
      let origMap = this.#origAttrs.get(el);
      if (!origMap) { origMap = {}; this.#origAttrs.set(el, origMap); }
      for (const attr of TRANSLATABLE_ATTRS) {
        if (!el.hasAttribute(attr)) continue;
        if (!(attr in origMap)) origMap[attr] = el.getAttribute(attr);
        const key = origMap[attr];
        const translated = this.t(key);
        if (translated !== key) el.setAttribute(attr, translated);
        else if (el.getAttribute(attr) !== key) el.setAttribute(attr, key);
      }
    });
  }

  /** Observe DOM insertions so dynamically rendered UI is auto-translated. */
  #startObserver(doc) {
    if (typeof MutationObserver === 'undefined') return;

    const queue = node => {
      const target = node.nodeType === Node.TEXT_NODE ? node.parentElement : node;
      if (!target || target.nodeType !== Node.ELEMENT_NODE) return;
      if (target.closest('#console-body')) return; // raw logs stay untranslated
      this.#pendingRoots.add(target);
    };

    this.#observer = new MutationObserver(mutations => {
      for (const m of mutations) {
        if (m.type !== 'childList') continue;
        m.addedNodes.forEach(queue);
      }
      if (this.#pendingRoots.size && this.#lang !== 'en') this.#scheduleTranslate();
    });

    this.#observer.observe(doc.body, { childList: true, subtree: true });
  }

  #scheduleTranslate() {
    if (this.#rafId) return;
    this.#rafId = requestAnimationFrame(() => {
      this.#rafId = 0;
      const roots = [...this.#pendingRoots];
      this.#pendingRoots.clear();
      roots.forEach(r => this.translateTree(r));
    });
  }
}

export const i18n = new I18n();
export const t = (key, params) => i18n.t(key, params);
