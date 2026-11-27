import fs from 'node:fs';
const src = fs.readFileSync('bootstrap.js','utf8');
let pass=0, fail=0;
const ok=(c,m)=>{ if(c){pass++;} else {fail++; console.log('FAIL:',m);} };

// --- 1. bootstrap installs the client ---
const win = {};
const sandbox = { window: win, console, Date, JSON, Promise, Error };
new Function('window','console','Date','JSON','Promise','Error', src)(win, console, Date, JSON, Promise, Error);
ok(typeof win.NativeRuntimeBridgeClient === 'object', 'client installed');
ok(typeof win.NativeRuntimeBridgeClient.call === 'function', 'call() exists');
ok(typeof win.NativeRuntimeBridgeClient.on === 'function', 'on() exists');
ok(typeof win.NativeRuntimeBridgeClient.off === 'function', 'off() exists');
ok(typeof win.NativeRuntimeBridgeClient._handleNativeMessage === 'function', '_handleNativeMessage() exists');

// --- 2. call() rejects when native interface is absent ---
let rejected = false;
await win.NativeRuntimeBridgeClient.call('ui.showToast',{message:'x'}).catch(e=>{ rejected = /not bound/.test(e.message); });
ok(rejected, 'call() rejects with "not bound" when interface missing');

// --- 3. call() posts the exact envelope and resolves on RESPONSE ---
let sent = null;
win.NativeRuntimeBridge = { postMessage: (m)=>{ sent = JSON.parse(m); }, ping: ()=>'pong' };
const p = win.NativeRuntimeBridgeClient.call('ui.showToast', {message:'hi'});
ok(sent && sent.type==='REQUEST', 'envelope type REQUEST');
ok(sent && sent.action==='ui.showToast', 'envelope action preserved');
ok(sent && sent.payload.message==='hi', 'envelope payload preserved');
ok(sent && typeof sent.id==='string' && sent.id.startsWith('req_'), 'envelope id generated');
ok(sent && typeof sent.timestamp==='number', 'envelope timestamp present');
win.NativeRuntimeBridgeClient._handleNativeMessage(JSON.stringify({id:sent.id,type:'RESPONSE',action:'ui.showToast',success:true,payload:{shown:true}}));
const res = await p;
ok(res && res.shown===true, 'RESPONSE resolves with payload');

// --- 4. ERROR rejects ---
const p2 = win.NativeRuntimeBridgeClient.call('ui.nope',{});
const id2 = sent.id;
win.NativeRuntimeBridgeClient._handleNativeMessage(JSON.stringify({id:id2,type:'ERROR',action:'ui.nope',success:false,error:'UNKNOWN_ACTION'}));
let err2=null; await p2.catch(e=>{err2=e.message;});
ok(err2==='UNKNOWN_ACTION', 'ERROR rejects with native error string');

// --- 5. events ---
let got=null;
const fn = (payload)=>{ got = payload; };
win.NativeRuntimeBridgeClient.on('ui.confirm', fn);
win.NativeRuntimeBridgeClient._handleNativeMessage(JSON.stringify({id:'e1',type:'EVENT',action:'ui.confirm',success:true,payload:{confirmed:true}}));
ok(got && got.confirmed===true, 'EVENT delivered to listener');
win.NativeRuntimeBridgeClient.off('ui.confirm', fn);
got=null;
win.NativeRuntimeBridgeClient._handleNativeMessage(JSON.stringify({id:'e2',type:'EVENT',action:'ui.confirm',success:true,payload:{confirmed:false}}));
ok(got===null, 'off() removes listener');

// --- 6. ping ---
ok(win.NativeRuntimeBridgeClient.ping()==='pong', 'ping() returns pong');

// --- 7. malformed message does not throw ---
let threw=false;
try { win.NativeRuntimeBridgeClient._handleNativeMessage('not json'); } catch(e){ threw=true; }
ok(!threw, 'malformed native message is swallowed');

// --- 8. idempotent install ---
const before = win.NativeRuntimeBridgeClient;
new Function('window','console','Date','JSON','Promise','Error', src)(win, console, Date, JSON, Promise, Error);
ok(win.NativeRuntimeBridgeClient===before, 'bootstrap is idempotent');

console.log(`\nBRIDGE_SMOKE: ${pass} passed, ${fail} failed`);
process.exit(fail?1:0);
