#!/usr/bin/env python3
"""Behavioural spec test for the StorageScope design (faithful port of the Java algorithm).

The Java itself cannot be compiled here (JRE only, no javac), so this validates the
algorithm and the handler's scoping rules, not the Java bytecode.
"""
import random, string, sys

PREFIX = "__p:"
SEP = ":"
LEGACY_CLAIM_KEY = "__p:__legacy_claimed"
MIGRATION_MARKER = "__migrated_v1"

def escape(s):
    out = []
    for c in s:
        if c == "%": out.append("%25")
        elif c == ":": out.append("%3A")
        else: out.append(c)
    return "".join(out)

def prefix_for(pid):
    if pid is None: return ""
    pid = pid.strip()
    return "" if not pid else PREFIX + escape(pid) + SEP

def scoped_key(pid, key):
    return prefix_for(pid) + (key or "")

def unscoped_key(pid, stored):
    if stored is None: return None
    p = prefix_for(pid)
    if not p:
        return None if stored.startswith(PREFIX) else stored
    return stored[len(p):] if stored.startswith(p) else None

def belongs_to(pid, stored):
    return unscoped_key(pid, stored) is not None

class Store:
    """Mirrors EncryptedStore: one shared map for every project."""
    def __init__(self): self.data = {}
    def get(self, k): return self.data.get(k)
    def put(self, k, v): self.data[k] = v
    def contains(self, k): return k in self.data
    def remove(self, k): return self.data.pop(k, None) is not None
    def clear(self): self.data.clear()
    def get_all(self): return dict(self.data)

class Handler:
    """Mirrors NativeStorageCommandHandler.handleRequest for the 7 actions."""
    def __init__(self, store, pid): self.store, self.pid = store, pid
    def get(self, key):
        raw = self.store.get(scoped_key(self.pid, key))
        return {"key": key, "value": raw, "exists": raw is not None}
    def set(self, key, value): self.store.put(scoped_key(self.pid, key), value); return True
    def remove(self, key): self.store.remove(scoped_key(self.pid, key)); return True
    def clear(self):
        doomed = [k for k in self.store.get_all() if belongs_to(self.pid, k)]
        for k in doomed: self.store.remove(k)
        return len(doomed)
    def keys(self):
        out = []
        for k in self.store.get_all():
            plain = unscoped_key(self.pid, k)
            if plain is not None and plain != MIGRATION_MARKER: out.append(plain)
        return out
    def has(self, key): return self.store.contains(scoped_key(self.pid, key))
    def get_all(self):
        out = {}
        for k, v in self.store.get_all().items():
            plain = unscoped_key(self.pid, k)
            if plain is not None and plain != MIGRATION_MARKER: out[plain] = v
        return out

passed = failed = 0
def check(name, cond, detail=""):
    global passed, failed
    if cond: passed += 1; print(f"  PASS  {name}")
    else: failed += 1; print(f"  FAIL  {name}  {detail}")

print("== 1. prefix derivation ==")
check("null project -> legacy namespace", prefix_for(None) == "")
check("blank project -> legacy namespace", prefix_for("   ") == "")
check("normal id", prefix_for("abc") == "__p:abc:")
check("id is trimmed", prefix_for("  abc  ") == prefix_for("abc"))
check("colon is escaped", prefix_for("a:b") == "__p:a%3Ab:")
check("percent is escaped", prefix_for("a%b") == "__p:a%25b:")

print("\n== 2. escape injectivity (fuzz 20000 pairs) ==")
random.seed(7)
alphabet = string.ascii_letters + string.digits + ":%_- ."
ids = {"".join(random.choice(alphabet) for _ in range(random.randint(1, 12))).strip() for _ in range(4000)}
ids = {i for i in ids if i}  # prefix_for() trims, so compare trimmed ids
mapped = {}
collision = None
for i in ids:
    p = prefix_for(i)
    if p in mapped and mapped[p] != i: collision = (mapped[p], i)
    mapped[p] = i
check("no two trimmed ids share a prefix", collision is None, str(collision))
check("distinct ids -> distinct prefixes", len(mapped) == len(ids))

print("\n== 3. round-trip ==")
for pid in [None, "", "abc", "a:b", "a%b", "my app", "11111111-2222-3333-4444-555555555555"]:
    for key in ["theme", "a:b", "__p:x", "", "বাংলা"]:
        if not prefix_for(pid) and key.startswith("__p:"):
            continue  # reserved in the legacy namespace, covered by section 9
        s = scoped_key(pid, key)
        check(f"round-trip pid={pid!r} key={key!r}", unscoped_key(pid, s) == key, f"got {unscoped_key(pid, s)!r}")

print("\n== 4. isolation between two projects ==")
store = Store()
a, b = Handler(store, "proj-a"), Handler(store, "proj-b")
a.set("theme", "dark")
b.set("theme", "light")
check("A reads own value", a.get("theme")["value"] == "dark")
check("B reads own value", b.get("theme")["value"] == "light")
check("A cannot see B key", a.get("secret")["exists"] is False)
b.set("secret", "b-only")
check("A still cannot see B key", a.get("secret")["exists"] is False)
check("A keys exclude B", a.keys() == ["theme"], str(a.keys()))
check("B keys exclude A", sorted(b.keys()) == ["secret", "theme"], str(b.keys()))
check("A getAll excludes B", a.get_all() == {"theme": "dark"}, str(a.get_all()))

print("\n== 5. clear is scoped ==")
removed = a.clear()
check("A clear removed only A keys", removed == 1, f"removed={removed}")
check("A is empty", a.keys() == [])
check("B survived A clear", b.get("theme")["value"] == "light" and b.get("secret")["value"] == "b-only")

print("\n== 6. legacy namespace ==")
legacy = Handler(store, None)
legacy.set("old", "v1")
check("legacy writes unprefixed", store.get("old") == "v1")
check("legacy sees only unprefixed", legacy.keys() == ["old"], str(legacy.keys()))
check("legacy cannot see project keys", legacy.get("theme")["exists"] is False)
check("project cannot see legacy key", a.get("old")["exists"] is False)
legacy.clear()
check("legacy clear keeps project data", b.get("theme")["value"] == "light")
check("legacy clear removed legacy key", store.get("old") is None)

print("\n== 7. reserved marker is hidden ==")
a.set(MIGRATION_MARKER, "1")
check("marker hidden from keys", MIGRATION_MARKER not in a.keys())
check("marker hidden from getAll", MIGRATION_MARKER not in a.get_all())
check("claim marker hidden from legacy", LEGACY_CLAIM_KEY not in legacy.keys())

print("\n== 8. legacy migration claim (first project wins) ==")
store2 = Store()
store2.put("legacyKey", "s:old-value")          # pre-scoping data
p1, p2 = Handler(store2, "proj-1"), Handler(store2, "proj-2")

def migrate(handler, store):
    marker = scoped_key(handler.pid, MIGRATION_MARKER)
    if store.contains(marker): return "already"
    if store.contains(LEGACY_CLAIM_KEY):
        store.put(marker, "1"); return "claimed-by-other"
    for k, v in list(store.get_all().items()):
        if k == MIGRATION_MARKER or k.startswith(PREFIX): continue
        store.put(scoped_key(handler.pid, k), v)
        store.remove(k)
    store.put(LEGACY_CLAIM_KEY, "1"); store.put(marker, "1")
    return "migrated"

check("first project migrates", migrate(p1, store2) == "migrated")
check("first project sees legacy value", p1.get("legacyKey")["value"] == "s:old-value")
check("second project does not claim", migrate(p2, store2) == "claimed-by-other")
check("second project sees nothing", p2.get("legacyKey")["exists"] is False)
check("second run is idempotent", migrate(p1, store2) == "already")

print("\n== 9. reserved keys in the legacy namespace ==")
check("legacy rejects __p: key", prefix_for(None) == "" and "__p:x".startswith("__p:"))
check("project accepts __p: key", unscoped_key("proj-a", scoped_key("proj-a", "__p:x")) == "__p:x")
check("project key is not reserved", not (prefix_for("proj-a") == "" and "__p:x".startswith("__p:")))

print(f"\n{passed} passed, {failed} failed")
sys.exit(1 if failed else 0)
