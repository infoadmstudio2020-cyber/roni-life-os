/**
 * GHAuthPage — GitHub authentication management page.
 */
import { githubAuth }     from '../modules/github/GitHubAuth.js';
import { githubSettings } from '../modules/github/GitHubSettings.js';
import { GitHubUI }       from '../modules/github/GitHubUI.js';
import { toast }          from '../ui/ToastService.js';
import { consoleService } from '../modules/storage/ConsoleService.js';
import { eventBus }       from '../core/EventBus.js';

export class GHAuthPage {
  async init(container) {
    await githubAuth.loadSaved();
    await githubSettings.load();
    this.#render(container);
  }

  #render(container) {
    const user  = githubAuth.user;
    const authed = githubAuth.isAuthenticated;

    container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title">
            <span class="material-symbols-outlined">verified_user</span>Authentication
          </h1>
          <p class="page-subtitle">Connect your GitHub account using a Personal Access Token</p>
        </div>

        <div class="gh-two-col">

          ${GitHubUI.card('key', 'Personal Access Token',
            authed ? this.#loggedInBody(user) : this.#loginBody()
          )}

          ${GitHubUI.card('help', 'How to create a token', `
            <ol class="gh-instructions">
              <li>Go to <a href="https://github.com/settings/tokens" target="_blank" class="gh-link">
                github.com/settings/tokens</a></li>
              <li>Click <strong>Generate new token (classic)</strong></li>
              <li>Add a note, e.g. <em>Android Lib Generator</em></li>
              <li>Set expiration (90 days recommended)</li>
              <li>Select scopes: <code class="code-inline">repo</code> (Full control of repositories),
                <code class="code-inline">workflow</code> (Required to publish GitHub Actions CI/CD workflows),
                <code class="code-inline">read:packages</code> (optional for packages)</li>
              <li>Click <strong>Generate token</strong> and copy it</li>
              <li>Paste it in the Token field and click <strong>Authenticate</strong></li>
            </ol>
            <div class="gh-security-note">
              <span class="material-symbols-outlined">shield</span>
              Your token is stored only in this device's IndexedDB. It is never written into project files or sent anywhere except the GitHub API.
            </div>
          `)}

        </div>
      </div>`;

    this.#bindEvents(container);
  }

  #loggedInBody(user) {
    return `
      <div class="gh-user-card">
        <div class="gh-user-avatar">
          <img src="${user.avatar_url}" alt="${user.login}" width="48" height="48" />
        </div>
        <div class="gh-user-info">
          <div class="gh-user-name">${user.name || user.login}</div>
          <div class="gh-user-login">@${user.login}</div>
          <div class="gh-user-repos">${user.public_repos ?? '—'} public repos</div>
        </div>
        ${GitHubUI.badge('authenticated')}
      </div>
      <div style="margin-top:var(--sp-4);padding:var(--sp-3);background:linear-gradient(135deg, rgba(56,189,248,0.12) 0%, rgba(14,165,233,0.05) 100%);border:1px solid rgba(56,189,248,0.3);border-radius:var(--radius-md);display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:var(--sp-2)">
        <div>
          <div style="font-weight:600;font-size:13px;color:var(--text-primary)">Ecosystem Version Manager Ready</div>
          <div style="font-size:11px;color:var(--text-secondary)">Your GitHub account is connected with 5,000 req/hr live rate limit.</div>
        </div>
        <button class="btn btn-primary" id="gh-goto-ecosystem-btn" style="display:inline-flex;align-items:center;gap:6px">
          <span class="material-symbols-outlined">hub</span>Open Ecosystem Version Manager
        </button>
      </div>
      <div class="gh-action-row" style="margin-top:var(--sp-3)">
        <button class="btn btn-secondary" id="gh-verify-btn">
          <span class="material-symbols-outlined">refresh</span>Verify Token
        </button>
        <a href="${user.html_url}" target="_blank" class="btn btn-ghost">
          <span class="material-symbols-outlined">open_in_new</span>View Profile
        </a>
        <button class="btn btn-danger" id="gh-logout-btn">
          <span class="material-symbols-outlined">logout</span>Sign Out
        </button>
      </div>`;
  }

  #loginBody() {
    return `
      ${GitHubUI.badge('unauthenticated')}
      <div style="margin-top:var(--sp-4)">
        <div class="form-group">
          <label class="form-label required">Personal Access Token</label>
          <div class="gh-token-input-wrap">
            <input class="input" id="gh-token-input" type="password"
              placeholder="ghp_xxxxxxxxxxxxxxxxxxxx" autocomplete="off" />
            <button class="btn btn-ghost btn-icon" id="gh-token-toggle" title="Show/hide token">
              <span class="material-symbols-outlined" id="gh-token-eye">visibility</span>
            </button>
          </div>
          <div class="form-hint">Classic token or fine-grained token with <code>repo</code> scope</div>
        </div>
        <button class="btn btn-primary" id="gh-auth-btn" style="margin-top:var(--sp-3)">
          <span class="material-symbols-outlined">login</span>Authenticate
        </button>
      </div>`;
  }

  #bindEvents(container) {
    container.querySelector('#gh-auth-btn')?.addEventListener('click', async () => {
      const btn   = container.querySelector('#gh-auth-btn');
      const input = container.querySelector('#gh-token-input');
      const token = input?.value?.trim();
      if (!token) { toast.error('Token required', 'Paste your GitHub token'); return; }
      btn.disabled = true;
      btn.innerHTML = '<span class="material-symbols-outlined spin">sync</span>Verifying…';
      try {
        const user = await githubAuth.loginWithToken(token);
        toast.success('Authenticated', `Signed in as @${user.login}`);
        consoleService.success(`GitHub: authenticated as @${user.login}`);
        this.#render(container);
      } catch (e) {
        toast.error('Authentication failed', e.message);
        btn.disabled = false;
        btn.innerHTML = '<span class="material-symbols-outlined">login</span>Authenticate';
      }
    });

    container.querySelector('#gh-token-toggle')?.addEventListener('click', () => {
      const input = container.querySelector('#gh-token-input');
      const eye   = container.querySelector('#gh-token-eye');
      if (!input) return;
      const show  = input.type === 'password';
      input.type  = show ? 'text' : 'password';
      if (eye) eye.textContent = show ? 'visibility_off' : 'visibility';
    });

    container.querySelector('#gh-verify-btn')?.addEventListener('click', async () => {
      const btn = container.querySelector('#gh-verify-btn');
      btn.disabled = true;
      try {
        await githubAuth.verify();
        toast.success('Token valid', `Signed in as @${githubAuth.user.login}`);
      } catch (e) {
        toast.error('Token invalid', e.message);
      } finally { btn.disabled = false; }
    });

    container.querySelector('#gh-goto-ecosystem-btn')?.addEventListener('click', () => {
      eventBus.emit('router:navigate', { pageId: 'ecosystem-manager' });
      window.location.hash = '#ecosystem-manager';
    });

    container.querySelector('#gh-logout-btn')?.addEventListener('click', async () => {
      await githubAuth.logout();
      toast.info('Signed out', 'GitHub token cleared');
      consoleService.info('GitHub: signed out');
      this.#render(container);
    });
  }
}
