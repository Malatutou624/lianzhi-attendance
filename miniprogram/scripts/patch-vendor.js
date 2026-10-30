// Post-build patch for Uni-app alpha bugs
// Bug 1: On={args(e){}} — empty handler for request → replace with {}
// Bug 2: yn() uses for...in which misses non-enumerable wx APIs (like request)
//        → patch yn() to use Object.getOwnPropertyNames instead

const fs = require('fs');
const path = require('path');

const vendorPath = path.resolve(__dirname, '../dist/build/mp-weixin/common/vendor.js');

if (!fs.existsSync(vendorPath)) {
  console.error('[patch] vendor.js not found at', vendorPath);
  process.exit(1);
}

let code = fs.readFileSync(vendorPath, 'utf-8');
let fixes = 0;

// === Fix 1: Replace broken On handler ===
// On={args(e){}} → On={}
if (code.includes('On={args(e){}}')) {
  code = code.replace(/On=\{args\(e\)\{\}\}/g, 'On={}');
  fixes++;
  console.log('[patch] ✅ Fix 1: On empty handler → On={}');
} else {
  console.log('[patch] Fix 1: On handler already fixed');
}

// Also force request mapping to use {} instead of On reference
code = code.replace(/request:On/g, 'request:{}');

// === Fix 2: Patch yn() to enumerate ALL wx properties, including non-enumerable ===
// Original uses: for(const t in wx) mn(t) && (e[t]=wx[t])
// Problem: for...in skips non-enumerable properties like request
// Fix: Also iterate Object.getOwnPropertyNames(wx)
const ynPattern = /function yn\(\)\{const e=\{\};for\(const t in wx\)mn\(t\)&&\(e\[t\]=wx\[t\]\);return/;
if (ynPattern.test(code)) {
  const fixedYn = `function yn(){const e={};for(const t in wx)mn(t)&&(e[t]=wx[t]);Object.getOwnPropertyNames(wx).forEach(function(t){mn(t)&&!(t in e)&&(e[t]=wx[t])});return`;
  code = code.replace(ynPattern, fixedYn);
  fixes++;
  console.log('[patch] ✅ Fix 2: yn() now copies non-enumerable wx properties too');
} else {
  console.log('[patch] Fix 2: yn() pattern not found (may already be patched)');
}

// === Fix 3: Also patch the fallback assignment for non-Pn scenario ===
// If n=wx and the Proxy fallback uses Cn which is missing non-enumerable props
// The fix 2 should already handle this since Cn = yn()
// But let's also make sure the Proxy fallback works directly with global wx

// === Save ===
if (fixes > 0) {
  fs.writeFileSync(vendorPath, code, 'utf-8');
  console.log(`[patch] ✅ All ${fixes} fix(es) applied, vendor.js saved`);
  
  // Verify
  const verifyCode = fs.readFileSync(vendorPath, 'utf-8');
  if (verifyCode.includes('Object.getOwnPropertyNames(wx)')) {
    console.log('[patch] ✅ Verification: yn() patch confirmed');
  }
  if (verifyCode.includes('request:{}')) {
    console.log('[patch] ✅ Verification: request handler is {}');
  }
  if (!verifyCode.includes('On={args(e){}}')) {
    console.log('[patch] ✅ Verification: broken On handler removed');
  }
} else {
  console.log('[patch] No fixes needed');
}
